package com.gemma.agentphone.agent

import android.content.Intent
import java.net.URLEncoder

class AutonomousActionParser {
    fun parse(command: String, responseSummary: String): ParsedAutonomousAction? {
        val normalizedCommand = command.trim().lowercase()
        val normalizedResponse = responseSummary.trim().lowercase()

        parseStructuredResponse(responseSummary)?.let { return it }

        return when {
            mentionsPlayStore(normalizedCommand) || mentionsPlayStore(normalizedResponse) -> {
                ParsedAutonomousAction(
                    action = AutonomousActionType.PLAY_STORE_SEARCH,
                    thought = null,
                    externalAction = buildPlayStoreSearch(extractAppQuery(command))
                )
            }

            mentionsWhatsApp(normalizedCommand) || mentionsWhatsApp(normalizedResponse) -> {
                ParsedAutonomousAction(
                    action = AutonomousActionType.WHATSAPP_MESSAGE,
                    thought = null,
                    externalAction = buildWhatsAppDraft(extractMessageBody(command))
                )
            }

            mentionsWebSearch(normalizedCommand) || mentionsWebSearch(normalizedResponse) -> {
                ParsedAutonomousAction(
                    action = AutonomousActionType.WEB_SEARCH,
                    thought = null,
                    externalAction = ExternalActionRequest(
                        spec = IntentSpec(
                            action = Intent.ACTION_VIEW,
                            data = "https://www.google.com/search?q=${encode(extractSearchQuery(command))}"
                        )
                    )
                )
            }

            else -> null
        }
    }

    private fun parseStructuredResponse(responseSummary: String): ParsedAutonomousAction? {
        val lines = responseSummary.lines().map { it.trim() }.filter { it.isNotBlank() }
        val thoughtLine = lines.firstOrNull { it.startsWith("THOUGHT:", ignoreCase = true) }
        val actionLine = lines.firstOrNull { it.startsWith("ACTION:", ignoreCase = true) } ?: return null
        val queryLine = lines.firstOrNull { it.startsWith("QUERY:", ignoreCase = true) }
        val textLine = lines.firstOrNull { it.startsWith("TEXT:", ignoreCase = true) }
        val appLine = lines.firstOrNull { it.startsWith("APP:", ignoreCase = true) }
        val targetLine = lines.firstOrNull { it.startsWith("TARGET:", ignoreCase = true) }
        val urlLine = lines.firstOrNull { it.startsWith("URL:", ignoreCase = true) }

        val thought = thoughtLine?.substringAfter(':')?.trim()
        val action = actionLine.substringAfter(':').trim().uppercase()
        val query = queryLine?.substringAfter(':')?.trim().orEmpty()
        val text = textLine?.substringAfter(':')?.trim().orEmpty()
        val app = appLine?.substringAfter(':')?.trim().orEmpty()
        val target = targetLine?.substringAfter(':')?.trim().orEmpty()
        val url = urlLine?.substringAfter(':')?.trim().orEmpty()

        return when (action) {
            "PLAY_STORE_SEARCH" -> ParsedAutonomousAction(
                action = AutonomousActionType.PLAY_STORE_SEARCH,
                thought = thought,
                externalAction = buildPlayStoreSearch(query, thought)
            )

            "WEB_SEARCH" -> ParsedAutonomousAction(
                action = AutonomousActionType.WEB_SEARCH,
                thought = thought,
                externalAction = ExternalActionRequest(
                    spec = IntentSpec(
                        action = Intent.ACTION_VIEW,
                        data = "https://www.google.com/search?q=${encode(query)}"
                    ),
                    thought = thought
                )
            )

            "OPEN_APP" -> if (app.isBlank()) null else ParsedAutonomousAction(
                action = AutonomousActionType.OPEN_APP,
                thought = thought,
                externalAction = ExternalActionRequest(
                    spec = IntentSpec(
                        action = Intent.ACTION_MAIN,
                        packageName = app
                    ),
                    thought = thought
                )
            )

            "OPEN_URL" -> if (url.isBlank()) null else ParsedAutonomousAction(
                action = AutonomousActionType.OPEN_URL,
                thought = thought,
                externalAction = ExternalActionRequest(
                    spec = IntentSpec(
                        action = Intent.ACTION_VIEW,
                        data = url
                    ),
                    thought = thought
                )
            )

            "WHATSAPP_MESSAGE" -> ParsedAutonomousAction(
                action = AutonomousActionType.WHATSAPP_MESSAGE,
                thought = thought,
                externalAction = buildWhatsAppDraft(text, thought)
            )

            "TAP_TEXT" -> ParsedAutonomousAction(
                action = AutonomousActionType.TAP_TEXT,
                thought = thought,
                accessibilityCommand = AccessibilityCommand(AccessibilityCommandType.TAP_TEXT, targetText = target)
            )

            "LONG_PRESS_TEXT" -> ParsedAutonomousAction(
                action = AutonomousActionType.LONG_PRESS_TEXT,
                thought = thought,
                accessibilityCommand = AccessibilityCommand(AccessibilityCommandType.LONG_PRESS_TEXT, targetText = target)
            )

            "INPUT_TEXT" -> ParsedAutonomousAction(
                action = AutonomousActionType.INPUT_TEXT,
                thought = thought,
                accessibilityCommand = AccessibilityCommand(
                    type = AccessibilityCommandType.INPUT_TEXT,
                    targetText = target,
                    text = text
                )
            )

            "SCROLL_DOWN" -> ParsedAutonomousAction(
                action = AutonomousActionType.SCROLL_DOWN,
                thought = thought,
                accessibilityCommand = AccessibilityCommand(AccessibilityCommandType.SCROLL_DOWN)
            )

            "SCROLL_UP" -> ParsedAutonomousAction(
                action = AutonomousActionType.SCROLL_UP,
                thought = thought,
                accessibilityCommand = AccessibilityCommand(AccessibilityCommandType.SCROLL_UP)
            )

            "BACK" -> ParsedAutonomousAction(
                action = AutonomousActionType.BACK,
                thought = thought,
                accessibilityCommand = AccessibilityCommand(AccessibilityCommandType.BACK)
            )

            "HOME" -> ParsedAutonomousAction(
                action = AutonomousActionType.HOME,
                thought = thought,
                accessibilityCommand = AccessibilityCommand(AccessibilityCommandType.HOME)
            )

            "OPEN_NOTIFICATIONS" -> ParsedAutonomousAction(
                action = AutonomousActionType.OPEN_NOTIFICATIONS,
                thought = thought,
                accessibilityCommand = AccessibilityCommand(AccessibilityCommandType.OPEN_NOTIFICATIONS)
            )

            "OPEN_RECENTS" -> ParsedAutonomousAction(
                action = AutonomousActionType.OPEN_RECENTS,
                thought = thought,
                accessibilityCommand = AccessibilityCommand(AccessibilityCommandType.OPEN_RECENTS)
            )

            "WAIT" -> ParsedAutonomousAction(
                action = AutonomousActionType.WAIT,
                thought = thought
            )

            "DONE" -> ParsedAutonomousAction(
                action = AutonomousActionType.DONE,
                thought = thought
            )

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

data class ParsedAutonomousAction(
    val action: AutonomousActionType,
    val thought: String?,
    val externalAction: ExternalActionRequest? = null,
    val accessibilityCommand: AccessibilityCommand? = null
)

data class AccessibilityCommand(
    val type: AccessibilityCommandType,
    val targetText: String = "",
    val text: String = ""
)

enum class AutonomousActionType {
    OPEN_APP,
    OPEN_URL,
    PLAY_STORE_SEARCH,
    WHATSAPP_MESSAGE,
    WEB_SEARCH,
    TAP_TEXT,
    LONG_PRESS_TEXT,
    INPUT_TEXT,
    SCROLL_DOWN,
    SCROLL_UP,
    BACK,
    HOME,
    OPEN_NOTIFICATIONS,
    OPEN_RECENTS,
    WAIT,
    DONE
}

enum class AccessibilityCommandType {
    TAP_TEXT,
    LONG_PRESS_TEXT,
    INPUT_TEXT,
    SCROLL_DOWN,
    SCROLL_UP,
    BACK,
    HOME,
    OPEN_NOTIFICATIONS,
    OPEN_RECENTS
}
