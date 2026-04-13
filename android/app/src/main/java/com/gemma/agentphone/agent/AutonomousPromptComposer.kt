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
            appendLine("Analyze the request carefully and provide your reasoning before choosing exactly one next action.")
            appendLine("The goal is to provide a smooth, Manus-like experience by thinking ahead.")
            appendLine("If the task is finished, use ACTION: DONE.")
            appendLine("If you need to wait for a screen to load or an animation to finish, use ACTION: WAIT.")
            appendLine()
            appendLine("Respond using this format only:")
            appendLine("THOUGHT: <your step-by-step reasoning about the current screen and what to do next>")
            appendLine("ACTION: <PLAY_STORE_SEARCH|WHATSAPP_MESSAGE|WEB_SEARCH|OPEN_APP|OPEN_SETTINGS|OPEN_MAPS|OPEN_MEDIA_SEARCH|GO_HOME|GO_BACK|OPEN_NOTIFICATIONS|TAP_TEXT|LONG_PRESS|SCROLL_UP|SCROLL_DOWN|WAIT|DONE>")
            appendLine("QUERY: <search query, app label, map destination, or settings target>")
            appendLine("TEXT: <message text or visible button text to tap/long-press>")
            appendLine("APP: <Android package name if known>")
            appendLine("NOTE: <short explanation of why this is the safest next step>")
        }
    }
}
