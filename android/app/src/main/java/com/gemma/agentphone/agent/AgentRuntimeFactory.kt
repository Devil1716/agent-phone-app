package com.gemma.agentphone.agent

import android.content.Context
import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiProviderDescriptor
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.AiResponse
import com.gemma.agentphone.model.AiSettings
import com.gemma.agentphone.model.HttpAiProvider
import java.io.File

class AgentRuntimeFactory {
    fun createCoordinator(
        context: Context,
        settings: AiSettings,
        providerRegistry: AiProviderRegistry
    ): ExecutionCoordinator {
        val modelFile = ModelDownloadManager(context).getModelFile()
        val activeProvider = resolveProvider(
            providerId = settings.activeProvider,
            modelId = settings.activeModel,
            settings = settings,
            providerRegistry = providerRegistry,
            context = context,
            modelFile = modelFile
        )
        val fallbackProvider = if (settings.allowCloudFallback) {
            resolveProvider(
                providerId = settings.fallbackProvider,
                modelId = settings.fallbackModel,
                settings = settings,
                providerRegistry = providerRegistry,
                context = context,
                modelFile = modelFile
            )
        } else {
            null
        }
        val primaryProvider = when {
            activeProvider != null && fallbackProvider != null ->
                ResilientAiProvider(activeProvider, fallbackProvider)
            activeProvider != null -> activeProvider
            fallbackProvider != null -> fallbackProvider
            else -> null
        }

        return ExecutionCoordinator(
            goalInterpreter = PromptAwareGoalInterpreter(settings),
            taskPlanner = TemplateTaskPlanner(),
            policyEngine = DefaultPolicyEngine(),
            observationService = DefaultObservationService(),
            executors = listOf(
                AppLaunchExecutor(AndroidInstalledAppResolver(context)),
                IntentExecutor(),
                BrowserExecutor(),
                AccessibilityExecutor(
                    aiProvider = primaryProvider,
                    customPrompt = settings.customPrompt,
                    autonomyMode = settings.autonomyMode
                )
            )
        )
    }

    private fun resolveProvider(
        providerId: String,
        modelId: String,
        settings: AiSettings,
        providerRegistry: AiProviderRegistry,
        context: Context,
        modelFile: File
    ): AiProvider? {
        return when {
            providerId == "gemma-local" && modelFile.exists() -> {
                MediaPipeAiProvider(
                    context = context,
                    descriptor = AiProviderDescriptor(
                        id = "gemma-local",
                        displayName = "Gemma Local Runtime",
                        models = listOf(modelId),
                        supportsOffline = true
                    ),
                    modelFile = modelFile
                )
            }

            providerId.startsWith("relay-") && settings.relayEndpoint.isNotBlank() -> {
                val descriptor = providerRegistry.getProvider(providerId)
                descriptor?.let { HttpAiProvider(it, settings.relayEndpoint) }
            }

            else -> providerRegistry.resolve(providerId)
        }
    }
}

internal class ResilientAiProvider(
    private val primary: AiProvider,
    private val fallback: AiProvider
) : AiProvider {
    override val descriptor: AiProviderDescriptor = primary.descriptor

    override fun infer(request: AiRequest): AiResponse {
        val primaryResponse = primary.infer(request)
        val isPrimaryUnavailable = primaryResponse.summary.contains("not ready", ignoreCase = true) ||
            primaryResponse.summary.contains("did not return a response", ignoreCase = true) ||
            primaryResponse.summary.contains("error", ignoreCase = true)

        if (!isPrimaryUnavailable) {
            return primaryResponse
        }

        val fallbackResponse = fallback.infer(request)
        return fallbackResponse.copy(
            summary = "${fallbackResponse.summary}\n(fallback used after local runtime was unavailable)"
        )
    }
}
