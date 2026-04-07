package com.gemma.agentphone.agent

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.io.File

class ModelDownloadManager(private val context: Context) {

    companion object {
        const val MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"
        const val MODEL_URL = "https://storage.googleapis.com/gemma-models/gemma-2b-it-cpu-int4.bin" // Placeholder
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun isModelDownloaded(): Boolean {
        return getModelFile().exists()
    }

    fun getModelFile(): File {
        return File(context.getExternalFilesDir(null), MODEL_FILENAME)
    }

    fun startDownload(): Long {
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Gemma 4 Intelligence Model")
            .setDescription("Downloading 1.3GB AI model weights...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, null, MODEL_FILENAME)
            .setAllowedOverMetered(true) // User approved cellular
            .setAllowedOverRoaming(true)

        return downloadManager.enqueue(request)
    }

    fun getDownloadProgress(downloadId: Long): Int {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (bytesTotal > 0) {
                return (bytesDownloaded * 100L / bytesTotal).toInt()
            }
        }
        cursor.close()
        return 0
    }

    fun isDownloadFinished(downloadId: Long): Boolean {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor = downloadManager.query(query)
        var finished = false
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            finished = status == DownloadManager.STATUS_SUCCESSFUL
        }
        cursor.close()
        return finished
    }
}
