package com.gemma.agentphone.actions

import com.gemma.agentphone.agent.AgentStep
import kotlinx.coroutines.delay

class WaitAction {
    suspend fun execute(step: AgentStep): ActionResult {
        val waitMs = step.value.toLongOrNull() ?: 1_000L
        delay(waitMs)
        return ActionResult(true, "Waited ${waitMs}ms.")
    }
}
