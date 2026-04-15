package com.gemma.agentphone.actions

import com.gemma.agentphone.accessibility.PhoneControlService
import kotlinx.coroutines.delay

enum class ScrollDirection {
    DOWN,
    UP
}

class ScrollAction(
    private val direction: ScrollDirection,
    private val serviceProvider: () -> PhoneControlService?
) {
    suspend fun execute(step: com.gemma.agentphone.agent.AgentStep): ActionResult {
        val service = serviceProvider() ?: return ActionResult(false, "Accessibility service is not enabled.")
        repeat(3) { attempt ->
            val success = if (direction == ScrollDirection.DOWN) {
                service.scrollDown()
            } else {
                service.scrollUp()
            }
            if (success) {
                return ActionResult(true, "Scrolled ${direction.name.lowercase()}.")
            }
            if (attempt < 2) {
                delay(500L)
            }
        }
        return ActionResult(false, "Could not scroll ${direction.name.lowercase()}.")
    }
}
