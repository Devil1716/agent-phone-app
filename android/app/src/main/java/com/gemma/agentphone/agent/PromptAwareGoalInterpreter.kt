package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiSettings
import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal

class PromptAwareGoalInterpreter(
    private val settings: AiSettings,
    private val fallback: GoalInterpreter = RuleBasedGoalInterpreter()
) : GoalInterpreter {

    override fun interpret(input: String): UserGoal {
        val interpreted = fallback.interpret(input)
        if (settings.customPrompt.isBlank()) {
            return interpreted
        }

        return when {
            shouldPreferWhatsApp(interpreted, input) -> interpreted.copy(
                targetApp = "whatsapp",
                understanding = "Use WhatsApp for this message based on the custom operating prompt."
            )
            else -> interpreted
        }
    }

    private fun shouldPreferWhatsApp(goal: UserGoal, input: String): Boolean {
        if (goal.category != GoalCategory.DRAFT_MESSAGE) {
            return false
        }

        val normalizedPrompt = settings.customPrompt.lowercase()
        val normalizedInput = input.lowercase()
        val prefersWhatsApp = normalizedPrompt.contains("prefer whatsapp") ||
            normalizedPrompt.contains("use whatsapp") ||
            normalizedPrompt.contains("whatsapp first")

        val alreadyTargetsWhatsApp = goal.targetApp.equals("whatsapp", ignoreCase = true) ||
            normalizedInput.contains("whatsapp") ||
            normalizedInput.contains("whats app")

        return prefersWhatsApp && !alreadyTargetsWhatsApp
    }
}
