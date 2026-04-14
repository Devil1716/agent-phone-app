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
                            observation = observation
                        ),
                        mode = "autonomous",
                        targetCategory = GoalCategory.GENERAL_APP_CONTROL
                    )

                    val response = aiProvider.infer(request)
                    externalAction = actionParser.parse(
                        command = step.payload.orEmpty(),
                        responseSummary = response.summary
                    ) ?: AgentAccessibilityService.dispatchAutonomousAction(response.summary)

                    val thought = externalAction?.thought
                    return StepResult(
                        stepId = step.id,
                        status = StepStatus.SUCCESS,
                        message = "Local Gemma suggested the next app-control step: ${response.summary} (Latency: ${response.latencyMs}ms)",
                        executorName = "AccessibilityExecutor",
                        thought = thought,
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
