package com.gemma.agentphone.agent

import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal

class TemplateTaskPlanner : TaskPlanner {
    override fun plan(goal: UserGoal, observation: ScreenObservation): TaskPlan {
        val steps = when (goal.category) {
            GoalCategory.OPEN_SETTINGS -> listOf(
                TaskStep("open_settings", StepType.OPEN_SYSTEM_SETTINGS, "Open Wi-Fi settings", payload = goal.targetValue)
            )

            GoalCategory.DRAFT_MESSAGE -> listOf(
                TaskStep("draft_message", StepType.DRAFT_MESSAGE, "Open draft message composer", payload = goal.targetValue),
                TaskStep("confirm_send", StepType.REQUEST_CONFIRMATION, "Confirm before sending message", riskLevel = RiskLevel.CONFIRM_REQUIRED)
            )

            GoalCategory.PLACE_CALL -> listOf(
                TaskStep("confirm_call", StepType.REQUEST_CONFIRMATION, "Confirm before placing call to ${goal.targetValue}", riskLevel = RiskLevel.CONFIRM_REQUIRED)
            )

            GoalCategory.WEB_SEARCH -> listOf(
                TaskStep("browser_search", StepType.OPEN_BROWSER_SEARCH, "Search the web for ${goal.targetValue}", payload = goal.targetValue)
            )

            GoalCategory.OPEN_MAPS -> listOf(
                TaskStep("open_maps", StepType.OPEN_MAPS, "Open maps for ${goal.targetValue.orEmpty()}", payload = goal.targetValue)
            )

            GoalCategory.PLAY_MEDIA -> listOf(
                TaskStep("play_media", StepType.OPEN_MEDIA_SEARCH, "Open media search for ${goal.targetValue}", payload = goal.targetValue)
            )

            GoalCategory.SUMMARIZE_NOTIFICATIONS -> listOf(
                TaskStep("summarize_notifications", StepType.SUMMARIZE_NOTIFICATIONS, "Summarize current notifications")
            )

            GoalCategory.GENERAL_APP_CONTROL -> listOf(
                TaskStep("autonomous_control", StepType.EXECUTE_AUTONOMOUSLY, "Execute custom command autonomously via local Gemma", payload = goal.text)
            )

            GoalCategory.UNSUPPORTED -> listOf(
                TaskStep("unsupported", StepType.UNSUPPORTED, "No safe automation plan available", riskLevel = RiskLevel.BLOCKED)
            )
        }

        return TaskPlan(goal.category.defaultStrategy(), steps)
    }
}
