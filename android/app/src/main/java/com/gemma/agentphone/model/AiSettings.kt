package com.gemma.agentphone.model

data class AiSettings(
    val activeProvider: String,
    val activeModel: String,
    val fallbackProvider: String,
    val fallbackModel: String,
    val autonomyMode: String,
    val allowCloudFallback: Boolean,
    val relayEndpoint: String
) {
    companion object {
        fun defaultGemma(): AiSettings {
            return AiSettings(
                activeProvider = "gemma-local",
                activeModel = "gemma-2b-instruct",
                fallbackProvider = "relay-gemma",
                fallbackModel = "gemma-9b-instruct",
                autonomyMode = "confirmed-action",
                allowCloudFallback = false,
                relayEndpoint = "http://192.168.1.2:8080"
            )
        }
    }
}
