package com.gemma.agentphone.model

data class AiSettings(
    val activeProvider: String,
    val activeModel: String,
    val fallbackProvider: String,
    val fallbackModel: String,
    val autonomyMode: String,
    val allowCloudFallback: Boolean
) {
    companion object {
        fun defaultGemma(): AiSettings {
            return AiSettings(
                activeProvider = "gemma-local",
                activeModel = "gemma-2b-instruct",
                fallbackProvider = "qwen-local",
                fallbackModel = "qwen-3b-instruct",
                autonomyMode = "confirmed-action",
                allowCloudFallback = false
            )
        }
    }
}
