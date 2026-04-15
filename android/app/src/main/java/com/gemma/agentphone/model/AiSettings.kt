package com.gemma.agentphone.model

data class AiSettings(
    val activeProvider: String,
    val activeModel: String,
    val fallbackProvider: String,
    val fallbackModel: String,
    val customPrompt: String,
    val autonomyMode: String,
    val allowCloudFallback: Boolean,
    val relayEndpoint: String,
    val modelDownloadUrl: String,
    val huggingFaceToken: String
) {
    companion object {
        fun defaultGemma(): AiSettings {
            return AiSettings(
                activeProvider = "gemma-local",
                activeModel = "gemma-4-e2b-it",
                fallbackProvider = "relay-gemma",
                fallbackModel = "gemma-4-9b-instruct",
                customPrompt = "",
                autonomyMode = "confirmed-action",
                allowCloudFallback = false,
                relayEndpoint = "http://192.168.1.2:8080",
                modelDownloadUrl = com.gemma.agentphone.BuildConfig.DEFAULT_MODEL_DOWNLOAD_URL,
                huggingFaceToken = ""
            )
        }
    }
}
