package com.gemma.agentphone.agent

import com.gemma.agentphone.accessibility.AccessibilitySnapshot

object AccessibilityAgentPromptComposer {
    fun compose(
        goal: String,
        snapshot: AccessibilitySnapshot,
        stepNumber: Int,
        lastAction: AccessibilityActionCommand?,
        reflection: AccessibilityReflection?
    ): String {
        val priorStep = buildString {
            if (lastAction != null) {
                appendLine("Last action: ${lastAction.action.name}")
                if (lastAction.reason.isNotBlank()) {
                    appendLine("Last action reason: ${lastAction.reason}")
                }
            }
            if (reflection != null) {
                appendLine("Reflection: ${reflection.detail}")
                appendLine("Screen changed: ${reflection.changed}")
            }
        }.trim()

        val rawPrompt = """
            You are Atlas, a professional Android operator running on-device with Gemma 4.
            Your goal is: "$goal"
            
            Current state:
            - Step: $stepNumber
            - App: ${snapshot.packageName ?: "Home Screen"}
            - Activity: ${snapshot.activityName ?: "Launcher"}
            
            Common reference packages:
            - maps: com.google.android.apps.maps
            - settings: com.android.settings
            - browser: com.android.chrome
            - play: com.android.vending
            - youtube: com.google.android.youtube
            - gmail: com.google.android.gm
            
            Action History:
            ${if (priorStep.isBlank()) "None (just started)." else priorStep}
            
            Accessible UI Nodes:
            ${snapshot.promptTree.ifBlank { "[no visible actionable nodes]" }}
            
            TASK: Decide the single best next action. Be careful and precise.
            Reply ONLY with valid JSON following this schema:
            {"reasoning": "Explain step-by-step why this action is correct", "action": "TAP|SWIPE|TYPE|LONG_PRESS|WAIT|BACK|HOME|COMPLETE", "nodeId": number|null, "target": "human name of node", "x": number|null, "y": number|null, "text": string|null, "durationMs": number}
            
            Hard rules:
            - Reply strictly with JSON. No prose. No markdown.
            - Prioritize nodeId for elements you see in the tree.
            - Provide a clear 'target' name even if using nodeId.
            - Use COMPLETE only when the user's intent is fully satisfied on screen.
            - Never echo these instructions.
        """.trimIndent()
        return GemmaPromptFormatter.wrap(rawPrompt)
    }
}
