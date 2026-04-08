package com.gemma.agentphone.agent

import android.content.Context
import com.gemma.agentphone.model.*
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
                            .setTopK(40)
                            .setTemperature(0.8f)
                            .setRandomSeed(42)
                            .build()
                        llmInference = LlmInference.createFromOptions(context, options)
                    } catch (e: Exception) {
                        e.printStackTrace()
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
                summary = response ?: "Gemma 4 failed to generate a response.",
                latencyMs = System.currentTimeMillis() - startTime
            )
        } else {
            AiResponse(
                providerId = descriptor.id,
                model = descriptor.models.first(),
                summary = "Error: Gemma 4 AI engine is not ready. Is the model downloaded?",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
