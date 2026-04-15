package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PlannerAgentTest {
    @Test
    fun retriesWhenPlanIsOnlyDone() = runBlocking {
        val engine = FakeEngine(
            responses = ArrayDeque(
                listOf(
                    """[{"action":"DONE","target":"","value":"","reason":"Task complete"}]""",
                    """
                    [
                      {"action":"LAUNCH_APP","target":"chrome","value":"","reason":"Open Chrome"},
                      {"action":"DONE","target":"","value":"","reason":"Task complete"}
                    ]
                    """.trimIndent()
                )
            )
        )

        val plan = PlannerAgent(engine).plan("open chrome")

        assertThat(plan.map { it.normalizedAction }).containsExactly("LAUNCH_APP", "DONE").inOrder()
        assertThat(engine.calls).isEqualTo(2)
    }

    @Test
    fun appendsDoneStepWhenMissing() = runBlocking {
        val engine = FakeEngine(
            responses = ArrayDeque(
                listOf("""[{"action":"LAUNCH_APP","target":"spotify","value":"","reason":"Open Spotify"}]""")
            )
        )

        val plan = PlannerAgent(engine).plan("open spotify")

        assertThat(plan.last().normalizedAction).isEqualTo("DONE")
        assertThat(plan).hasSize(2)
    }

    @Test
    fun usesDeterministicPlayStoreInstallPlan() = runBlocking {
        val engine = FakeEngine(responses = ArrayDeque())

        val plan = PlannerAgent(engine).plan("open play store and download subway surfers")

        assertThat(plan.map { it.normalizedAction })
            .containsExactly("LAUNCH_APP", "SEARCH", "TAP_TEXT", "TAP_TEXT", "WAIT", "DONE")
            .inOrder()
        assertThat(plan[1].target).contains("Search apps & games")
        assertThat(plan[1].value).isEqualTo("subway surfers")
        assertThat(engine.calls).isEqualTo(0)
    }
}

private class FakeEngine(
    private val responses: ArrayDeque<String>
) : TextGenerationEngine {
    var calls: Int = 0
        private set

    override suspend fun generate(prompt: String): String {
        calls += 1
        return responses.removeFirst()
    }
}
