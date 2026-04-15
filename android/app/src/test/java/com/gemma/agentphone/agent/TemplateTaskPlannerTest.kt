package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal
import org.junit.Test

class TemplateTaskPlannerTest {
    private val planner = TemplateTaskPlanner()
    private val observation = ScreenObservation("com.gemma.agentphone", emptyList(), 0L)

    @Test
    fun wifiSettingsGoalUsesFastPath() {
        val plan = planner.plan(
            UserGoal("open Wi-Fi settings", GoalCategory.OPEN_SETTINGS, targetValue = "wifi"),
            observation
        )

        assertThat(plan.strategy).isEqualTo(ExecutionStrategy.FAST_PATH)
        assertThat(plan.steps.first().type).isEqualTo(StepType.OPEN_SYSTEM_SETTINGS)
    }

    @Test
    fun generalAppControlUsesSlowPath() {
        val plan = planner.plan(
            UserGoal(
                "open Spotify",
                GoalCategory.GENERAL_APP_CONTROL,
                targetApp = "Spotify",
                requiresFastPath = false,
                understanding = "Open Spotify and continue the requested flow there.",
                shouldOpenAppFirst = true
            ),
            observation
        )

        assertThat(plan.strategy).isEqualTo(ExecutionStrategy.SLOW_PATH)
        assertThat(plan.steps.first().type).isEqualTo(StepType.OPEN_APP)
        assertThat(plan.steps.last().type).isEqualTo(StepType.EXECUTE_AUTONOMOUSLY)
        assertThat(plan.steps.last().contextHint).contains("Spotify")
    }
}
