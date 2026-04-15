package com.gemma.agentphone.actions

import com.gemma.agentphone.accessibility.PhoneControlService
import com.gemma.agentphone.agent.AgentStep
import kotlinx.coroutines.delay

class InputTextAction(
    private val serviceProvider: () -> PhoneControlService?
) {
    suspend fun execute(step: AgentStep): ActionResult {
        val service = serviceProvider() ?: return ActionResult(false, "Accessibility service is not enabled.")
        val target = step.target.trim()
        val value = step.value
        if (value.isBlank()) {
            return ActionResult(false, "No input text was provided.")
        }

        if (target.isNotBlank() && target !in setOf("field", "text", "address_bar", "bar")) {
            service.tapByText(target)
            delay(350L)
        }

        return if (service.inputText(value)) {
            ActionResult(true, "Entered text.")
        } else {
            ActionResult(false, "Could not enter text into the current field.")
        }
    }
}
