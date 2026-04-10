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
        var status = StepStatus.SUCCESS
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
                    status = StepStatus.SKIPPED
                    "No AI provider is available to reason about this command. Configure a local model or fallback provider first."
                } else {
                    try {
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
                        )
                        val performedAccessibilityAction = if (externalAction == null) {
                            AgentAccessibilityService.dispatchAutonomousAction(response.summary)
                        } else {
                            false
                        }
                        val runtimeUnavailable = response.summary.contains("not ready", ignoreCase = true) ||
                            response.summary.contains("did not return", ignoreCase = true) ||
                            response.summary.contains("error", ignoreCase = true)

                        when {
                            externalAction != null || performedAccessibilityAction -> {
                                "Local Gemma suggested the next app-control step: ${response.summary} (Latency: ${response.latencyMs}ms)"
                            }

                            runtimeUnavailable -> {
                                status = StepStatus.SKIPPED
                                "The agent could not act because the local runtime was unavailable. Response: ${response.summary}"
                            }

                            !AgentAccessibilityService.isConnected() -> {
                                status = StepStatus.SKIPPED
                                "The agent produced a plan, but Accessibility access is not enabled, so it cannot control the phone yet. Response: ${response.summary}"
                            }

                            else -> {
                                status = StepStatus.SKIPPED
                                "The agent analyzed the request, but the response did not map to a supported phone action yet. Response: ${response.summary}"
                            }
                        }
                    } catch (exception: Exception) {
                        status = StepStatus.SKIPPED
                        "The local Gemma runtime failed while preparing this task. ${exception.localizedMessage ?: "Retry after redownloading the model or enabling fallback."}"
                    }
                }
            }

            else -> "Accessibility executor skipped"
        }

        return StepResult(
            stepId = step.id,
            status = status,
            message = message,
            executorName = "AccessibilityExecutor",
            externalAction = externalAction
        )
    }
}
