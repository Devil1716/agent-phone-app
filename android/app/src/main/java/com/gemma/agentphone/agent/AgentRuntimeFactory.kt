package com.gemma.agentphone.agent

import android.content.Context
import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiProviderDescriptor
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettings
import com.gemma.agentphone.model.HttpAiProvider

class AgentRuntimeFactory {
    fun createCoordinator(
        context: Context,
        settings: AiSettings,
        providerRegistry: AiProviderRegistry
    ): ExecutionCoordinator {
        val downloadManager = ModelDownloadManager(context)
        val modelFile = downloadManager.getModelFile()

        val primaryProvider = if (modelFile.exists()) {
            MediaPipeAiProvider(
                context = context,
                descriptor = AiProviderDescriptor(
                    id = "gemma-local",
                    displayName = "Gemma Local Runtime",
                    models = listOf(settings.activeModel),
                    supportsOffline = true
                ),
                modelFile = modelFile
            )
        } else {
            resolveConfiguredProvider(settings, providerRegistry)
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

    private fun resolveConfiguredProvider(
        settings: AiSettings,
        providerRegistry: AiProviderRegistry
    ): AiProvider? {
        if (settings.activeProvider.startsWith("relay-") && settings.relayEndpoint.isNotBlank()) {
            val descriptor = providerRegistry.getProvider(settings.activeProvider)
            if (descriptor != null) {
                return HttpAiProvider(descriptor, settings.relayEndpoint)
            }
        }

        return providerRegistry.resolve(settings.activeProvider)
            ?: providerRegistry.listProviders().firstOrNull()?.let { providerRegistry.resolve(it.id) }
    }
}
