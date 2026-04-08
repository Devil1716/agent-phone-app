package com.gemma.agentphone.agent
 
import com.gemma.agentphone.model.AiProviderDescriptor

class AgentRuntimeFactory {
    fun createCoordinator(context: android.content.Context, settings: com.gemma.agentphone.model.AiSettings, providerRegistry: com.gemma.agentphone.model.AiProviderRegistry): ExecutionCoordinator {
        val downloadManager = ModelDownloadManager(context)
        val modelFile = downloadManager.getModelFile()
        
        val primaryProvider = if (modelFile.exists()) {
             MediaPipeAiProvider(
                context = context,
                descriptor = AiProviderDescriptor(
                    id = "gemma-local",
                    displayName = "Gemma 4 (Real)",
                    models = listOf("gemma-2b-it-cpu-int4"),
                    supportsOffline = true
                ),
                modelFile = modelFile
            )
        } else {
            providerRegistry.resolve(settings.activeProvider) ?: providerRegistry.listProviders().firstOrNull()?.let { providerRegistry.resolve(it.id) }
        }
        
        return ExecutionCoordinator(
            goalInterpreter = RuleBasedGoalInterpreter(),
            taskPlanner = TemplateTaskPlanner(),
            policyEngine = DefaultPolicyEngine(),
            observationService = DefaultObservationService(),
            executors = listOf(
                IntentExecutor(),
                BrowserExecutor(),
                AccessibilityExecutor(primaryProvider)
            )
        )
    }
}
