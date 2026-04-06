package com.gemma.agentphone.agent

class IntentExecutor : ActionExecutor {
    override fun canExecute(step: TaskStep): Boolean {
        return step.type in listOf(StepType.OPEN_SYSTEM_SETTINGS, StepType.OPEN_MAPS, StepType.DRAFT_MESSAGE)
    }

    override fun execute(step: TaskStep, observation: ScreenObservation): StepResult {
        return when (step.type) {
            StepType.OPEN_SYSTEM_SETTINGS -> StepResult(
                stepId = step.id,
                status = StepStatus.SUCCESS,
                message = "Prepared Wi-Fi settings intent",
                executorName = "IntentExecutor",
                externalAction = ExternalActionRequest(IntentSpec(action = "android.settings.WIFI_SETTINGS"))
            )

            StepType.OPEN_MAPS -> {
                val query = (step.payload ?: "home").ifBlank { "home" }.replace(" ", "+")
                StepResult(
                    stepId = step.id,
                    status = StepStatus.SUCCESS,
                    message = "Prepared maps navigation intent",
                    executorName = "IntentExecutor",
                    externalAction = ExternalActionRequest(
                        IntentSpec(
                            action = "android.intent.action.VIEW",
                            data = "google.navigation:q=$query"
                        )
                    )
                )
            }

            StepType.DRAFT_MESSAGE -> {
                val body = (step.payload ?: "").replace(" ", "%20")
                StepResult(
                    stepId = step.id,
                    status = StepStatus.SUCCESS,
                    message = "Prepared SMS draft intent",
                    executorName = "IntentExecutor",
                    externalAction = ExternalActionRequest(
                        IntentSpec(
                            action = "android.intent.action.SENDTO",
                            data = "smsto:?body=$body"
                        )
                    )
                )
            }

            else -> StepResult(step.id, StepStatus.SKIPPED, "Intent executor skipped", "IntentExecutor")
        }
    }
}
