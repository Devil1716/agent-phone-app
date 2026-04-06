package com.gemma.agentphone.model

data class AiProvider(
    val id: String,
    val displayName: String,
    val models: List<String>,
    val supportsOffline: Boolean
)
