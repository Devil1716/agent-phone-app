package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.GoalCategory

class AccessibilityExecutor(private val aiProvider: AiProvider? = null) : ActionExecutor {
    override fun canExecute(step: TaskStep): Boolean {
        return step.type == StepType.SUMMARIZE_NOTIFICATIONS || 
               step.type == StepType.REPORT_RESULT || 
               step.type == StepType.EXECUTE_AUTONOMOUSLY
    }

    override fun execute(step: TaskStep, observation: ScreenObservation): StepResult {
        val message = when (step.type) {
            StepType.SUMMARIZE_NOTIFICATIONS -> {
                val summaries = AgentNotificationListener.getActiveNotificationSummaries()
                buildString {
                    appendLine("Notification Summary:")
                    summaries.forEach { appendLine("• $it") }
                }
            }
            StepType.REPORT_RESULT -> "This command falls back to the slower general app-control planner"
            StepType.EXECUTE_AUTONOMOUSLY -> {
                if (aiProvider == null) {
                    "Error: No AI provider available to execute autonomous task."
                } else {
                    val request = AiRequest(
                        prompt = step.payload ?: "No prompt provided",
                        mode = "autonomous",
                        targetCategory = GoalCategory.GENERAL_APP_CONTROL
                    )
                    
                    val response = aiProvider.infer(request)
                    AgentAccessibilityService.dispatchAutonomousAction()
                    
                    "Gemma 4 autonomously analyzed the screen and took action: ${response.summary} (Latency: ${response.latencyMs}ms)"
                }
            }
            else -> "Accessibility executor skipped"
        }

        return StepResult(
            stepId = step.id,
            status = StepStatus.SUCCESS,
            message = message,
            executorName = "AccessibilityExecutor"
        )
    }
}
