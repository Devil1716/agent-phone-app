package com.gemma.agentphone.agent

import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal

class RuleBasedGoalInterpreter : GoalInterpreter {
    override fun interpret(input: String): UserGoal {
        val normalized = input.trim().lowercase()

        return when {
            normalized.contains("wi-fi") || normalized.contains("wifi") -> {
                UserGoal(input, GoalCategory.OPEN_SETTINGS, targetApp = "android.settings", targetValue = "wifi")
            }

            normalized.startsWith("message ") -> {
                UserGoal(input, GoalCategory.DRAFT_MESSAGE, targetApp = "sms", targetValue = input.substringAfter("message ").trim())
            }

            normalized.startsWith("call ") -> {
                UserGoal(input, GoalCategory.PLACE_CALL, targetApp = "dialer", targetValue = input.substringAfter("call ").trim())
            }

            normalized.contains("search the web") || normalized.contains("search for") || normalized.contains("browser") -> {
                val query = input.substringAfterLast("for", input).trim().ifBlank { input }
                UserGoal(input, GoalCategory.WEB_SEARCH, targetApp = "browser", targetValue = query)
            }

            normalized.contains("navigate") || normalized.contains("maps") -> {
                UserGoal(input, GoalCategory.OPEN_MAPS, targetApp = "maps", targetValue = input.substringAfterLast("to", "").trim())
            }

            normalized.contains("play ") || normalized.contains("youtube") -> {
                UserGoal(input, GoalCategory.PLAY_MEDIA, targetApp = "youtube", targetValue = input.substringAfter("play ", input).trim())
            }

            normalized.contains("notifications") -> {
                UserGoal(input, GoalCategory.SUMMARIZE_NOTIFICATIONS, targetApp = "system")
            }

            normalized.startsWith("open ") -> {
                UserGoal(input, GoalCategory.GENERAL_APP_CONTROL, targetApp = input.substringAfter("open ").trim(), requiresFastPath = false)
            }

            else -> UserGoal(input, GoalCategory.GENERAL_APP_CONTROL, requiresFastPath = false)
        }
    }
}
