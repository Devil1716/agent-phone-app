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
                    step.id,
                    StepStatus.SUCCESS,
                    "Prepared browser search request",
                    "BrowserExecutor",
                    ExternalActionRequest(
                        IntentSpec(
                            action = "android.intent.action.VIEW",
                            data = "https://www.google.com/search?q=$query"
                        )
                    )
                )
            }

            StepType.OPEN_MEDIA_SEARCH -> {
                val query = (step.payload ?: "").trim().replace(" ", "+")
                StepResult(
                    step.id,
                    StepStatus.SUCCESS,
                    "Prepared media search request",
                    "BrowserExecutor",
                    ExternalActionRequest(
                        IntentSpec(
                            action = "android.intent.action.VIEW",
                            data = "https://m.youtube.com/results?search_query=$query"
                        )
                    )
                )
            }

            else -> StepResult(step.id, StepStatus.SKIPPED, "Browser executor skipped", "BrowserExecutor")
        }
    }
}
