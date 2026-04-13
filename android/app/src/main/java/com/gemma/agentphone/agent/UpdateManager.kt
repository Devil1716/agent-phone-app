package com.gemma.agentphone.agent

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.gemma.agentphone.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE
) {
    companion object {
        const val UPDATE_FILE_NAME = "gemma-agent-update.apk"
        private const val TAG = "UpdateManager"
    }

    private val repoUrl =
        "https://api.github.com/repos/${BuildConfig.APP_REPO_OWNER}/${BuildConfig.APP_REPO_NAME}/releases/latest"
    private val releasesPageUrl =
        "https://github.com/${BuildConfig.APP_REPO_OWNER}/${BuildConfig.APP_REPO_NAME}/releases/latest"

    fun latestReleasePageUrl(): String = releasesPageUrl

    fun checkForUpdates(
        onUpdateFound: (version: String, url: String) -> Unit,
        onNoUpdate: () -> Unit = {},
        onError: (message: String) -> Unit = {}
    ) {
        val request = Request.Builder()
            .url(repoUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Gemma-Agent-Phone-App")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Failed to check for updates", e)
                onError("Network error while checking for updates.")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.w(TAG, "Unexpected update response: ${it.code}")
                        onError("Update check failed with HTTP ${it.code}.")
                        return
                    }

                    try {
                        val body = it.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val latestVersion = json.optString("tag_name", "").trim()
                        val downloadUrl = extractReleaseUrl(json)

                        if (latestVersion.isBlank() || downloadUrl.isBlank()) {
                            onNoUpdate()
                            return
                        }

                        // We prefer versionName comparison but we could also check body for "versionCode: X"
                        if (isNewerVersion(latestVersion)) {
                            onUpdateFound(latestVersion, downloadUrl)
                        } else {
                            onNoUpdate()
                        }
                    } catch (exception: Exception) {
                        Log.w(TAG, "Unable to parse update response", exception)
                        onError("Unable to parse release metadata.")
                    }
                }
            }
        })
    }

    fun downloadAndInstallUpdate(apkUrl: String): Long? {
        cleanupOldUpdates()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Gemma Agent Update")
            .setDescription("Downloading newer version: $apkUrl")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, UPDATE_FILE_NAME)

        return try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue update download", e)
            null
        }
    }

    fun installDownloadedUpdate(downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
        
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start installer", e)
        }
    }

    fun cleanupOldUpdates() {
        try {
            val updateFile = File(context.getExternalFilesDir(null), UPDATE_FILE_NAME)
            if (updateFile.exists()) {
                updateFile.delete()
                Log.d(TAG, "Cleaned up old update file.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup old updates", e)
        }
    }

    internal fun isNewerVersion(latest: String, current: String = currentVersionName): Boolean {
        val latestVersion = parseVersion(latest)
        val currentVersion = parseVersion(current)
        
        val numberCompare = compareVersionParts(latestVersion.numbers, currentVersion.numbers)
        if (numberCompare != 0) {
            return numberCompare > 0
        }
        
        return latestVersion.stageRank > currentVersion.stageRank
    }

    private fun extractReleaseUrl(json: JSONObject): String {
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    val apkUrl = asset.optString("browser_download_url", "").trim()
                    if (apkUrl.isNotBlank()) {
                        return apkUrl
                    }
                }
            }
        }
        return json.optString("html_url", "").trim()
    }

    private fun compareVersionParts(latestParts: List<Int>, currentParts: List<Int>): Int {
        val maxSize = maxOf(latestParts.size, currentParts.size)

        for (index in 0 until maxSize) {
            val latestValue = latestParts.getOrElse(index) { 0 }
            val currentValue = currentParts.getOrElse(index) { 0 }
            if (latestValue != currentValue) {
                return if (latestValue > currentValue) 1 else -1
            }
        }
        return 0
    }

    private fun normalizeVersion(raw: String): List<Int> {
        return raw
            .removePrefix("v")
            .split('.', '-', '_')
            .mapNotNull { token -> token.toIntOrNull() }
    }

    private fun parseVersion(raw: String): ParsedVersion {
        val normalized = raw.trim().removePrefix("v")
        val numbers = normalizeVersion(normalized)
        val lowered = normalized.lowercase(Locale.US)
        val stageRank = when {
            lowered.contains("debug") || lowered.contains("dev") || lowered.contains("snapshot") -> -1
            lowered.contains("alpha") -> 0
            lowered.contains("beta") -> 1
            lowered.contains("rc") -> 2
            else -> 3
        }
        return ParsedVersion(numbers, stageRank)
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val stageRank: Int
    )
}
