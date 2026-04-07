package com.gemma.agentphone.agent

class AccessibilityExecutor : ActionExecutor {
    override fun canExecute(step: TaskStep): Boolean {
        return step.type == StepType.SUMMARIZE_NOTIFICATIONS || step.type == StepType.REPORT_RESULT
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
