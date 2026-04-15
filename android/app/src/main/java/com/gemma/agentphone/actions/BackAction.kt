package com.gemma.agentphone.actions

import com.gemma.agentphone.accessibility.PhoneControlService
import com.gemma.agentphone.agent.AgentStep

class BackAction(
    private val serviceProvider: () -> PhoneControlService?
) {
    suspend fun execute(step: AgentStep): ActionResult {
        val success = serviceProvider()?.pressBack() == true
        return if (success) {
            ActionResult(true, "Pressed back.")
        } else {
            ActionResult(false, "Could not press back.")
        }
    }
}
