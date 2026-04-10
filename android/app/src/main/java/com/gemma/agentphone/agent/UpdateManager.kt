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
import java.util.Locale

class UpdateManager(
    private val client: OkHttpClient = OkHttpClient(),
    private val currentVersionName: String = BuildConfig.VERSION_NAME
) {

    private val repoUrl =
        "https://api.github.com/repos/${BuildConfig.APP_REPO_OWNER}/${BuildConfig.APP_REPO_NAME}/releases/latest"

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
                Log.w("UpdateManager", "Failed to check for updates", e)
                onError("Network error while checking for updates.")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.w("UpdateManager", "Unexpected update response: ${it.code}")
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

                        if (isNewerVersion(latestVersion)) {
                            onUpdateFound(latestVersion, downloadUrl)
                        } else {
                            onNoUpdate()
                        }
                    } catch (exception: Exception) {
                        Log.w("UpdateManager", "Unable to parse update response", exception)
                        onError("Unable to parse release metadata.")
                    }
                }
            }
        })
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
