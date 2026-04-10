package com.gemma.agentphone.model

import android.content.Context

class AiSettingsRepository(
    context: Context
) {
    private val store = AiSettingsStore(
        SharedPreferencesKeyValueStore(
            context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        )
    )

    fun load(): AiSettings {
        return store.load()
    }

    fun save(settings: AiSettings) {
        store.save(settings)
    }
}
