package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntentExecutorTest {
    private val executor = IntentExecutor()
    private val observation = ScreenObservation("com.gemma.agentphone", emptyList(), 0L)

    @Test
    fun canExecuteSettingsStep() {
        val step = TaskStep("wifi", StepType.OPEN_SYSTEM_SETTINGS, "Open Wi-Fi")
        assertThat(executor.canExecute(step)).isTrue()
    }

    @Test
    fun canExecuteMapsStep() {
        val step = TaskStep("maps", StepType.OPEN_MAPS, "Open maps")
        assertThat(executor.canExecute(step)).isTrue()
    }

    @Test
    fun canExecuteMessageStep() {
        val step = TaskStep("msg", StepType.DRAFT_MESSAGE, "Draft message")
        assertThat(executor.canExecute(step)).isTrue()
    }

    @Test
    fun cannotExecuteBrowserStep() {
        val step = TaskStep("search", StepType.OPEN_BROWSER_SEARCH, "Search")
        assertThat(executor.canExecute(step)).isFalse()
    }

    @Test
    fun wifiSettingsProducesCorrectIntent() {
        val step = TaskStep("wifi", StepType.OPEN_SYSTEM_SETTINGS, "Open Wi-Fi", payload = "wifi")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SUCCESS)
        assertThat(result.executorName).isEqualTo("IntentExecutor")
        assertThat(result.externalAction).isNotNull()
        assertThat(result.externalAction!!.spec.action).isEqualTo("android.settings.WIFI_SETTINGS")
    }

    @Test
    fun mapsProducesNavigationUri() {
        val step = TaskStep("maps", StepType.OPEN_MAPS, "Navigate", payload = "Central Park")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SUCCESS)
        assertThat(result.externalAction!!.spec.data).isEqualTo("google.navigation:q=Central+Park")
    }

    @Test
    fun mapsWithBlankPayloadDefaultsToHome() {
        val step = TaskStep("maps", StepType.OPEN_MAPS, "Navigate", payload = "")
        val result = executor.execute(step, observation)

        assertThat(result.externalAction!!.spec.data).isEqualTo("google.navigation:q=home")
    }

    @Test
    fun draftMessageProducesSmsIntent() {
        val step = TaskStep("msg", StepType.DRAFT_MESSAGE, "Draft", payload = "hello there")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SUCCESS)
        assertThat(result.externalAction!!.spec.action).isEqualTo("android.intent.action.SENDTO")
        assertThat(result.externalAction!!.spec.data).contains("smsto:")
    }

    @Test
    fun unsupportedStepTypeIsSkipped() {
        val step = TaskStep("x", StepType.SUMMARIZE_NOTIFICATIONS, "Summarize")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SKIPPED)
    }
}
