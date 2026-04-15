package com.gemma.agentphone.agent

import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal

class TemplateTaskPlanner : TaskPlanner {
    override fun plan(goal: UserGoal, observation: ScreenObservation): TaskPlan {
        val steps = when (goal.category) {
            GoalCategory.OPEN_SETTINGS -> listOf(
                TaskStep(
                    "open_settings",
                    StepType.OPEN_SYSTEM_SETTINGS,
                    "Open Wi-Fi settings",
                    payload = goal.targetValue,
                    contextHint = goal.understanding
                )
            )

            GoalCategory.DRAFT_MESSAGE -> listOf(
                TaskStep(
                    "draft_message",
                    StepType.DRAFT_MESSAGE,
                    "Open ${goal.targetApp ?: "message"} draft composer",
                    targetApp = goal.targetApp,
                    payload = goal.targetValue,
                    contextHint = goal.understanding
                ),
                TaskStep(
                    "confirm_send",
                    StepType.REQUEST_CONFIRMATION,
                    "Confirm before sending message",
                    targetApp = goal.targetApp,
                    payload = goal.targetValue,
                    contextHint = goal.understanding,
                    riskLevel = RiskLevel.CONFIRM_REQUIRED
                )
            )

            GoalCategory.PLACE_CALL -> listOf(
                TaskStep(
                    "confirm_call",
                    StepType.REQUEST_CONFIRMATION,
                    "Confirm before placing call to ${goal.targetValue}",
                    contextHint = goal.understanding,
                    riskLevel = RiskLevel.CONFIRM_REQUIRED
                )
            )

            GoalCategory.WEB_SEARCH -> listOf(
                TaskStep(
                    "browser_search",
                    StepType.OPEN_BROWSER_SEARCH,
                    "Search the web for ${goal.targetValue}",
                    payload = goal.targetValue,
                    contextHint = goal.understanding
                )
            )

            GoalCategory.OPEN_MAPS -> listOf(
                TaskStep(
                    "open_maps",
                    StepType.OPEN_MAPS,
                    "Open maps for ${goal.targetValue.orEmpty()}",
                    payload = goal.targetValue,
                    contextHint = goal.understanding
                )
            )

            GoalCategory.PLAY_MEDIA -> listOf(
                TaskStep(
                    "play_media",
                    StepType.OPEN_MEDIA_SEARCH,
                    "Open media search for ${goal.targetValue}",
                    payload = goal.targetValue,
                    contextHint = goal.understanding
                )
            )

            GoalCategory.SUMMARIZE_NOTIFICATIONS -> listOf(
                TaskStep(
                    "summarize_notifications",
                    StepType.SUMMARIZE_NOTIFICATIONS,
                    "Summarize current notifications",
                    contextHint = goal.understanding
                )
            )

            GoalCategory.GENERAL_APP_CONTROL -> buildList {
                    goal.targetApp
                        ?.takeIf { shouldOpenApp(goal, observation) }
                        ?.let { targetApp ->
                            add(
                                TaskStep(
                                    id = "open_target_app",
                                    type = StepType.OPEN_APP,
                                    description = "Open $targetApp before continuing the requested flow",
                                    targetApp = targetApp,
                                    payload = targetApp,
                                    contextHint = goal.understanding
                                )
                            )
                        }
                    add(
                        TaskStep(
                            "autonomous_control",
                            StepType.EXECUTE_AUTONOMOUSLY,
                            "Take autonomous control to achieve: ${goal.text}",
                            payload = goal.text,
                            contextHint = goal.understanding
                        )
                    )
                }

            GoalCategory.UNSUPPORTED -> listOf(
                TaskStep(
                    "unsupported",
                    StepType.UNSUPPORTED,
                    "No safe automation plan available",
                    contextHint = goal.understanding,
                    riskLevel = RiskLevel.BLOCKED
                )
            )
        }

        return TaskPlan(goal.category.defaultStrategy(), steps)
    }

    private fun shouldOpenApp(goal: UserGoal, observation: ScreenObservation): Boolean {
        if (goal.shouldOpenAppFirst && !goal.targetApp.isNullOrBlank()) {
            val normalizedForeground = observation.foregroundApp.lowercase()
            return !normalizedForeground.contains(goal.targetApp.lowercase())
        }

        val targetApp = goal.targetApp?.trim().orEmpty()
        if (targetApp.isBlank()) {
            return false
        }

        val normalizedForeground = observation.foregroundApp.lowercase()
        return !normalizedForeground.contains(targetApp.lowercase())
    }
}
