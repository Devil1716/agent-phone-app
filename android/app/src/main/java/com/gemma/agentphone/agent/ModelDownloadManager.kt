package com.gemma.agentphone.agent

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.URLUtil
import com.gemma.agentphone.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "model_download"
        private const val KEY_ACTIVE_DOWNLOAD_ID = "active_download_id"
        private const val KEY_ACTIVE_FILENAME = "active_filename"
        private const val KEY_DOWNLOAD_PROGRESS = "download_progress"
        private const val KEY_IS_DOWNLOADING = "is_downloading"
        private const val KEY_LAST_ERROR_MESSAGE = "last_error_message"
        private val nextDownloadId = AtomicLong(System.currentTimeMillis())
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getModelFile(): File {
        val directory = File(context.filesDir, "models")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val savedFilename = prefs.getString(KEY_ACTIVE_FILENAME, null)
        val preferredFile = savedFilename?.let { File(directory, it) }
        return preferredFile?.takeIf(File::exists)
            ?: File(directory, BuildConfig.DEFAULT_MODEL_FILENAME)
    }

    fun isModelDownloaded(): Boolean = getModelFile().exists()

    fun getActiveDownloadId(): Long? {
        val value = prefs.getLong(KEY_ACTIVE_DOWNLOAD_ID, -1L)
        return value.takeIf { it > 0L }
    }

    fun clearActiveDownload() {
        prefs.edit()
            .remove(KEY_ACTIVE_DOWNLOAD_ID)
            .putBoolean(KEY_IS_DOWNLOADING, false)
            .putInt(KEY_DOWNLOAD_PROGRESS, 0)
            .apply()
    }

    private fun clearLastError() {
        prefs.edit().remove(KEY_LAST_ERROR_MESSAGE).apply()
    }

    private fun rememberError(message: String) {
        prefs.edit().putString(KEY_LAST_ERROR_MESSAGE, message).apply()
    }

    private fun getLastError(): String? = prefs.getString(KEY_LAST_ERROR_MESSAGE, null)

    fun startDownload(downloadUrl: String, huggingFaceToken: String): Result<Long> {
        if (downloadUrl.isBlank() || !URLUtil.isNetworkUrl(downloadUrl)) {
            return Result.failure(IllegalArgumentException("A valid model download URL is required."))
        }

        return runCatching {
            val downloadId = nextDownloadId.incrementAndGet()
            val targetFile = resolveTargetFile(downloadUrl)
            targetFile.parentFile?.mkdirs()
            removeExistingModels()
            clearLastError()
            prefs.edit()
                .putLong(KEY_ACTIVE_DOWNLOAD_ID, downloadId)
                .putString(KEY_ACTIVE_FILENAME, targetFile.name)
                .putBoolean(KEY_IS_DOWNLOADING, true)
                .putInt(KEY_DOWNLOAD_PROGRESS, 0)
                .apply()
            startStreamingDownload(downloadId, downloadUrl, huggingFaceToken.trim(), targetFile)
            downloadId
        }
    }

    fun importModel(sourceUri: Uri): Result<File> {
        return runCatching {
            val target = resolveImportedTargetFile(sourceUri)
            target.parentFile?.mkdirs()
            removeExistingModels()
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "The selected file could not be opened." }
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            prefs.edit().putString(KEY_ACTIVE_FILENAME, target.name).apply()
            clearActiveDownload()
            clearLastError()
            target
        }
    }

    fun getStatus(): ModelDownloadStatus {
        if (isModelDownloaded()) {
            clearActiveDownload()
            clearLastError()
            return ModelDownloadStatus.Ready(getModelFile())
        }

        val isDownloading = prefs.getBoolean(KEY_IS_DOWNLOADING, false)
        val downloadId = getActiveDownloadId()
        if (!isDownloading || downloadId == null) {
            return getLastError()?.let(ModelDownloadStatus::Failed) ?: ModelDownloadStatus.Missing
        }
        val progress = prefs.getInt(KEY_DOWNLOAD_PROGRESS, 0)
        return ModelDownloadStatus.Downloading(downloadId, progress)
    }

    private fun startStreamingDownload(
        downloadId: Long,
        downloadUrl: String,
        huggingFaceToken: String,
        targetFile: File
    ) {
        Thread {
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.partial")
            try {
                val requestBuilder = Request.Builder().url(downloadUrl)
                if (huggingFaceToken.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $huggingFaceToken")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException(formatFailureMessage(response.code))
                    }

                    val body = response.body ?: throw IOException("Gemma download returned an empty response body.")
                    val contentLength = body.contentLength()
                    tempFile.outputStream().buffered().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) {
                                    break
                                }

                                output.write(buffer, 0, read)
                                downloaded += read
                                updateProgress(downloadId, downloaded, contentLength)
                            }
                        }
                    }
                }

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                prefs.edit()
                    .putString(KEY_ACTIVE_FILENAME, targetFile.name)
                    .putInt(KEY_DOWNLOAD_PROGRESS, 100)
                    .apply()
                clearActiveDownload()
                clearLastError()
            } catch (exception: Exception) {
                tempFile.delete()
                val message = exception.localizedMessage
                    ?: "Gemma download failed. Verify the model URL or import the model manually."
                rememberError(message)
                clearActiveDownload()
            }
        }.start()
    }

    private fun updateProgress(downloadId: Long, downloaded: Long, total: Long) {
        val progress = if (total > 0L) {
            ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
        } else {
            0
        }
        prefs.edit()
            .putLong(KEY_ACTIVE_DOWNLOAD_ID, downloadId)
            .putBoolean(KEY_IS_DOWNLOADING, true)
            .putInt(KEY_DOWNLOAD_PROGRESS, progress)
            .apply()
    }

    private fun resolveTargetFile(downloadUrl: String): File {
        val filename = Uri.parse(downloadUrl).lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBefore('?')
            ?.takeIf { it.endsWith(".task") || it.endsWith(".litertlm") }
            ?: BuildConfig.DEFAULT_MODEL_FILENAME
        return File(File(context.filesDir, "models"), filename)
    }

    private fun resolveImportedTargetFile(sourceUri: Uri): File {
        val importedName = queryDisplayName(sourceUri)
            ?.takeIf { it.endsWith(".task") || it.endsWith(".litertlm") }
            ?: BuildConfig.DEFAULT_MODEL_FILENAME
        return File(File(context.filesDir, "models"), importedName)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            .useFirstRow { cursor ->
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
    }

    private fun removeExistingModels() {
        val directory = File(context.filesDir, "models")
        directory.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".task") || it.name.endsWith(".litertlm") || it.name.endsWith(".partial")) }
            ?.forEach(File::delete)
    }

    private fun formatFailureMessage(reason: Int): String {
        return when (reason) {
            401, 403 -> "Gemma download requires Hugging Face access. Accept the model terms, then add a Hugging Face token in settings or import the file manually."
            404 -> "The configured Gemma model URL no longer exists. Update the model source in settings."
            in 500..599 -> "The Gemma source is temporarily unavailable. Retry the download in a moment or import the model manually."
            else -> "Gemma download failed with HTTP $reason. Verify the model URL and permissions, or import the model manually."
        }
    }
}

private inline fun <T> Cursor?.useFirstRow(block: (Cursor) -> T): T? {
    if (this == null) {
        return null
    }
    use { cursor ->
        return if (cursor.moveToFirst()) block(cursor) else null
    }
}

sealed class ModelDownloadStatus {
    object Missing : ModelDownloadStatus()
    data class Downloading(val downloadId: Long, val progress: Int) : ModelDownloadStatus()
    data class Ready(val file: File) : ModelDownloadStatus()
    data class Failed(val message: String) : ModelDownloadStatus()
}
