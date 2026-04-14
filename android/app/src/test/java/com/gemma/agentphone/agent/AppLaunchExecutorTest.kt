package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLaunchExecutorTest {
    private val resolver = InstalledAppResolver { query ->
        if (query.equals("calculator", ignoreCase = true)) {
            ResolvedAppLaunch("com.google.android.calculator", "Calculator")
        } else {
            null
        }
    }

    private val executor = AppLaunchExecutor(resolver)
    private val observation = ScreenObservation("com.gemma.agentphone", emptyList(), 0L)

    @Test
    fun preparesLaunchIntentForResolvedApp() {
        val result = executor.execute(
            TaskStep(
                id = "open_app",
                type = StepType.OPEN_APP,
                description = "Open calculator",
                targetApp = "calculator"
            ),
            observation
        )

        assertThat(result.status).isEqualTo(StepStatus.SUCCESS)
        assertThat(result.externalAction?.spec?.packageName).isEqualTo("com.google.android.calculator")
    }

    @Test
    fun returnsSkippedWhenAppCannotBeResolved() {
        val result = executor.execute(
            TaskStep(
                id = "open_app",
                type = StepType.OPEN_APP,
                description = "Open unknown",
                targetApp = "unknown"
            ),
            observation
        )

        assertThat(result.status).isEqualTo(StepStatus.SKIPPED)
        assertThat(result.externalAction).isNull()
    }
}
