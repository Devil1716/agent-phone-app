package com.gemma.agentphone.agent

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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class UpdateManager {

    private val client = OkHttpClient()
    private val repoUrl =
        "https://api.github.com/repos/${BuildConfig.APP_REPO_OWNER}/${BuildConfig.APP_REPO_NAME}/releases"

    fun checkForUpdates(onUpdateFound: (version: String, url: String) -> Unit) {
        val request = Request.Builder()
            .url(repoUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Atlas-Update-Manager")
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("UpdateManager", "Failed to check for updates", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.w("UpdateManager", "Unexpected update response: ${it.code}")
                        return
                    }

                    val body = it.body?.string().orEmpty()
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() == 0) return

                    val json = jsonArray.getJSONObject(0)
                    val latestVersion = json.optString("tag_name", "")
                    
                    var downloadUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    if (downloadUrl.isBlank()) {
                        downloadUrl = json.optString("html_url", "")
                    }

                    if (latestVersion.isNotBlank() && downloadUrl.isNotBlank() && isNewerVersion(latestVersion)) {
                        onUpdateFound(latestVersion, downloadUrl)
                    }
                }
            }
        })
    }

    fun downloadAndInstallUpdate(context: Context, url: String) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UpdateManager", "Failed to download update APK", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("UpdateManager", "Failed to download APK: ${response.code}")
                    return
                }

                try {
                    val updateFile = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        "atlas_update.apk"
                    )
                    FileOutputStream(updateFile).use { output ->
                        response.body?.byteStream()?.copyTo(output)
                    }

                    installApk(context, updateFile)
                } catch (e: Exception) {
                    Log.e("UpdateManager", "Error saving or installing APK", e)
                }
            }
        })
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun isNewerVersion(latest: String): Boolean {
        val latestParts = normalizeVersion(latest)
        val currentParts = normalizeVersion(BuildConfig.VERSION_NAME)
        val maxSize = maxOf(latestParts.size, currentParts.size)

        for (index in 0 until maxSize) {
            val latestValue = latestParts.getOrElse(index) { 0 }
            val currentValue = currentParts.getOrElse(index) { 0 }
            if (latestValue != currentValue) {
                return latestValue > currentValue
            }
        }
        return false
    }

    private fun normalizeVersion(raw: String): List<Int> {
        return raw.lowercase()
            .trim()
            .removePrefix("v")
            .split('.', '-', '_')
            .mapNotNull { token -> token.toIntOrNull() }
    }
}
