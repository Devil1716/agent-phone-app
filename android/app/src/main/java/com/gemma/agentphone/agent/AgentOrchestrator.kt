package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiSettings
import com.gemma.agentphone.model.AiProviderRegistry

class AgentOrchestrator(
    val settings: AiSettings,
    private val providerRegistry: AiProviderRegistry = AiProviderRegistry()
) {
    fun summaryLines(): List<String> {
        val providerName = providerRegistry.getProvider(settings.activeProvider)?.displayName ?: settings.activeProvider

        return listOf(
            "Execution mode: fast path for common app-control flows, slow path for recovery.",
            "Primary provider: $providerName",
            "Primary model: ${settings.activeModel}",
            "Fallback provider: ${settings.fallbackProvider}",
            "Fallback model: ${settings.fallbackModel}",
            "Custom prompt: ${if (settings.customPrompt.isBlank()) "not set" else "configured"}",
            "Relay endpoint: ${settings.relayEndpoint}",
            "Autonomy mode: ${settings.autonomyMode}",
            "Cloud fallback enabled: ${settings.allowCloudFallback}"
        )
    }
}
