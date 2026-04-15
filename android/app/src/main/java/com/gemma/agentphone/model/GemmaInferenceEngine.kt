package com.gemma.agentphone.model

import android.content.Context
import com.gemma.agentphone.agent.TextGenerationEngine
import com.gemma.agentphone.diagnostics.AppLogger
import com.google.common.util.concurrent.MoreExecutors
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GemmaInferenceEngine(
    private val context: Context,
    private val modelManager: GemmaModelManager
) : TextGenerationEngine {
    companion object {
        private const val TAG = "GemmaInferenceEngine"
        private const val INVALID_MODEL_MESSAGE =
            "The saved Gemma model bundle is invalid. Import or download a valid MediaPipe task file and try again."
    }

    private val inferenceMutex = Mutex()
    private var inference: LlmInference? = null
    private var activeBackend: LlmInference.Backend = LlmInference.Backend.DEFAULT

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        var lastFailure: Throwable? = null
        repeat(3) { attempt ->
            try {
                val startNs = System.nanoTime()
                val llm = getOrCreateInference()
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.05f)
                    .setTopK(40)
                    .setTopP(0.9f)
                    .build()
                val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
                try {
                    session.addQueryChunk(prompt)
                    val response = session.generateResponseAsync().await().trim()
                    validateStructuredResponse(prompt, response)
                    val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
                    AppLogger.i(
                        context,
                        TAG,
                        "Gemma inference succeeded on attempt ${attempt + 1} in ${latencyMs}ms using $activeBackend."
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
                    "Gemma inference attempt ${attempt + 1} failed. Resetting the runtime.",
                    throwable
                )
                if (throwable.isCorruptModelError()) {
                    modelManager.invalidateModel(INVALID_MODEL_MESSAGE)
                    throw IllegalStateException(INVALID_MODEL_MESSAGE, throwable)
                }
                reset()
                runCatching { System.gc() }
            }
        }
        throw IllegalStateException(lastFailure?.localizedMessage ?: "Gemma inference failed after retries.")
    }

    private suspend fun getOrCreateInference(): LlmInference {
        return inferenceMutex.withLock {
            inference?.let { return@withLock it }
            if (!modelManager.isModelReady()) {
                throw IllegalStateException("Gemma model is not ready yet.")
            }

            val attempts = listOf(
                LlmInference.Backend.GPU,
                LlmInference.Backend.DEFAULT,
                LlmInference.Backend.CPU
            ).distinct()
            var lastFailure: Throwable? = null

            for (backend in attempts) {
                try {
                    val options = LlmInferenceOptions.builder()
                        .setModelPath(modelManager.getModelPath())
                        .setMaxTokens(1536)
                        .setMaxTopK(64)
                        .setPreferredBackend(backend)
                        .build()
                    return@withLock LlmInference.createFromOptions(context, options).also {
                        activeBackend = backend
                        inference = it
                        AppLogger.i(
                            context,
                            TAG,
                            "Gemma runtime loaded from ${modelManager.getModelPath()} with backend $backend."
                        )
                    }
                } catch (throwable: Throwable) {
                    lastFailure = throwable
                    AppLogger.w(context, TAG, "Gemma backend $backend failed to initialize.", throwable)
                    if (throwable.isCorruptModelError()) {
                        modelManager.invalidateModel(INVALID_MODEL_MESSAGE)
                        throw IllegalStateException(INVALID_MODEL_MESSAGE, throwable)
                    }
                }
            }

            throw IllegalStateException(
                lastFailure?.localizedMessage ?: "Unable to initialize the Gemma runtime."
            )
        }
    }

    private suspend fun reset() {
        inferenceMutex.withLock {
            runCatching { inference?.close() }
            inference = null
            activeBackend = LlmInference.Backend.DEFAULT
        }
    }

    private fun validateStructuredResponse(prompt: String, response: String) {
        require(response.isNotBlank()) { "Gemma returned an empty response." }
        val expectsJson = prompt.contains("ONLY one JSON object", ignoreCase = true) ||
            prompt.contains("Reply ONLY with one JSON object", ignoreCase = true) ||
            prompt.contains("Reply ONLY with JSON", ignoreCase = true)
        if (!expectsJson) {
            return
        }

        val looksLikeObject = response.contains('{') && response.contains('}')
        require(looksLikeObject) {
            "Gemma returned a truncated structured response."
        }
    }

    private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    try {
                        continuation.resume(get())
                    } catch (throwable: Throwable) {
                        continuation.resumeWithException(throwable)
                    }
                },
                MoreExecutors.directExecutor()
            )
            continuation.invokeOnCancellation {
                cancel(true)
            }
        }
    }

    private fun Throwable.isCorruptModelError(): Boolean {
        return generateSequence(this) { it.cause }
            .mapNotNull { it.localizedMessage }
            .any { message ->
                message.contains("Unable to open zip archive", ignoreCase = true) ||
                    message.contains("zip archive", ignoreCase = true) ||
                    message.contains("model asset bundle", ignoreCase = true) ||
                    message.contains("task bundle", ignoreCase = true)
            }
    }
}
