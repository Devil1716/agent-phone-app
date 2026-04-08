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
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun getModelFile(): File {
        return File(requireNotNull(context.getExternalFilesDir(null)), BuildConfig.DEFAULT_MODEL_FILENAME)
    }

    fun isModelDownloaded(): Boolean = getModelFile().exists()

    fun getActiveDownloadId(): Long? {
        val value = prefs.getLong(KEY_ACTIVE_DOWNLOAD_ID, -1L)
        return value.takeIf { it > 0L }
    }

    fun clearActiveDownload() {
        prefs.edit().remove(KEY_ACTIVE_DOWNLOAD_ID).apply()
    }

    fun startDownload(downloadUrl: String): Result<Long> {
        if (downloadUrl.isBlank() || !URLUtil.isNetworkUrl(downloadUrl)) {
            return Result.failure(IllegalArgumentException("A valid model download URL is required."))
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Gemma local model")
            .setDescription("Downloading the on-device Gemma task bundle.")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, BuildConfig.DEFAULT_MODEL_FILENAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        return runCatching {
            val id = downloadManager.enqueue(request)
            prefs.edit().putLong(KEY_ACTIVE_DOWNLOAD_ID, id).apply()
            id
        }
    }

    fun importModel(sourceUri: Uri): Result<File> {
        return runCatching {
            val target = getModelFile()
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "The selected file could not be opened." }
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            clearActiveDownload()
            target
        }
    }

    fun getStatus(): ModelDownloadStatus {
        if (isModelDownloaded()) {
            clearActiveDownload()
            return ModelDownloadStatus.Ready(getModelFile())
        }

        val downloadId = getActiveDownloadId() ?: return ModelDownloadStatus.Missing
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) {
                clearActiveDownload()
                return ModelDownloadStatus.Missing
            }

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
                    if (isModelDownloaded()) ModelDownloadStatus.Ready(getModelFile())
                    else ModelDownloadStatus.Failed("The download finished but the model file was not found.")
                }

                DownloadManager.STATUS_FAILED -> {
                    clearActiveDownload()
                    ModelDownloadStatus.Failed("DownloadManager failed with reason code $reason.")
                }

                else -> ModelDownloadStatus.Missing
            }
        }
    }
}

sealed class ModelDownloadStatus {
    object Missing : ModelDownloadStatus()
    data class Downloading(val downloadId: Long, val progress: Int) : ModelDownloadStatus()
    data class Ready(val file: File) : ModelDownloadStatus()
    data class Failed(val message: String) : ModelDownloadStatus()
}
