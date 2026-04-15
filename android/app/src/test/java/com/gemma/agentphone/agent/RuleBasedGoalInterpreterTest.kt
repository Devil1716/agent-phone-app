package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import com.gemma.agentphone.model.GoalCategory
import org.junit.Test

class RuleBasedGoalInterpreterTest {
    private val interpreter = RuleBasedGoalInterpreter()

    @Test
    fun parsesWifiSettingsCommand() {
        val goal = interpreter.interpret("open Wi-Fi settings")

        assertThat(goal.category).isEqualTo(GoalCategory.OPEN_SETTINGS)
        assertThat(goal.targetValue).isEqualTo("wifi")
        assertThat(goal.understanding).contains("Wi-Fi settings")
    }

    @Test
    fun parsesBrowserSearchCommand() {
        val goal = interpreter.interpret("search the web for Gemma Android automation")

        assertThat(goal.category).isEqualTo(GoalCategory.WEB_SEARCH)
        assertThat(goal.targetValue).contains("Gemma Android automation")
    }

    @Test
    fun parsesSingleWordAsAppTarget() {
        val goal = interpreter.interpret("calculator")

        assertThat(goal.category).isEqualTo(GoalCategory.GENERAL_APP_CONTROL)
        assertThat(goal.targetApp).isEqualTo("calculator")
        assertThat(goal.shouldOpenAppFirst).isTrue()
    }

    @Test
    fun extractsOpenAppTargetFromLongerCommand() {
        val goal = interpreter.interpret("open play store and find a notes app")

        assertThat(goal.category).isEqualTo(GoalCategory.GENERAL_APP_CONTROL)
        assertThat(goal.targetApp).isEqualTo("play store")
        assertThat(goal.shouldOpenAppFirst).isTrue()
    }

    @Test
    fun parsesWhatsAppMessageCommand() {
        val goal = interpreter.interpret("send hello from Gemma on WhatsApp")

        assertThat(goal.category).isEqualTo(GoalCategory.DRAFT_MESSAGE)
        assertThat(goal.targetApp).isEqualTo("whatsapp")
        assertThat(goal.targetValue).contains("hello from Gemma")
    }

    @Test
    fun playStoreCommandDoesNotGetClassifiedAsMedia() {
        val goal = interpreter.interpret("download Spotify from Play Store")

        assertThat(goal.category).isEqualTo(GoalCategory.GENERAL_APP_CONTROL)
    }
}
