package com.gemma.agentphone.agent

import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal

class RuleBasedGoalInterpreter : GoalInterpreter {
    override fun interpret(input: String): UserGoal {
        val normalized = input.trim().lowercase()

        return when {
            normalized.contains("play store") || normalized.contains("google play") -> {
                UserGoal(
                    input,
                    GoalCategory.GENERAL_APP_CONTROL,
                    targetApp = "play store",
                    targetValue = extractPlayStoreQuery(input),
                    requiresFastPath = false
                )
            }

            normalized.contains("wi-fi") || normalized.contains("wifi") -> {
                UserGoal(input, GoalCategory.OPEN_SETTINGS, targetApp = "android.settings", targetValue = "wifi")
            }

            isWhatsAppMessageCommand(normalized) -> {
                UserGoal(
                    input,
                    GoalCategory.DRAFT_MESSAGE,
                    targetApp = "whatsapp",
                    targetValue = extractMessageBody(input)
                )
            }

            normalized.startsWith("message ") || normalized.startsWith("send ") -> {
                UserGoal(
                    input,
                    GoalCategory.DRAFT_MESSAGE,
                    targetApp = "sms",
                    targetValue = extractMessageBody(input)
                )
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
                UserGoal(
                    input,
                    GoalCategory.GENERAL_APP_CONTROL,
                    targetApp = extractOpenTarget(input),
                    requiresFastPath = false
                )
            }

            normalized.matches(Regex("[a-z0-9 ._-]{2,}")) && !normalized.contains(" ") -> {
                UserGoal(input, GoalCategory.GENERAL_APP_CONTROL, targetApp = input.trim(), requiresFastPath = false)
            }

            else -> UserGoal(input, GoalCategory.GENERAL_APP_CONTROL, requiresFastPath = false)
        }
    }

    private fun isWhatsAppMessageCommand(normalized: String): Boolean {
        val mentionsWhatsApp = normalized.contains("whatsapp") || normalized.contains("whats app")
        val isMessageIntent = normalized.startsWith("message ") ||
            normalized.startsWith("send ") ||
            normalized.contains("send the message") ||
            normalized.contains("send a message")

        return mentionsWhatsApp && isMessageIntent
    }

    private fun extractMessageBody(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("message ", ignoreCase = true) -> trimmed.substringAfter("message ").trim()
            trimmed.startsWith("send ", ignoreCase = true) -> trimmed.substringAfter("send ").trim()
            else -> trimmed
        }
    }

    private fun extractOpenTarget(input: String): String {
        val openTarget = input.substringAfter("open ", "").trim()
        if (openTarget.isBlank()) return input.trim()
        return openTarget
            .replace(Regex("(?i)\\b(and|then|to)\\b.*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { openTarget }
    }

    private fun extractPlayStoreQuery(input: String): String {
        val query = input
            .replace(Regex("(?i)open|download|install|from|on|using|the|google play|play store|app"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return query.ifBlank { input.trim() }
    }
}
