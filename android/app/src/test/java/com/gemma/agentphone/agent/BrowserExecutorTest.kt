package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrowserExecutorTest {
    private val executor = BrowserExecutor()
    private val observation = ScreenObservation("com.gemma.agentphone", emptyList(), 0L)

    @Test
    fun canExecuteBrowserSearch() {
        val step = TaskStep("search", StepType.OPEN_BROWSER_SEARCH, "Search")
        assertThat(executor.canExecute(step)).isTrue()
    }

    @Test
    fun canExecuteMediaSearch() {
        val step = TaskStep("media", StepType.OPEN_MEDIA_SEARCH, "Play")
        assertThat(executor.canExecute(step)).isTrue()
    }

    @Test
    fun cannotExecuteSettingsStep() {
        val step = TaskStep("wifi", StepType.OPEN_SYSTEM_SETTINGS, "Settings")
        assertThat(executor.canExecute(step)).isFalse()
    }

    @Test
    fun browserSearchProducesGoogleUrl() {
        val step = TaskStep("search", StepType.OPEN_BROWSER_SEARCH, "Search", payload = "Gemma Android")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SUCCESS)
        assertThat(result.executorName).isEqualTo("BrowserExecutor")
        assertThat(result.externalAction).isNotNull()
        assertThat(result.externalAction!!.spec.action).isEqualTo("android.intent.action.VIEW")
        assertThat(result.externalAction!!.spec.data).isEqualTo("https://www.google.com/search?q=Gemma+Android")
    }

    @Test
    fun mediaSearchProducesYouTubeUrl() {
        val step = TaskStep("media", StepType.OPEN_MEDIA_SEARCH, "Play", payload = "lo-fi beats")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SUCCESS)
        assertThat(result.externalAction!!.spec.data).isEqualTo("https://m.youtube.com/results?search_query=lo-fi+beats")
    }

    @Test
    fun emptyPayloadDoesNotCrash() {
        val step = TaskStep("search", StepType.OPEN_BROWSER_SEARCH, "Search", payload = "")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SUCCESS)
        assertThat(result.externalAction!!.spec.data).contains("google.com/search")
    }

    @Test
    fun unsupportedStepTypeIsSkipped() {
        val step = TaskStep("x", StepType.OPEN_MAPS, "Maps")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SKIPPED)
    }
}
