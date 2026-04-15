package com.gemma.agentphone.model

data class UserGoal(
    val text: String,
    val category: GoalCategory,
    val targetApp: String? = null,
    val targetValue: String? = null,
    val requiresFastPath: Boolean = true,
    val understanding: String? = null,
    val shouldOpenAppFirst: Boolean = false
)

enum class GoalCategory {
    OPEN_SETTINGS,
    DRAFT_MESSAGE,
    PLACE_CALL,
    WEB_SEARCH,
    OPEN_MAPS,
    PLAY_MEDIA,
    SUMMARIZE_NOTIFICATIONS,
    GENERAL_APP_CONTROL,
    UNSUPPORTED
}
