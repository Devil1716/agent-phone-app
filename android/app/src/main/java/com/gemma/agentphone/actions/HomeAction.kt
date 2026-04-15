package com.gemma.agentphone.actions

import com.gemma.agentphone.accessibility.PhoneControlService
import com.gemma.agentphone.agent.AgentStep

class HomeAction(
    private val serviceProvider: () -> PhoneControlService?
) {
    suspend fun execute(step: AgentStep): ActionResult {
        val success = serviceProvider()?.pressHome() == true
        return if (success) {
            ActionResult(true, "Pressed home.")
        } else {
            ActionResult(false, "Could not press home.")
        }
    }
}
