package com.gemma.agentphone.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gemma.agentphone.DefaultExternalActionLauncher
import com.gemma.agentphone.ExternalActionLauncher
import com.gemma.agentphone.MainActivity
import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiProviderDescriptor
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettings
import com.gemma.agentphone.model.HttpAiProvider
import java.io.File

class AgentRuntimeFactory {
    fun createCoordinator(
        context: Context,
        settings: AiSettings,
        providerRegistry: AiProviderRegistry
    ): ExecutionCoordinator {
        val downloadManager = ModelDownloadManager(context)
        val modelFile = downloadManager.getModelFile().takeIf { it.exists() }
        val primaryProvider = resolveConfiguredProvider(
            context = context,
            providerId = settings.activeProvider,
            model = settings.activeModel,
            relayEndpoint = settings.relayEndpoint,
            modelFile = modelFile,
            providerRegistry = providerRegistry
        )
        val fallbackProvider = resolveConfiguredProvider(
            context = context,
            providerId = settings.fallbackProvider,
            model = settings.fallbackModel,
            relayEndpoint = settings.relayEndpoint,
            modelFile = modelFile,
            providerRegistry = providerRegistry
        )
        val orchestrator = AgentOrchestrator(
            settings = settings,
            primaryProvider = primaryProvider,
            fallbackProvider = fallbackProvider
        )

        return ExecutionCoordinator(
            goalInterpreter = PromptAwareGoalInterpreter(
                settings = settings,
                fallback = LlmGoalInterpreter(aiProvider = orchestrator)
            ),
            taskPlanner = TemplateTaskPlanner(),
            policyEngine = DefaultPolicyEngine(),
            observationService = DefaultObservationService(),
            executors = listOf(
                AppLaunchExecutor(AndroidInstalledAppResolver(context)),
                IntentExecutor(),
                BrowserExecutor(),
                AccessibilityExecutor(
                    aiProvider = orchestrator,
                    customPrompt = settings.customPrompt,
                    autonomyMode = settings.autonomyMode
                )
            ),
            externalActionDispatcher = createExternalActionDispatcher(context)
        )
    }

    private fun resolveConfiguredProvider(
        context: Context,
        providerId: String,
        model: String,
        relayEndpoint: String,
        modelFile: File?,
        providerRegistry: AiProviderRegistry
    ): AiProvider? {
        if (providerId == "gemma-local") {
            return modelFile?.let {
                MediaPipeAiProvider(
                    context = context,
                    descriptor = AiProviderDescriptor(
                        id = providerId,
                        displayName = "Gemma Local Runtime",
                        models = listOf(model),
                        supportsOffline = true
                    ),
                    modelFile = it
                )
            }
        }

        if (providerId.startsWith("relay-") && relayEndpoint.isNotBlank()) {
            val descriptor = providerRegistry.getProvider(providerId)
            if (descriptor != null) {
                return HttpAiProvider(
                    descriptor = descriptor.copy(models = listOf(model)),
                    baseUrl = relayEndpoint
                )
            }
        }

        return providerRegistry.resolve(providerId)
    }

    private fun createExternalActionDispatcher(context: Context): (ExternalActionRequest) -> Unit {
        val applicationContext = context.applicationContext
        val launcher = if (context is Activity) {
            ExternalActionLauncher { activity, spec ->
                if (activity is MainActivity) {
                    MainActivity.externalActionLauncher.launch(activity, spec)
                } else {
                    DefaultExternalActionLauncher.launch(activity, spec)
                }
            }
        } else {
            null
        }

        return externalActionDispatcher@{ action ->
            val spec = action.spec
            if (launcher != null && context is Activity) {
                launcher.launch(context, spec)
                return@externalActionDispatcher
            }

            val intent = if (spec.packageName != null && spec.action == Intent.ACTION_MAIN && spec.data == null) {
                applicationContext.packageManager.getLaunchIntentForPackage(spec.packageName)
            } else {
                Intent(spec.action).apply {
                    spec.data?.let { data = Uri.parse(it) }
                    spec.packageName?.let(::setPackage)
                }
            }?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent != null) {
                runCatching { applicationContext.startActivity(intent) }
            }
        }
    }
}
