package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiProviderDescriptor
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.AiResponse
import com.gemma.agentphone.model.GoalCategory
import org.junit.Test

class ResilientAiProviderTest {

    @Test
    fun usesFallbackWhenPrimaryReportsNotReady() {
        val provider = ResilientAiProvider(
            primary = fakeProvider("Primary", "The local Gemma runtime is not ready yet."),
            fallback = fakeProvider("Fallback", "Handled by relay provider.")
        )

        val response = provider.infer(sampleRequest())

        assertThat(response.summary).contains("Handled by relay provider.")
        assertThat(response.summary).contains("fallback used")
    }

    @Test
    fun keepsPrimaryResponseWhenHealthy() {
        val provider = ResilientAiProvider(
            primary = fakeProvider("Primary", "Primary provider handled the request."),
            fallback = fakeProvider("Fallback", "Handled by relay provider.")
        )

        val response = provider.infer(sampleRequest())

        assertThat(response.summary).contains("Primary provider handled the request.")
        assertThat(response.summary).doesNotContain("fallback used")
    }

    @Test
    fun fallsBackWhenPrimaryThrows() {
        val provider = ResilientAiProvider(
            primary = throwingProvider("Primary"),
            fallback = fakeProvider("Fallback", "Handled by relay provider.")
        )

        val response = provider.infer(sampleRequest())

        assertThat(response.summary).contains("Handled by relay provider.")
        assertThat(response.summary).contains("fallback used")
    }

    @Test
    fun returnsFailureSummaryWhenFallbackThrows() {
        val provider = ResilientAiProvider(
            primary = throwingProvider("Primary"),
            fallback = throwingProvider("Fallback")
        )

        val response = provider.infer(sampleRequest())

        assertThat(response.summary).contains("Fallback provider failed")
        assertThat(response.summary).contains("fallback used")
    }

    private fun sampleRequest(): AiRequest {
        return AiRequest(
            prompt = "Open Wi-Fi settings",
            mode = "autonomous",
            targetCategory = GoalCategory.OPEN_SETTINGS
        )
    }

    private fun fakeProvider(name: String, summary: String): AiProvider {
        return object : AiProvider {
            override val descriptor = AiProviderDescriptor(
                id = name.lowercase(),
                displayName = name,
                models = listOf("test-model"),
                supportsOffline = false
            )

            override fun infer(request: AiRequest): AiResponse {
                return AiResponse(
                    providerId = descriptor.id,
                    model = descriptor.models.first(),
                    summary = summary,
                    latencyMs = 10
                )
            }
        }
    }

    private fun throwingProvider(name: String): AiProvider {
        return object : AiProvider {
            override val descriptor = AiProviderDescriptor(
                id = name.lowercase(),
                displayName = name,
                models = listOf("test-model"),
                supportsOffline = false
            )

            override fun infer(request: AiRequest): AiResponse {
                throw IllegalStateException("$name crashed")
            }
        }
    }
}
