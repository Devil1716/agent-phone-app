package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiSettings
import com.gemma.agentphone.model.GoalCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PromptAwareGoalInterpreterTest {
    @Test
    fun keepsFastPathOpenAppCommandsWhenCustomPromptIsConfigured() {
        val interpreter = PromptAwareGoalInterpreter(
            settings = AiSettings.defaultGemma().copy(
                customPrompt = "When I ask to send a message, prefer WhatsApp."
            )
        )

        val goal = interpreter.interpret("open gmail")

        assertThat(goal.category).isEqualTo(GoalCategory.GENERAL_APP_CONTROL)
        assertThat(goal.targetApp).isEqualTo("gmail")
    }

    @Test
    fun prefersWhatsAppForMessagesWhenPromptRequestsIt() {
        val interpreter = PromptAwareGoalInterpreter(
            settings = AiSettings.defaultGemma().copy(
                customPrompt = "Prefer WhatsApp for messages."
            )
        )

        val goal = interpreter.interpret("message Rahul I am running late")

        assertThat(goal.category).isEqualTo(GoalCategory.DRAFT_MESSAGE)
        assertThat(goal.targetApp).isEqualTo("whatsapp")
    }

    @Test
    fun leavesExistingWhatsAppCommandsAlone() {
        val interpreter = PromptAwareGoalInterpreter(
            settings = AiSettings.defaultGemma().copy(
                customPrompt = "Prefer WhatsApp for messages."
            )
        )

        val goal = interpreter.interpret("send a WhatsApp message saying hi")

        assertThat(goal.category).isEqualTo(GoalCategory.DRAFT_MESSAGE)
        assertThat(goal.targetApp).isEqualTo("whatsapp")
    }
}
