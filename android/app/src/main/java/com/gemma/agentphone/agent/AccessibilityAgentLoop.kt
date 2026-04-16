package com.gemma.agentphone.agent

import android.content.Context
import com.gemma.agentphone.accessibility.AccessibilitySnapshot
import com.gemma.agentphone.accessibility.PhoneControlService
import kotlinx.coroutines.delay

class AccessibilityAgentLoop(
    private val context: Context,
    private val engine: TextGenerationEngine,
    private val parser: AccessibilityActionParser = AccessibilityActionParser(),
    private val dispatcher: AccessibilityActionDispatcher = AccessibilityActionDispatcher(),
    private val grounder: AccessibilityNodeGrounder = AccessibilityNodeGrounder()
) {
    companion object {
        private const val MAX_STEPS = 18
        private const val MAX_STAGNANT_STEPS = 3
        private const val POST_ACTION_DELAY_MS = 550L
    }

    suspend fun execute(
        goal: String,
        emitStatus: (AgentStatus) -> Unit,
        emitLog: (AgentLogEntry) -> Unit,
        shouldStop: () -> Boolean
    ): AgentExecutionSummary {
        if (!PhoneControlService.isEnabled(context)) {
            return AgentExecutionSummary(
                success = false,
                summary = "Enable the Atlas accessibility service before running autonomous control."
            )
        }

        var snapshot = PhoneControlService.instance?.captureSnapshot()
            ?: return AgentExecutionSummary(
                success = false,
                summary = "Atlas could not read the current screen. Bring the target app to the foreground and retry."
            )

        emitLog(
            AgentLogEntry(
                title = "Perceive",
                detail = perceptionLog(snapshot),
                success = true
            )
        )

        var lastAction: AccessibilityActionCommand? = null
        var lastReflection: AccessibilityReflection? = null
        var stagnantSteps = 0

        for (stepIndex in 1..MAX_STEPS) {
            if (shouldStop()) {
                return AgentExecutionSummary(false, "Execution stopped by the user.")
            }

            emitStatus(AgentStatus.Planning(goal))
            val action = requestAction(
                goal = goal,
                snapshot = snapshot,
                stepIndex = stepIndex,
                lastAction = lastAction,
                reflection = lastReflection
            )
            val agentStep = action.toAgentStep()

            emitLog(
                AgentLogEntry(
                    title = "Plan",
                    detail = agentStep.summary(),
                    success = null,
                    step = agentStep
                )
            )

            if (action.action == AccessibilityActionType.COMPLETE) {
                return AgentExecutionSummary(
                    success = true,
                    summary = agentStep.reason.ifBlank { "The goal is complete." }
                )
            }

            emitStatus(AgentStatus.Executing(agentStep, stepIndex, MAX_STEPS))
            val dispatchResult = dispatcher.dispatch(action)
            emitLog(
                AgentLogEntry(
                    title = "Act",
                    detail = dispatchResult.detail,
                    success = dispatchResult.success,
                    step = agentStep
                )
            )

            if (!dispatchResult.success) {
                stagnantSteps += 1
                lastAction = action
                lastReflection = AccessibilityReflection(
                    changed = false,
                    detail = dispatchResult.detail
                )
                if (stagnantSteps >= MAX_STAGNANT_STEPS) {
                    return AgentExecutionSummary(
                        success = false,
                        summary = "Atlas could not make progress on the current screen."
                    )
                }
                continue
            }

            if (action.action != AccessibilityActionType.WAIT) {
                delay(POST_ACTION_DELAY_MS)
            }

            val refreshed = PhoneControlService.instance?.captureSnapshot()
                ?: return AgentExecutionSummary(
                    success = false,
                    summary = "Atlas lost access to the live screen after acting."
                )

            val changed = screenChanged(snapshot, refreshed)
            val reflection = AccessibilityReflection(
                changed = changed,
                detail = if (changed) {
                    "The accessibility tree changed after ${action.action.name.lowercase()}."
                } else {
                    "The accessibility tree did not change after ${action.action.name.lowercase()}."
                }
            )
            emitLog(
                AgentLogEntry(
                    title = "Reflect",
                    detail = reflection.detail,
                    success = changed || action.action == AccessibilityActionType.WAIT,
                    step = agentStep
                )
            )

            stagnantSteps = if (changed || action.action == AccessibilityActionType.WAIT) {
                0
            } else {
                stagnantSteps + 1
            }
            if (stagnantSteps >= MAX_STAGNANT_STEPS) {
                return AgentExecutionSummary(
                    success = false,
                    summary = "Atlas reached a stagnant state without visible UI progress."
                )
            }

            lastAction = action
            lastReflection = reflection
            snapshot = refreshed
        }

        return AgentExecutionSummary(
            success = false,
            summary = "Atlas reached the ${MAX_STEPS}-step safety limit."
        )
    }

    private suspend fun requestAction(
        goal: String,
        snapshot: AccessibilitySnapshot,
        stepIndex: Int,
        lastAction: AccessibilityActionCommand?,
        reflection: AccessibilityReflection?
    ): AccessibilityActionCommand {
        var previousResponse = ""
        repeat(3) { attempt ->
            val rawResponse = engine.generate(
                if (attempt == 0) {
                    AccessibilityAgentPromptComposer.compose(
                        goal = goal,
                        snapshot = snapshot,
                        stepNumber = stepIndex,
                        lastAction = lastAction,
                        reflection = reflection
                    )
                } else {
                    GemmaPromptFormatter.wrap(
                        """
                        Rewrite the previous answer as ONLY one valid JSON object matching the agreed schema.
                        Previous answer:
                        $previousResponse
                        """.trimIndent()
                    )
                }
            )
            // Strip any Gemma chat-template markers the model echoed back before parsing.
            previousResponse = GemmaPromptFormatter.stripMarkers(rawResponse)
            runCatching { parser.parse(previousResponse) }.getOrNull()?.let { command ->
                return groundCommand(command, snapshot)
            }
        }

        throw IllegalStateException("Gemma 4 did not return valid JSON for the next action.")
    }

    /**
     * Attempts to resolve a missing nodeId using [AccessibilityNodeGrounder] when Gemma 4
     * describes a target in the reason field instead of providing a numeric nodeId.
     * For actions that require a target (TAP, LONG_PRESS), this is a best-effort fallback;
     * if grounding fails the original (potentially incomplete) command is returned as-is so
     * the dispatcher can apply its own heuristics or fail gracefully.
     */
    private fun groundCommand(
        command: AccessibilityActionCommand,
        snapshot: AccessibilitySnapshot
    ): AccessibilityActionCommand {
        val needsGrounding = command.nodeId == null &&
            command.x == null &&
            command.reason.isNotBlank() &&
            (command.action == AccessibilityActionType.TAP ||
                command.action == AccessibilityActionType.LONG_PRESS)
        if (!needsGrounding) {
            return command
        }
        val resolvedId = grounder.groundByText(command.reason, snapshot)
            ?: grounder.groundByText(command.text, snapshot)
        return if (resolvedId != null) {
            command.copy(nodeId = resolvedId)
        } else {
            command
        }
    }

    private fun perceptionLog(snapshot: AccessibilitySnapshot): String {
        val preview = snapshot.preview()
        return buildString {
            append("Captured ")
            append(snapshot.nodes.size)
            append(" actionable nodes from ")
            append(snapshot.packageName)
            if (preview.isNotBlank()) {
                append('\n')
                append(preview)
            }
        }
    }

    private fun screenChanged(before: AccessibilitySnapshot, after: AccessibilitySnapshot): Boolean {
        return before.packageName != after.packageName ||
            before.activityName != after.activityName ||
            before.promptTree != after.promptTree
    }
}
