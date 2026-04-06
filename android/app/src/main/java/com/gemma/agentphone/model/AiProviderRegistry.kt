package com.gemma.agentphone.model

class AiProviderRegistry {
    private val providers = listOf(
        StaticAiProvider(
            AiProviderDescriptor(
                id = "gemma-local",
                displayName = "Gemma Local",
                models = listOf("gemma-2b-instruct", "gemma-7b-instruct"),
                supportsOffline = true
            )
        ),
        StaticAiProvider(
            AiProviderDescriptor(
                id = "qwen-local",
                displayName = "Qwen Local",
                models = listOf("qwen-1.5b-instruct", "qwen-3b-instruct"),
                supportsOffline = true
            )
        ),
        StaticAiProvider(
            AiProviderDescriptor(
                id = "phi-local",
                displayName = "Phi Local",
                models = listOf("phi-3.5-mini-instruct"),
                supportsOffline = true
            )
        ),
        StaticAiProvider(
            AiProviderDescriptor(
                id = "llama-local",
                displayName = "Llama Local",
                models = listOf("llama-3.2-3b-instruct"),
                supportsOffline = true
            )
        ),
        StaticAiProvider(
            AiProviderDescriptor(
                id = "relay-gemma",
                displayName = "Relay Gemma",
                models = listOf("gemma-9b-instruct"),
                supportsOffline = false
            )
        ),
        StaticAiProvider(
            AiProviderDescriptor(
                id = "relay-qwen",
                displayName = "Relay Qwen",
                models = listOf("qwen-14b-instruct"),
                supportsOffline = false
            )
        )
    )

    fun listProviders(): List<AiProviderDescriptor> = providers.map { it.descriptor }

    fun getProvider(providerId: String): AiProviderDescriptor? {
        return providers.firstOrNull { it.descriptor.id == providerId }?.descriptor
    }

    fun resolve(providerId: String): AiProvider? {
        return providers.firstOrNull { it.descriptor.id == providerId }
    }
}

private class StaticAiProvider(
    override val descriptor: AiProviderDescriptor
) : AiProvider {
    override fun infer(request: AiRequest): AiResponse {
        return AiResponse(
            providerId = descriptor.id,
            model = descriptor.models.first(),
            summary = "Handled ${request.targetCategory} in ${request.mode} mode.",
            latencyMs = if (descriptor.supportsOffline) 120 else 420
        )
    }
}
