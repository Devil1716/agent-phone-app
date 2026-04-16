package com.gemma.agentphone.agent

enum class AccessibilityActionType {
    TAP,
    SWIPE,
    TYPE,
    LONG_PRESS,
    WAIT,
    BACK,
    HOME,
    COMPLETE
}

data class AccessibilityActionCommand(
    val action: AccessibilityActionType,
    val nodeId: Int? = null,
    val x: Int? = null,
    val y: Int? = null,
    val startX: Int? = null,
    val startY: Int? = null,
    val endX: Int? = null,
    val endY: Int? = null,
    val text: String = "",
    val durationMs: Long = 650L,
    val reason: String = "",
    val reasoning: String = "",
    val target: String = ""
) {
    fun toAgentStep(): AgentStep {
        val defaultTarget = when {
            nodeId != null -> "node#$nodeId"
            x != null && y != null -> "$x,$y"
            startX != null && startY != null && endX != null && endY != null ->
                "$startX,$startY -> $endX,$endY"
            else -> ""
        }
        val value = when (action) {
            AccessibilityActionType.TYPE -> text
            AccessibilityActionType.WAIT -> durationMs.toString()
            else -> ""
        }
        return AgentStep(
            action = action.name,
            target = target.ifBlank { defaultTarget },
            value = value,
            reason = reasoning.ifBlank { reason.ifBlank { defaultReason() } }
        )
    }

    private fun defaultReason(): String {
        return when (action) {
            AccessibilityActionType.TAP -> "Tap the selected UI element."
            AccessibilityActionType.SWIPE -> "Swipe across the current screen."
            AccessibilityActionType.TYPE -> "Enter text into the focused field."
            AccessibilityActionType.LONG_PRESS -> "Long-press the selected UI element."
            AccessibilityActionType.WAIT -> "Pause for the UI to finish updating."
            AccessibilityActionType.BACK -> "Go back one screen."
            AccessibilityActionType.HOME -> "Return to the launcher."
            AccessibilityActionType.COMPLETE -> "The goal looks complete."
        }
    }
}

data class AccessibilityReflection(
    val changed: Boolean,
    val detail: String
)

data class AccessibilityDispatchResult(
    val success: Boolean,
    val detail: String
)
