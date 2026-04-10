package com.gemma.agentphone.model

class AiSettingsStore(
    private val keyValueStore: KeyValueStore
) {
    fun load(): AiSettings {
        val defaults = AiSettings.defaultGemma()
        return AiSettings(
            activeProvider = keyValueStore.getString("activeProvider", defaults.activeProvider),
            activeModel = keyValueStore.getString("activeModel", defaults.activeModel),
            fallbackProvider = keyValueStore.getString("fallbackProvider", defaults.fallbackProvider),
            fallbackModel = keyValueStore.getString("fallbackModel", defaults.fallbackModel),
            customPrompt = keyValueStore.getString("customPrompt", defaults.customPrompt),
            autonomyMode = keyValueStore.getString("autonomyMode", defaults.autonomyMode),
            allowCloudFallback = keyValueStore.getBoolean("allowCloudFallback", defaults.allowCloudFallback),
            relayEndpoint = keyValueStore.getString("relayEndpoint", defaults.relayEndpoint),
            modelDownloadUrl = keyValueStore.getString("modelDownloadUrl", defaults.modelDownloadUrl),
            huggingFaceToken = keyValueStore.getString("huggingFaceToken", defaults.huggingFaceToken)
        )
    }

    fun save(settings: AiSettings) {
        keyValueStore.putString("activeProvider", settings.activeProvider)
        keyValueStore.putString("activeModel", settings.activeModel)
        keyValueStore.putString("fallbackProvider", settings.fallbackProvider)
        keyValueStore.putString("fallbackModel", settings.fallbackModel)
        keyValueStore.putString("customPrompt", settings.customPrompt)
        keyValueStore.putString("autonomyMode", settings.autonomyMode)
        keyValueStore.putBoolean("allowCloudFallback", settings.allowCloudFallback)
        keyValueStore.putString("relayEndpoint", settings.relayEndpoint)
        keyValueStore.putString("modelDownloadUrl", settings.modelDownloadUrl)
        keyValueStore.putString("huggingFaceToken", settings.huggingFaceToken)
    }
}
