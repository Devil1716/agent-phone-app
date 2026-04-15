package com.gemma.agentphone.agent

class AutonomousPromptComposer(
    private val customPrompt: String,
    private val autonomyMode: String
) {
    fun compose(command: String, observation: ScreenObservation, understanding: String? = null): String {
        val visibleText = observation.visibleText
            .filter { it.isNotBlank() }
            .take(18)
            .joinToString(separator = "\n- ", prefix = "- ")
            .ifBlank { "- No visible text captured." }

        return buildString {
            appendLine("You are an Android phone-control agent.")
            appendLine("Autonomy mode: $autonomyMode")
            appendLine("User command: $command")
            understanding?.takeIf { it.isNotBlank() }?.let {
                appendLine("Interpreted goal: $it")
            }
            appendLine("Current foreground app: ${observation.foregroundApp}")
            appendLine("Visible screen text:")
            appendLine(visibleText)
            appendLine()
            if (customPrompt.isNotBlank()) {
                appendLine("Custom operating prompt:")
                appendLine(customPrompt)
                appendLine()
            }
            appendLine("Follow the custom operating prompt if it is relevant.")
            appendLine("Decide exactly one safe next action for the user's command based on the live screen.")
            appendLine("Do not explain hidden reasoning. Provide only a brief THOUGHT summary that the user can see in the trace.")
            appendLine("If messaging is requested and the custom prompt prefers WhatsApp, choose WhatsApp.")
            appendLine("Prefer direct UI control when the target is visible on screen.")
            appendLine("If the goal is already complete, answer with ACTION: DONE.")
            appendLine("If the screen is transitioning, answer with ACTION: WAIT.")
            appendLine("Respond using this exact schema:")
            appendLine("THOUGHT: <one short user-visible sentence>")
            appendLine("ACTION: <TAP_TEXT|LONG_PRESS_TEXT|INPUT_TEXT|SCROLL_DOWN|SCROLL_UP|BACK|HOME|OPEN_NOTIFICATIONS|OPEN_RECENTS|OPEN_APP|OPEN_URL|WEB_SEARCH|PLAY_STORE_SEARCH|WHATSAPP_MESSAGE|WAIT|DONE>")
            appendLine("TARGET: <visible label to tap, long press, or focus when needed>")
            appendLine("TEXT: <text to type or message body when needed>")
            appendLine("QUERY: <search query when needed>")
            appendLine("APP: <package name when ACTION is OPEN_APP>")
            appendLine("URL: <absolute url when ACTION is OPEN_URL>")
        }
    }
}
