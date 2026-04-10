package com.gemma.agentphone.model

data class AiProviderDescriptor(
    val id: String,
    val displayName: String,
    val models: List<String>,
    val supportsOffline: Boolean
)

data class AiRequest(
    val prompt: String,
    val mode: String,
    val targetCategory: GoalCategory
)

data class AiResponse(
    val providerId: String,
    val model: String,
    val summary: String,
    val latencyMs: Long
)

interface AiProvider {
    val descriptor: AiProviderDescriptor
    fun infer(request: AiRequest): AiResponse
}
