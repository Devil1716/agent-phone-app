package com.gemma.agentphone.agent

import android.content.Context
import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiProviderDescriptor
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.AiResponse
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File

class MediaPipeAiProvider(
    private val context: Context,
    override val descriptor: AiProviderDescriptor,
    private val modelFile: File
) : AiProvider {

    private var llmInference: LlmInference? = null
    private var isInitializing = false

    private fun ensureInitialized(): LlmInference? {
        if (llmInference == null && !isInitializing && modelFile.exists()) {
            synchronized(this) {
                if (llmInference == null) {
                    try {
                        isInitializing = true
                        val options = LlmInferenceOptions.builder()
                            .setModelPath(modelFile.absolutePath)
                            .setMaxTokens(512)
                            .setMaxTopK(40)
                            .build()
                        llmInference = LlmInference.createFromOptions(context, options)
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    } finally {
                        isInitializing = false
                    }
                }
            }
        }
        return llmInference
    }

    override fun infer(request: AiRequest): AiResponse {
        val startTime = System.currentTimeMillis()
        val engine = ensureInitialized()

        return if (engine != null) {
            val response = engine.generateResponse(request.prompt)
            AiResponse(
                providerId = descriptor.id,
                model = descriptor.models.first(),
                summary = response ?: "The local Gemma model did not return a response.",
                latencyMs = System.currentTimeMillis() - startTime
            )
        } else {
            AiResponse(
                providerId = descriptor.id,
                model = descriptor.models.first(),
                summary = "The local Gemma runtime is not ready yet. Download or import the model first.",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
