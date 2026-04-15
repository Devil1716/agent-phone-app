package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiProviderDescriptor
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.AiResponse
import com.gemma.agentphone.model.GoalCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LlmGoalInterpreterTest {
    @Test
    fun parsesStructuredUnderstandingResponse() {
        val interpreter = LlmGoalInterpreter(
            aiProvider = object : AiProvider {
                override val descriptor: AiProviderDescriptor = AiProviderDescriptor(
                    id = "fake",
                    displayName = "Fake",
                    models = listOf("fake-model"),
                    supportsOffline = true
                )

                override fun infer(request: AiRequest): AiResponse {
                    return AiResponse(
                        providerId = descriptor.id,
                        model = descriptor.models.first(),
                        summary = """
                            {
                              "category": "GENERAL_APP_CONTROL",
                              "targetApp": "Play Store",
                              "payload": "open play store and find a notes app",
                              "understanding": "Open the Play Store and search for a notes app.",
                              "shouldOpenAppFirst": true
                            }
                        """.trimIndent(),
                        latencyMs = 1L
                    )
                }
            }
        )

        val goal = interpreter.interpret("open play store and find a notes app")

        assertThat(goal.category).isEqualTo(GoalCategory.GENERAL_APP_CONTROL)
        assertThat(goal.targetApp).isEqualTo("Play Store")
        assertThat(goal.shouldOpenAppFirst).isTrue()
        assertThat(goal.understanding).contains("Play Store")
    }
}
