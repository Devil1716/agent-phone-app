package com.gemma.agentphone.model

import android.content.Context

class AiSettingsRepository(
    context: Context
) {
    private val preferences = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    fun load(): AiSettings {
        val defaults = AiSettings.defaultGemma()
        return AiSettings(
            activeProvider = preferences.getString("activeProvider", defaults.activeProvider) ?: defaults.activeProvider,
            activeModel = preferences.getString("activeModel", defaults.activeModel) ?: defaults.activeModel,
            fallbackProvider = preferences.getString("fallbackProvider", defaults.fallbackProvider) ?: defaults.fallbackProvider,
            fallbackModel = preferences.getString("fallbackModel", defaults.fallbackModel) ?: defaults.fallbackModel,
            autonomyMode = preferences.getString("autonomyMode", defaults.autonomyMode) ?: defaults.autonomyMode,
            allowCloudFallback = preferences.getBoolean("allowCloudFallback", defaults.allowCloudFallback)
        )
    }

    fun save(settings: AiSettings) {
        preferences.edit()
            .putString("activeProvider", settings.activeProvider)
            .putString("activeModel", settings.activeModel)
            .putString("fallbackProvider", settings.fallbackProvider)
            .putString("fallbackModel", settings.fallbackModel)
            .putString("autonomyMode", settings.autonomyMode)
            .putBoolean("allowCloudFallback", settings.allowCloudFallback)
            .apply()
    }
}
