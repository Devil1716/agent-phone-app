package com.gemma.agentphone.agent

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import com.gemma.agentphone.BuildConfig
import java.io.File

class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "model_download"
        private const val KEY_ACTIVE_DOWNLOAD_ID = "active_download_id"
        private const val KEY_LAST_ERROR_MESSAGE = "last_error_message"
        const val ERROR_MODEL_ALREADY_PRESENT = "MODEL_ALREADY_PRESENT"
        const val ERROR_MODEL_DOWNLOAD_ACTIVE = "MODEL_DOWNLOAD_ACTIVE"
        private const val TEMP_DOWNLOAD_SUFFIX = ".part"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun getModelFile(): File {
        val appFilesDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(appFilesDir, BuildConfig.DEFAULT_MODEL_FILENAME)
    }

    private fun getTempDownloadFile(): File {
        val appFilesDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(appFilesDir, BuildConfig.DEFAULT_MODEL_FILENAME + TEMP_DOWNLOAD_SUFFIX)
    }

    private fun hasUsableModelFile(): Boolean {
        val file = getModelFile()
        return file.exists() && file.isFile && file.length() > 0L
    }

    fun isModelDownloaded(): Boolean = hasUsableModelFile()

    fun getActiveDownloadId(): Long? {
        val value = prefs.getLong(KEY_ACTIVE_DOWNLOAD_ID, -1L)
        return value.takeIf { it > 0L }
    }

    fun clearActiveDownload() {
        prefs.edit().remove(KEY_ACTIVE_DOWNLOAD_ID).apply()
    }

    private fun clearLastError() {
        prefs.edit().remove(KEY_LAST_ERROR_MESSAGE).apply()
    }

    private fun rememberError(message: String) {
        prefs.edit().putString(KEY_LAST_ERROR_MESSAGE, message).apply()
    }

    private fun getLastError(): String? = prefs.getString(KEY_LAST_ERROR_MESSAGE, null)

    fun startDownload(downloadUrl: String, huggingFaceToken: String): Result<Long> {
        when (getStatus()) {
            is ModelDownloadStatus.Ready -> {
                return Result.failure(IllegalStateException(ERROR_MODEL_ALREADY_PRESENT))
            }

            is ModelDownloadStatus.Downloading -> {
                return Result.failure(IllegalStateException(ERROR_MODEL_DOWNLOAD_ACTIVE))
            }

            else -> Unit
        }

        if (isModelDownloaded()) {
            return Result.failure(IllegalStateException(ERROR_MODEL_ALREADY_PRESENT))
        }

        if (downloadUrl.isBlank() || !URLUtil.isNetworkUrl(downloadUrl)) {
            return Result.failure(IllegalArgumentException("A valid model download URL is required."))
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Gemma local model")
            .setDescription("Downloading the on-device Gemma task bundle.")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, BuildConfig.DEFAULT_MODEL_FILENAME + TEMP_DOWNLOAD_SUFFIX)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        if (huggingFaceToken.isNotBlank()) {
            request.addRequestHeader("Authorization", "Bearer ${huggingFaceToken.trim()}")
        }

        return runCatching {
            getTempDownloadFile().delete()
            clearLastError()
            val id = downloadManager.enqueue(request)
            prefs.edit().putLong(KEY_ACTIVE_DOWNLOAD_ID, id).apply()
            id
        }
    }

    fun importModel(sourceUri: Uri): Result<File> {
        return runCatching {
            val target = getModelFile()
            getTempDownloadFile().delete()
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "The selected file could not be opened." }
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            clearActiveDownload()
            clearLastError()
            target
        }
    }

    fun getStatus(): ModelDownloadStatus {
        val downloadId = getActiveDownloadId()
        if (downloadId != null) {
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            cursor.use {
                if (it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val progress = if (total > 0L) ((downloaded * 100L) / total).toInt() else 0

                    return when (status) {
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_PAUSED,
                        DownloadManager.STATUS_RUNNING -> ModelDownloadStatus.Downloading(downloadId, progress)

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            clearActiveDownload()
                            moveCompletedDownloadIntoPlace()?.let { completedFile ->
                                clearLastError()
                                ModelDownloadStatus.Ready(completedFile)
                            } ?: run {
                                val message = "The download finished but the Gemma model file was not found on the device."
                                rememberError(message)
                                ModelDownloadStatus.Failed(message)
                            }
                        }

                        DownloadManager.STATUS_FAILED -> {
                            clearActiveDownload()
                            val message = formatFailureMessage(reason)
                            rememberError(message)
                            ModelDownloadStatus.Failed(message)
                        }

                        else -> getLastError()?.let(ModelDownloadStatus::Failed) ?: ModelDownloadStatus.Missing
                    }
                } else {
                    clearActiveDownload()
                }
            }
        }

        return if (hasUsableModelFile()) {
            clearLastError()
            ModelDownloadStatus.Ready(getModelFile())
        } else {
            val tempFile = getTempDownloadFile()
            if (tempFile.exists() && tempFile.length() > 0L) {
                val message = "Gemma model download did not complete successfully. Retry the download or import the model file manually."
                rememberError(message)
                ModelDownloadStatus.Failed(message)
            } else {
                getLastError()?.let(ModelDownloadStatus::Failed) ?: ModelDownloadStatus.Missing
            }
        }
    }

    private fun moveCompletedDownloadIntoPlace(): File? {
        val tempFile = getTempDownloadFile()
        val finalFile = getModelFile()

        return when {
            tempFile.exists() && tempFile.length() > 0L -> {
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (tempFile.renameTo(finalFile)) {
                    finalFile
                } else {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                    if (finalFile.exists() && finalFile.length() > 0L) finalFile else null
                }
            }

            finalFile.exists() && finalFile.length() > 0L -> finalFile
            else -> null
        }
    }

    private fun formatFailureMessage(reason: Int): String {
        return when (reason) {
            401 -> "Gemma download requires Hugging Face access. Accept the model terms, then add a Hugging Face token in settings or import the file manually."
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "The Gemma download was interrupted while transferring data. Retry on a stable connection or import the file manually."
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "The device does not have enough storage space for the Gemma model."
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The Gemma source redirected too many times. Recheck the model URL in settings."
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The Gemma source returned an unexpected HTTP response. Verify the model URL and access permissions."
            DownloadManager.ERROR_CANNOT_RESUME -> "Android could not resume the Gemma download. Start it again or import the model file."
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Android could not find the storage target for the Gemma model."
            else -> "Gemma download failed with reason code $reason. Update the model source, add a Hugging Face token, or import the model manually."
        }
    }
}

sealed class ModelDownloadStatus {
    object Missing : ModelDownloadStatus()
    data class Downloading(val downloadId: Long, val progress: Int) : ModelDownloadStatus()
    data class Ready(val file: File) : ModelDownloadStatus()
    data class Failed(val message: String) : ModelDownloadStatus()
}
