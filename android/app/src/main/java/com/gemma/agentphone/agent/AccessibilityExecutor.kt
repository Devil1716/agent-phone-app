package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.GoalCategory

class AccessibilityExecutor(
    private val aiProvider: AiProvider? = null,
    private val customPrompt: String = "",
    private val autonomyMode: String = "confirmed-action",
    private val actionParser: AutonomousActionParser = AutonomousActionParser()
) : ActionExecutor {
    override fun canExecute(step: TaskStep): Boolean {
        return step.type == StepType.SUMMARIZE_NOTIFICATIONS ||
            step.type == StepType.REPORT_RESULT ||
            step.type == StepType.EXECUTE_AUTONOMOUSLY
    }

    override fun execute(step: TaskStep, observation: ScreenObservation): StepResult {
        var externalAction: ExternalActionRequest? = null
        val message = when (step.type) {
            StepType.SUMMARIZE_NOTIFICATIONS -> {
                val summaries = AgentNotificationListener.getActiveNotificationSummaries()
                buildString {
                    appendLine("Notification Summary:")
                    summaries.forEach { appendLine("- $it") }
                }
            }

            StepType.REPORT_RESULT -> "This command falls back to the slower general app-control planner"

            StepType.EXECUTE_AUTONOMOUSLY -> {
                if (aiProvider == null) {
                    "Error: No AI provider is available to reason about this command."
                } else {
                    val request = AiRequest(
                        prompt = AutonomousPromptComposer(
                            customPrompt = customPrompt,
                            autonomyMode = autonomyMode
                        ).compose(
                            command = step.payload.orEmpty(),
                            observation = observation,
                            understanding = step.contextHint
                        ),
                        mode = "autonomous",
                        targetCategory = GoalCategory.GENERAL_APP_CONTROL
                    )

                    val response = aiProvider.infer(request)
                    val parsedAction = actionParser.parse(
                        command = step.payload.orEmpty(),
                        responseSummary = response.summary
                    )
                    val thought = parsedAction?.thought
                    externalAction = parsedAction?.externalAction
                    val accessibilityResult = parsedAction?.accessibilityCommand?.let(AgentAccessibilityService::executeCommand)

                    val status = when {
                        parsedAction == null -> StepStatus.SKIPPED
                        accessibilityResult != null && !accessibilityResult.success -> StepStatus.SKIPPED
                        else -> StepStatus.SUCCESS
                    }
                    val actionLabel = parsedAction?.action?.name ?: "UNPARSEABLE"
                    val detail = when {
                        parsedAction == null -> "The model response was not actionable. Expected a structured ACTION block."
                        accessibilityResult != null -> accessibilityResult.message
                        parsedAction.action == AutonomousActionType.DONE -> "ACTION: DONE"
                        parsedAction.action == AutonomousActionType.WAIT -> "ACTION: WAIT"
                        else -> "ACTION: $actionLabel"
                    }

                    return StepResult(
                        stepId = step.id,
                        status = status,
                        message = detail,
                        executorName = "AccessibilityExecutor",
                        thought = thought ?: response.summary.lineSequence().firstOrNull()?.trim(),
                        externalAction = externalAction
                    )
                }
            }

            else -> "Accessibility executor skipped"
        }

        return StepResult(
            stepId = step.id,
            status = StepStatus.SUCCESS,
            message = message,
            executorName = "AccessibilityExecutor",
            externalAction = externalAction
        )
    }
}
