package com.gemma.agentphone.agent

class AutonomousPromptComposer(
    private val customPrompt: String,
    private val autonomyMode: String
) {
    fun compose(command: String, observation: ScreenObservation): String {
        val visibleText = observation.visibleText
            .filter { it.isNotBlank() }
            .map { it.trim().take(80) }
            .take(8)
            .joinToString(separator = "\n- ", prefix = "- ")
            .ifBlank { "- No visible text captured." }
        val promptContext = customPrompt.trim().take(280)

        return buildString {
            appendLine("You are an Android phone-control agent.")
            appendLine("Autonomy mode: $autonomyMode")
            appendLine("User command: $command")
            appendLine("Current foreground app: ${observation.foregroundApp}")
            appendLine("Visible screen text:")
            appendLine(visibleText)
            appendLine()
            if (promptContext.isNotBlank()) {
                appendLine("Custom operating prompt:")
                appendLine(promptContext)
                appendLine()
            }
            appendLine("Follow the custom operating prompt if it is relevant.")
            appendLine("Analyze the request carefully before choosing exactly one next action.")
            appendLine("Prefer opening, drafting, or navigating over taking irreversible actions.")
            appendLine("Do not claim that you already tapped or sent something.")
            appendLine("If messaging is requested and the custom prompt prefers WhatsApp, choose WhatsApp.")
            appendLine("Respond using this format only:")
            appendLine("ACTION: <PLAY_STORE_SEARCH|WHATSAPP_MESSAGE|WEB_SEARCH|OPEN_APP|OPEN_SETTINGS|OPEN_MAPS|OPEN_MEDIA_SEARCH|GO_HOME|GO_BACK|OPEN_NOTIFICATIONS|TAP_TEXT>")
            appendLine("QUERY: <search query, app label, map destination, or settings target>")
            appendLine("TEXT: <message text or visible button text>")
            appendLine("APP: <Android package name if known>")
            appendLine("NOTE: <short explanation of why this is the safest next step>")
        }
    }
}
