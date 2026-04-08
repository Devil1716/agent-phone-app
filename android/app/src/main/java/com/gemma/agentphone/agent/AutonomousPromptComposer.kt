package com.gemma.agentphone.agent

class AutonomousPromptComposer(
    private val customPrompt: String,
    private val autonomyMode: String
) {
    fun compose(command: String, observation: ScreenObservation): String {
        val visibleText = observation.visibleText
            .filter { it.isNotBlank() }
            .take(12)
            .joinToString(separator = "\n- ", prefix = "- ")
            .ifBlank { "- No visible text captured." }

        return buildString {
            appendLine("You are an Android phone-control agent.")
            appendLine("Autonomy mode: $autonomyMode")
            appendLine("User command: $command")
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
            appendLine("Decide the best next action for the user's command.")
            appendLine("If messaging is requested and the custom prompt prefers WhatsApp, choose WhatsApp.")
            appendLine("Respond using this format when possible:")
            appendLine("ACTION: <PLAY_STORE_SEARCH|WHATSAPP_MESSAGE|WEB_SEARCH|OPEN_APP>")
            appendLine("QUERY: <query if needed>")
            appendLine("TEXT: <message text if needed>")
            appendLine("APP: <package name if opening a specific app>")
            appendLine("NOTE: <short explanation>")
        }
    }
}
