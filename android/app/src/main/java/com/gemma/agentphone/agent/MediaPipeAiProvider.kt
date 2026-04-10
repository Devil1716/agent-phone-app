package com.gemma.agentphone.agent

import android.content.Context
import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiProviderDescriptor
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.AiResponse
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MediaPipeAiProvider(
    private val context: Context,
    override val descriptor: AiProviderDescriptor,
    private val modelFile: File
) : AiProvider {

    companion object {
        private data class CachedEngine(
            val inference: LlmInference,
            val maxTokens: Int,
            val topK: Int
        )

        private val engineCache = ConcurrentHashMap<String, CachedEngine>()
        private val initializationLocks = ConcurrentHashMap<String, Any>()

        fun prewarm(context: Context, modelFile: File) {
            if (!modelFile.exists()) {
                return
            }
            getOrCreateEngine(context.applicationContext, modelFile, maxTokens = 160, topK = 24)
        }

        private fun getOrCreateEngine(
            context: Context,
            modelFile: File,
            maxTokens: Int,
            topK: Int
        ): CachedEngine? {
            val cacheKey = modelFile.absolutePath
            val cached = engineCache[cacheKey]
            if (cached != null && cached.maxTokens <= maxTokens && cached.topK <= topK) {
                return cached
            }

            val lock = initializationLocks.getOrPut(cacheKey) { Any() }
            synchronized(lock) {
                val refreshed = engineCache[cacheKey]
                if (refreshed != null && refreshed.maxTokens <= maxTokens && refreshed.topK <= topK) {
                    return refreshed
                }

                return try {
                    val options = LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(maxTokens)
                        .setMaxTopK(topK)
                        .build()
                    CachedEngine(
                        inference = LlmInference.createFromOptions(context, options),
                        maxTokens = maxTokens,
                        topK = topK
                    ).also { engineCache[cacheKey] = it }
                } catch (throwable: Throwable) {
                    throwable.printStackTrace()
                    null
                }
            }
        }
    }

    private fun runInferenceSafely(engine: LlmInference, prompt: String): String? {
        return try {
            engine.generateResponse(prompt)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            null
        }
    }

    private fun ensureInitialized(request: AiRequest): LlmInference? {
        if (!modelFile.exists()) {
            return null
        }

        val maxTokens = when (request.mode) {
            "autonomous" -> 160
            else -> 256
        }
        val topK = when (request.mode) {
            "autonomous" -> 24
            else -> 32
        }

        return getOrCreateEngine(
            context = context.applicationContext,
            modelFile = modelFile,
            maxTokens = maxTokens,
            topK = topK
        )?.inference
    }

    override fun infer(request: AiRequest): AiResponse {
        val startTime = System.currentTimeMillis()
        val engine = ensureInitialized(request)

        return if (engine != null) {
            val response = runInferenceSafely(engine, request.prompt)
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
