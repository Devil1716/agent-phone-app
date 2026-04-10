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
        assertThat(goal.targetApp).isEqualTo("play store")
        assertThat(goal.targetValue).contains("Spotify")
    }

    @Test
    fun openCommandExtractsOnlyPrimaryAppTarget() {
        val goal = interpreter.interpret("open playstore and download subway surfers")

        assertThat(goal.category).isEqualTo(GoalCategory.GENERAL_APP_CONTROL)
        assertThat(goal.targetApp).contains("playstore")
    }
}
