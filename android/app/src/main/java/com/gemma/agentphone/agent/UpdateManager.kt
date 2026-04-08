package com.gemma.agentphone.agent

import android.util.Log
import com.gemma.agentphone.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class UpdateManager {

    private val client = OkHttpClient()
    private val repoUrl =
        "https://api.github.com/repos/${BuildConfig.APP_REPO_OWNER}/${BuildConfig.APP_REPO_NAME}/releases/latest"

    fun checkForUpdates(onUpdateFound: (version: String, url: String) -> Unit) {
        val request = Request.Builder()
            .url(repoUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Gemma-Agent-Phone-App")
            .build()
            // ...
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
                    val json = JSONObject(body)
                    val latestVersion = json.optString("tag_name", "")
                    
                    // Try to find a direct APK download link in assets first
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

                    // Fallback to the release page if no direct APK asset is found
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
        return raw
            .trim()
            .removePrefix("v")
            .split('.', '-', '_')
            .mapNotNull { token -> token.toIntOrNull() }
    }
}
