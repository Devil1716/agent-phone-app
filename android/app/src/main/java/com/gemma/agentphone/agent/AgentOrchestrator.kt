package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiSettings
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.UserGoal

class AgentOrchestrator(
    val settings: AiSettings,
    private val providerRegistry: AiProviderRegistry = AiProviderRegistry()
) {
    fun createPlan(goal: UserGoal): List<String> {
        val providerName = providerRegistry.getProvider(settings.activeProvider)?.displayName ?: settings.activeProvider

        return listOf(
            "Understand user goal: ${goal.text}",
            "Choose model provider: $providerName",
            "Use model: ${settings.activeModel}",
            "Build action plan",
            "Execute approved actions through accessibility",
            "Report result to user"
        )
    }
}
