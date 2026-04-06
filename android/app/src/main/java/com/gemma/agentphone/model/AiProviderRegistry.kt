package com.gemma.agentphone.model

class AiProviderRegistry {
    private val providers = listOf(
        AiProvider(
            id = "gemma-local",
            displayName = "Gemma Local",
            models = listOf("gemma-2b-instruct", "gemma-7b-instruct"),
            supportsOffline = true
        ),
        AiProvider(
            id = "qwen-local",
            displayName = "Qwen Local",
            models = listOf("qwen-1.5b-instruct", "qwen-3b-instruct"),
            supportsOffline = true
        ),
        AiProvider(
            id = "llama-local",
            displayName = "Llama Local",
            models = listOf("llama-3.2-3b-instruct"),
            supportsOffline = true
        ),
        AiProvider(
            id = "phi-local",
            displayName = "Phi Local",
            models = listOf("phi-3.5-mini-instruct"),
            supportsOffline = true
        )
    )

    fun listProviders(): List<AiProvider> = providers

    fun getProvider(providerId: String): AiProvider? {
        return providers.firstOrNull { it.id == providerId }
    }
}
