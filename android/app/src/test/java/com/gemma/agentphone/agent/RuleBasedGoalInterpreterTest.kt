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
}
