package com.gemma.agentphone.agent

import com.gemma.agentphone.accessibility.ScreenState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class VerifierAgentTest {
    @Test
    fun malformedCompletionResponseDoesNotMarkPlayStoreInstallDone() = runBlocking {
        val verifier = VerifierAgent(StaticEngine("not json"))

        val result = verifier.confirmCompletion(
            "open play store and download subway surfers",
            ScreenState(
                visibleText = listOf("Subway Surfers", "Install"),
                packageName = "com.android.vending",
                activityName = "DetailsActivity",
                timestamp = 0L
            )
        )

        assertThat(result.success).isFalse()
        assertThat(result.reason).contains("does not show")
    }

    @Test
    fun malformedLaunchVerificationCanStillUsePlayStoreFallback() = runBlocking {
        val verifier = VerifierAgent(StaticEngine("not json"))

        val result = verifier.verify(
            ScreenState(
                visibleText = listOf("Google Play", "Search apps & games"),
                packageName = "com.android.vending",
                activityName = "MainActivity",
                timestamp = 0L
            ),
            AgentStep("LAUNCH_APP", "Play Store", "", "Open Play Store")
        )

        assertThat(result.success).isTrue()
        assertThat(result.reason).contains("appears to be open")
    }
}

private class StaticEngine(
    private val response: String
) : TextGenerationEngine {
    override suspend fun generate(prompt: String): String = response
}
