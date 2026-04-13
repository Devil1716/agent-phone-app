package com.gemma.agentphone.agent

import android.content.Intent
import java.net.URLEncoder

class AutonomousActionParser {
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
        val thoughtLine = lines.firstOrNull { it.startsWith("THOUGHT:", ignoreCase = true) }
        val actionLine = lines.firstOrNull { it.startsWith("ACTION:", ignoreCase = true) } ?: return null
        val queryLine = lines.firstOrNull { it.startsWith("QUERY:", ignoreCase = true) }
        val textLine = lines.firstOrNull { it.startsWith("TEXT:", ignoreCase = true) }
        val appLine = lines.firstOrNull { it.startsWith("APP:", ignoreCase = true) }

        val thought = thoughtLine?.substringAfter(':')?.trim()
        val action = actionLine.substringAfter(':').trim().uppercase()
        val query = queryLine?.substringAfter(':')?.trim().orEmpty()
        val text = textLine?.substringAfter(':')?.trim().orEmpty()
        val app = appLine?.substringAfter(':')?.trim().orEmpty()

        return when (action) {
            "PLAY_STORE_SEARCH" -> buildPlayStoreSearch(query, thought)
            "WEB_SEARCH" -> ExternalActionRequest(
                spec = IntentSpec(
                    action = Intent.ACTION_VIEW,
                    data = "https://www.google.com/search?q=${encode(query)}"
                ),
                thought = thought
            )

            "OPEN_APP" -> {
                if (app.isBlank()) null else ExternalActionRequest(
                    spec = IntentSpec(
                        action = Intent.ACTION_MAIN,
                        packageName = app
                    ),
                    thought = thought
                )
            }

            "WHATSAPP_MESSAGE" -> buildWhatsAppDraft(text, thought)
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

    private fun buildPlayStoreSearch(query: String, thought: String? = null): ExternalActionRequest {
        val encodedQuery = encode(query.ifBlank { "apps" })
        return ExternalActionRequest(
            spec = IntentSpec(
                action = Intent.ACTION_VIEW,
                data = "market://search?q=$encodedQuery&c=apps"
            ),
            thought = thought
        )
    }

    private fun buildWhatsAppDraft(message: String, thought: String? = null): ExternalActionRequest {
        return ExternalActionRequest(
            spec = IntentSpec(
                action = Intent.ACTION_VIEW,
                data = "https://wa.me/?text=${encode(message)}",
                packageName = "com.whatsapp"
            ),
            thought = thought
        )
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
