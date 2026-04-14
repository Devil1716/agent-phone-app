package com.gemma.agentphone.agent

class BrowserExecutor : ActionExecutor {
    override fun canExecute(step: TaskStep): Boolean {
        return step.type in listOf(StepType.OPEN_BROWSER_SEARCH, StepType.OPEN_MEDIA_SEARCH)
    }

    override fun execute(step: TaskStep, observation: ScreenObservation): StepResult {
        return when (step.type) {
            StepType.OPEN_BROWSER_SEARCH -> {
                val query = (step.payload ?: "").trim().replace(" ", "+")
                StepResult(
                    stepId = step.id,
                    status = StepStatus.SUCCESS,
                    message = "Prepared browser search request",
                    executorName = "BrowserExecutor",
                    externalAction = ExternalActionRequest(
                        spec = IntentSpec(
                            action = "android.intent.action.VIEW",
                            data = "https://www.google.com/search?q=$query"
                        )
                    )
                )
            }

            StepType.OPEN_MEDIA_SEARCH -> {
                val query = (step.payload ?: "").trim().replace(" ", "+")
                StepResult(
                    stepId = step.id,
                    status = StepStatus.SUCCESS,
                    message = "Prepared media search request",
                    executorName = "BrowserExecutor",
                    externalAction = ExternalActionRequest(
                        spec = IntentSpec(
                            action = "android.intent.action.VIEW",
                            data = "https://m.youtube.com/results?search_query=$query"
                        )
                    )
                )
            }

            else -> StepResult(
                stepId = step.id,
                status = StepStatus.SKIPPED,
                message = "Browser executor skipped",
                executorName = "BrowserExecutor"
            )
        }
    }
}
