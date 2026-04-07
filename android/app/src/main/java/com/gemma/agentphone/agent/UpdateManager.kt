package com.gemma.agentphone.agent

import android.content.Context
import android.util.Log
import com.gemma.agentphone.BuildConfig
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()
    private val repoUrl = "https://api.github.com/repos/DaRkAngeL/agent-phone-app/releases/latest" // Placeholder repo

    fun checkForUpdates(onUpdateFound: (version: String, url: String) -> Unit) {
        val request = Request.Builder()
            .url(repoUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UpdateManager", "Failed to check for updates", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return
                    val json = JSONObject(body)
                    val latestVersion = json.optString("tag_name", "0.0.0")
                    val downloadUrl = json.optString("html_url", "")

                    if (isNewerVersion(latestVersion)) {
                        onUpdateFound(latestVersion, downloadUrl)
                    }
                }
            }
        })
    }

    private fun isNewerVersion(latest: String): Boolean {
        val current = BuildConfig.VERSION_NAME
        // Simple string comparison for alpha/beta logic or semver check
        return latest > current 
    }
}
