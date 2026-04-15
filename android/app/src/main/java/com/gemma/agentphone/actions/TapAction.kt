package com.gemma.agentphone.actions

import com.gemma.agentphone.accessibility.PhoneControlService
import com.gemma.agentphone.agent.AgentStep

class TapAction(
    private val serviceProvider: () -> PhoneControlService?
) {
    suspend fun execute(step: AgentStep): ActionResult {
        val service = serviceProvider() ?: return ActionResult(false, "Accessibility service is not enabled.")
        return if (step.normalizedAction == "TAP_COORDS") {
            val (x, y) = parseCoordinates(step.target.ifBlank { step.value })
                ?: return ActionResult(false, "Invalid tap coordinates.")
            if (service.tapByCoords(x, y)) {
                ActionResult(true, "Tapped coordinates $x,$y.")
            } else {
                ActionResult(false, "Could not tap coordinates $x,$y.")
            }
        } else {
            val targetText = step.target.ifBlank { step.value }
            if (targetText.isBlank()) {
                ActionResult(false, "No visible text target was provided.")
            } else if (service.tapByText(targetText)) {
                ActionResult(true, "Tapped \"$targetText\".")
            } else {
                ActionResult(false, "Could not find \"$targetText\" on screen.")
            }
        }
    }

    private fun parseCoordinates(raw: String): Pair<Float, Float>? {
        val parts = raw.split(',', ' ').filter { it.isNotBlank() }
        if (parts.size < 2) {
            return null
        }
        val x = parts[0].toFloatOrNull() ?: return null
        val y = parts[1].toFloatOrNull() ?: return null
        return x to y
    }
}
