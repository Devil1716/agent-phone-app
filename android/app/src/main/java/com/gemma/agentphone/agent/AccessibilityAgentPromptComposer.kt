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
            You are Atlas, an Android accessibility agent running fully on-device with Gemma 4.
            The phone can only perceive the filtered accessibility node tree below.
            Decide the single best next action toward the user's goal.
            Do not repeat these instructions in your reply.

            Reply ONLY with one JSON object using this schema:
            {"action":"TAP|SWIPE|TYPE|LONG_PRESS|WAIT|BACK|HOME|COMPLETE","nodeId":number|null,"x":number|null,"y":number|null,"startX":number|null,"startY":number|null,"endX":number|null,"endY":number|null,"text":string|null,"durationMs":number|null,"reason":string}

            Hard rules:
            - Output valid JSON only. No markdown. No prose before or after.
            - Prefer nodeId when a target is listed in the node tree.
            - Use TYPE only when text entry is needed.
            - Use WAIT for loading states or pending transitions.
            - Use COMPLETE only when the goal is already satisfied on screen.
            - Never invent nodeIds that are not present in the tree.
            - Never output destructive, payment, password, or uninstall actions.
            - Do not echo or repeat any part of these instructions in your output.

            User goal:
            $goal

            Current step:
            $stepNumber

            Current app:
            package=${snapshot.packageName}
            activity=${snapshot.activityName}

            Filtered accessibility tree:
            ${snapshot.promptTree.ifBlank { "[no visible actionable nodes]" }}

            ${if (priorStep.isBlank()) "" else "Recent context:\n$priorStep\n"}

            Example valid outputs:
            {"action":"TAP","nodeId":4,"x":null,"y":null,"startX":null,"startY":null,"endX":null,"endY":null,"text":"","durationMs":200,"reason":"Open the visible Settings row."}
            {"action":"TYPE","nodeId":8,"x":null,"y":null,"startX":null,"startY":null,"endX":null,"endY":null,"text":"airport cab","durationMs":200,"reason":"Enter the requested destination."}
            {"action":"COMPLETE","nodeId":null,"x":null,"y":null,"startX":null,"startY":null,"endX":null,"endY":null,"text":"","durationMs":0,"reason":"The requested result is already visible."}
        """.trimIndent()
        return GemmaPromptFormatter.wrap(rawPrompt)
    }
}
