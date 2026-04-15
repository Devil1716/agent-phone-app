package com.gemma.agentphone.agent

import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal

class RuleBasedGoalInterpreter : GoalInterpreter {
    override fun interpret(input: String): UserGoal {
        val normalized = input.trim().lowercase()

        return when {
            normalized.contains("play store") || normalized.contains("google play") -> {
                val playStoreTarget = if (normalized.startsWith("open ")) extractOpenTarget(input) else null
                UserGoal(
                    input,
                    GoalCategory.GENERAL_APP_CONTROL,
                    targetApp = playStoreTarget,
                    understanding = if (playStoreTarget == null) {
                        "This needs app-by-app control inside the Play Store."
                    } else {
                        "Open $playStoreTarget, then continue the requested flow inside that app."
                    },
                    requiresFastPath = false,
                    shouldOpenAppFirst = playStoreTarget != null
                )
            }

            normalized.contains("wi-fi") || normalized.contains("wifi") -> {
                UserGoal(
                    input,
                    GoalCategory.OPEN_SETTINGS,
                    targetApp = "android.settings",
                    targetValue = "wifi",
                    understanding = "Open Wi-Fi settings directly through Android system settings."
                )
            }

            isWhatsAppMessageCommand(normalized) -> {
                UserGoal(
                    input,
                    GoalCategory.DRAFT_MESSAGE,
                    targetApp = "whatsapp",
                    targetValue = extractMessageBody(input),
                    understanding = "Draft a WhatsApp message before asking for confirmation."
                )
            }

            normalized.startsWith("message ") || normalized.startsWith("send ") -> {
                UserGoal(
                    input,
                    GoalCategory.DRAFT_MESSAGE,
                    targetApp = "sms",
                    targetValue = extractMessageBody(input),
                    understanding = "Prepare a text message draft before asking for confirmation."
                )
            }

            normalized.startsWith("call ") -> {
                UserGoal(
                    input,
                    GoalCategory.PLACE_CALL,
                    targetApp = "dialer",
                    targetValue = input.substringAfter("call ").trim(),
                    understanding = "Prepare a phone call and stop for confirmation before placing it."
                )
            }

            normalized.contains("search the web") || normalized.contains("search for") || normalized.contains("browser") -> {
                val query = input.substringAfterLast("for", input).trim().ifBlank { input }
                UserGoal(
                    input,
                    GoalCategory.WEB_SEARCH,
                    targetApp = "browser",
                    targetValue = query,
                    understanding = "Run a web search for \"$query\"."
                )
            }

            normalized.contains("navigate") || normalized.contains("maps") -> {
                val destination = input.substringAfterLast("to", "").trim()
                UserGoal(
                    input,
                    GoalCategory.OPEN_MAPS,
                    targetApp = "maps",
                    targetValue = destination,
                    understanding = "Open maps navigation for \"$destination\"."
                )
            }

            normalized.contains("play ") || normalized.contains("youtube") -> {
                val mediaQuery = input.substringAfter("play ", input).trim()
                UserGoal(
                    input,
                    GoalCategory.PLAY_MEDIA,
                    targetApp = "youtube",
                    targetValue = mediaQuery,
                    understanding = "Search for media playback for \"$mediaQuery\"."
                )
            }

            normalized.contains("notifications") -> {
                UserGoal(
                    input,
                    GoalCategory.SUMMARIZE_NOTIFICATIONS,
                    targetApp = "system",
                    understanding = "Summarize the current notification shade."
                )
            }

            normalized.startsWith("open ") -> {
                val openTarget = extractOpenTarget(input)
                UserGoal(
                    input,
                    GoalCategory.GENERAL_APP_CONTROL,
                    targetApp = openTarget,
                    understanding = "Open $openTarget, then continue the requested flow inside that app.",
                    requiresFastPath = false,
                    shouldOpenAppFirst = true
                )
            }

            normalized.matches(Regex("[a-z0-9 ._-]{2,}")) && !normalized.contains(" ") -> {
                val targetApp = input.trim()
                UserGoal(
                    input,
                    GoalCategory.GENERAL_APP_CONTROL,
                    targetApp = targetApp,
                    understanding = "Open $targetApp and continue from the live screen state.",
                    requiresFastPath = false,
                    shouldOpenAppFirst = true
                )
            }

            else -> UserGoal(
                input,
                GoalCategory.GENERAL_APP_CONTROL,
                understanding = "Understand the request from the live screen and work through it safely.",
                requiresFastPath = false
            )
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
        val trimmed = input.substringAfter("open ", input).trim()
        val splitMarkers = listOf(" and ", " then ", " to ", ",")
        val firstMarkerIndex = splitMarkers
            .map { marker -> trimmed.lowercase().indexOf(marker) }
            .filter { it > 0 }
            .minOrNull()

        return if (firstMarkerIndex != null) {
            trimmed.substring(0, firstMarkerIndex).trim()
        } else {
            trimmed
        }
    }
}
