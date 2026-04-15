package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiProviderDescriptor
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.AiResponse
import com.gemma.agentphone.model.AiSettings

class AgentOrchestrator(
    private val settings: AiSettings,
    private val primaryProvider: AiProvider?,
    private val fallbackProvider: AiProvider?
) : AiProvider {

    override val descriptor: AiProviderDescriptor = primaryProvider?.descriptor
        ?: fallbackProvider?.descriptor
        ?: AiProviderDescriptor(
            id = "agent-orchestrator",
            displayName = "Agent Orchestrator",
            models = listOf(settings.activeModel),
            supportsOffline = false
        )

    override fun infer(request: AiRequest): AiResponse {
        val candidates = buildCandidates(request)
        val failures = mutableListOf<AiResponse>()

        for (provider in candidates) {
            val response = runCatching { provider.infer(request) }
                .getOrElse { throwable ->
                    AiResponse(
                        providerId = provider.descriptor.id,
                        model = provider.descriptor.models.firstOrNull().orEmpty(),
                        summary = throwable.localizedMessage ?: "Provider execution failed.",
                        latencyMs = 0L
                    )
                }

            if (response.isUsable()) {
                return response
            }
            failures += response
        }

        return failures.lastOrNull() ?: AiResponse(
            providerId = descriptor.id,
            model = descriptor.models.firstOrNull().orEmpty(),
            summary = "No AI provider is currently ready. Download a compatible Gemma model or configure a relay fallback.",
            latencyMs = 0L
        )
    }

    fun summaryLines(): List<String> {
        return listOf(
            "Execution mode: fast path for common app-control flows, slow path for recovery.",
            "Primary provider: ${primaryProvider?.descriptor?.displayName ?: settings.activeProvider}",
            "Primary model: ${settings.activeModel}",
            "Fallback provider: ${fallbackProvider?.descriptor?.displayName ?: settings.fallbackProvider}",
            "Fallback model: ${settings.fallbackModel}",
            "Custom prompt: ${if (settings.customPrompt.isBlank()) "not set" else "configured"}",
            "Relay endpoint: ${settings.relayEndpoint}",
            "Autonomy mode: ${settings.autonomyMode}",
            "Cloud fallback enabled: ${settings.allowCloudFallback}"
        )
    }

    private fun buildCandidates(request: AiRequest): List<AiProvider> {
        val ordered = linkedSetOf<AiProvider>()
        primaryProvider?.let(ordered::add)

        val shouldTryFallback = fallbackProvider != null && (
            primaryProvider == null ||
                settings.allowCloudFallback ||
                request.mode == "orchestrator"
            )
        if (shouldTryFallback) {
            fallbackProvider?.let(ordered::add)
        }

        return ordered.toList()
    }

    private fun AiResponse.isUsable(): Boolean {
        val normalized = summary.lowercase()
        return summary.isNotBlank() &&
            !normalized.contains("not ready") &&
            !normalized.contains("download or import") &&
            !normalized.startsWith("error:") &&
            !normalized.contains("provider execution failed")
    }
}
