package com.gemma.agentphone.model

import android.content.Context
import com.gemma.agentphone.agent.TextGenerationEngine
import com.gemma.agentphone.diagnostics.AppLogger
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class GemmaInferenceEngine(
    private val context: Context,
    private val modelManager: GemmaModelManager
) : TextGenerationEngine {
    companion object {
        private const val TAG = "GemmaInferenceEngine"
    }

    private val inferenceMutex = Mutex()
    private var inference: LlmInference? = null

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        var lastFailure: Throwable? = null
        repeat(3) { attempt ->
            try {
                val startNs = System.nanoTime()
                val llm = getOrCreateInference()
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.05f)
                    .setTopK(40)
                    .build()
                val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
                try {
                    session.addQueryChunk(prompt)
                    val response = session.generateResponse().trim()
                    validateStructuredResponse(prompt, response)
                    val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
                    AppLogger.i(
                        context,
                        TAG,
                        "Gemma 4 inference succeeded on attempt ${attempt + 1} in ${latencyMs}ms."
                    )
                    return@withContext response
                } finally {
                    session.close()
                }
            } catch (throwable: Throwable) {
                lastFailure = throwable
                AppLogger.w(
                    context,
                    TAG,
                    "Gemma 4 inference attempt ${attempt + 1} failed. Resetting the runtime.",
                    throwable
                )
                reset()
                runCatching { System.gc() }
            }
        }
        throw IllegalStateException(lastFailure?.localizedMessage ?: "Gemma 4 inference failed after retries.")
    }

    private suspend fun getOrCreateInference(): LlmInference {
        return inferenceMutex.withLock {
            inference?.let { return@withLock it }
            if (!modelManager.isModelReady()) {
                throw IllegalStateException("Gemma 4 model is not ready yet.")
            }
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelManager.getModelPath())
                .setMaxTokens(1536)
                .setMaxTopK(64)
                .build()
            LlmInference.createFromOptions(context, options).also {
                AppLogger.i(context, TAG, "Gemma 4 runtime loaded from ${modelManager.getModelPath()}.")
                inference = it
            }
        }
    }

    private suspend fun reset() {
        inferenceMutex.withLock {
            runCatching { inference?.close() }
            inference = null
        }
    }

    private fun validateStructuredResponse(prompt: String, response: String) {
        require(response.isNotBlank()) { "Gemma 4 returned an empty response." }
        val expectsJson = prompt.contains("ONLY a JSON array", ignoreCase = true) ||
            prompt.contains("ONLY with JSON", ignoreCase = true) ||
            prompt.contains("Reply ONLY with JSON", ignoreCase = true)
        if (!expectsJson) {
            return
        }

        val looksLikeArray = response.contains('[') && response.contains(']')
        val looksLikeObject = response.contains('{') && response.contains('}')
        require(looksLikeArray || looksLikeObject) {
            "Gemma 4 returned a truncated structured response."
        }
    }
}
