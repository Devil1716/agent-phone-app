package com.gemma.agentphone.agent

import android.content.Intent
import java.net.URLEncoder

class AutonomousActionParser {
    private val commonPackageAliases = mapOf(
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "google maps" to "com.google.android.apps.maps",
        "maps" to "com.google.android.apps.maps",
        "play store" to "com.android.vending",
        "settings" to "com.android.settings",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube"
    )

    fun parse(command: String, responseSummary: String): ExternalActionRequest? {
        val normalizedCommand = command.trim().lowercase()
        val normalizedResponse = responseSummary.trim().lowercase()

        parseStructuredResponse(responseSummary)?.let { return it }

        return when {
            mentionsPlayStore(normalizedCommand) || mentionsPlayStore(normalizedResponse) -> {
                buildPlayStoreSearch(extractAppQuery(command))
            }

            mentionsWhatsApp(normalizedCommand) || mentionsWhatsApp(normalizedResponse) -> {
                buildWhatsAppDraft(extractMessageBody(command))
            }

            mentionsWebSearch(normalizedCommand) || mentionsWebSearch(normalizedResponse) -> {
                ExternalActionRequest(
                    IntentSpec(
                        action = Intent.ACTION_VIEW,
                        data = "https://www.google.com/search?q=${encode(extractSearchQuery(command))}"
                    )
                )
            }

            else -> null
        }
    }

    private fun parseStructuredResponse(responseSummary: String): ExternalActionRequest? {
        val lines = responseSummary.lines().map { it.trim() }.filter { it.isNotBlank() }
        val actionLine = lines.firstOrNull { it.startsWith("ACTION:", ignoreCase = true) } ?: return null
        val queryLine = lines.firstOrNull { it.startsWith("QUERY:", ignoreCase = true) }
        val textLine = lines.firstOrNull { it.startsWith("TEXT:", ignoreCase = true) }
        val appLine = lines.firstOrNull { it.startsWith("APP:", ignoreCase = true) }

        val action = actionLine.substringAfter(':').trim().uppercase()
        val query = queryLine?.substringAfter(':')?.trim().orEmpty()
        val text = textLine?.substringAfter(':')?.trim().orEmpty()
        val app = appLine?.substringAfter(':')?.trim().orEmpty()

        return when (action) {
            "PLAY_STORE_SEARCH" -> buildPlayStoreSearch(query)
            "WEB_SEARCH",
            "OPEN_BROWSER_SEARCH" -> ExternalActionRequest(
                IntentSpec(
                    action = Intent.ACTION_VIEW,
                    data = "https://www.google.com/search?q=${encode(query.ifBlank { extractSearchQuery(query) })}"
                )
            )

            "OPEN_MEDIA_SEARCH" -> ExternalActionRequest(
                IntentSpec(
                    action = Intent.ACTION_VIEW,
                    data = "https://m.youtube.com/results?search_query=${encode(query)}"
                )
            )

            "OPEN_SETTINGS" -> {
                if (query.contains("wifi", ignoreCase = true) || query.contains("wi-fi", ignoreCase = true)) {
                    ExternalActionRequest(IntentSpec(action = "android.settings.WIFI_SETTINGS"))
                } else {
                    ExternalActionRequest(IntentSpec(action = "android.settings.SETTINGS"))
                }
            }

            "OPEN_MAPS" -> ExternalActionRequest(
                IntentSpec(
                    action = Intent.ACTION_VIEW,
                    data = "google.navigation:q=${encode(query.ifBlank { "home" })}"
                )
            )

            "OPEN_APP" -> {
                val packageName = resolvePackageName(app.ifBlank { query })
                if (packageName.isBlank()) null else ExternalActionRequest(
                    IntentSpec(
                        action = Intent.ACTION_MAIN,
                        packageName = packageName
                    )
                )
            }

            "WHATSAPP_MESSAGE" -> buildWhatsAppDraft(text)
            else -> null
        }
    }

    private fun mentionsPlayStore(text: String): Boolean {
        return text.contains("play store") || text.contains("google play")
    }

    private fun mentionsWhatsApp(text: String): Boolean {
        return text.contains("whatsapp") || text.contains("whats app")
    }

    private fun mentionsWebSearch(text: String): Boolean {
        return text.contains("search the web") || text.contains("google search") || text.contains("search for")
    }

    private fun extractAppQuery(command: String): String {
        val cleaned = command
            .replace(Regex("(?i)download|install|from|on|using|the|google play|play store|app"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return cleaned.ifBlank { command.trim() }
    }

    private fun extractMessageBody(command: String): String {
        val cleaned = command
            .replace(Regex("(?i)send|message|on whatsapp|via whatsapp|through whatsapp"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return cleaned.ifBlank { command.trim() }
    }

    private fun extractSearchQuery(command: String): String {
        return command.substringAfter("search", command).trim().ifBlank { command.trim() }
    }

    private fun buildPlayStoreSearch(query: String): ExternalActionRequest {
        val encodedQuery = encode(query.ifBlank { "apps" })
        return ExternalActionRequest(
            IntentSpec(
                action = Intent.ACTION_VIEW,
                data = "market://search?q=$encodedQuery&c=apps"
            )
        )
    }

    private fun buildWhatsAppDraft(message: String): ExternalActionRequest {
        return ExternalActionRequest(
            IntentSpec(
                action = Intent.ACTION_VIEW,
                data = "https://wa.me/?text=${encode(message.ifBlank { "Hello" })}",
                packageName = "com.whatsapp"
            )
        )
    }

    private fun resolvePackageName(value: String): String {
        val normalized = value.trim().lowercase()
        if (normalized.isBlank()) {
            return ""
        }
        return if (normalized.contains('.')) {
            normalized
        } else {
            commonPackageAliases[normalized].orEmpty()
        }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
