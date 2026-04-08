package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityExecutorTest {
    private val executor = AccessibilityExecutor()
    private val observation = ScreenObservation("com.gemma.agentphone", emptyList(), 0L)

    @Test
    fun canExecuteNotificationSummary() {
        val step = TaskStep("notif", StepType.SUMMARIZE_NOTIFICATIONS, "Summarize")
        assertThat(executor.canExecute(step)).isTrue()
    }

    @Test
    fun canExecuteReportResult() {
        val step = TaskStep("report", StepType.REPORT_RESULT, "Report")
        assertThat(executor.canExecute(step)).isTrue()
    }

    @Test
    fun cannotExecuteSettingsStep() {
        val step = TaskStep("wifi", StepType.OPEN_SYSTEM_SETTINGS, "Settings")
        assertThat(executor.canExecute(step)).isFalse()
    }

    @Test
    fun cannotExecuteBrowserStep() {
        val step = TaskStep("search", StepType.OPEN_BROWSER_SEARCH, "Search")
        assertThat(executor.canExecute(step)).isFalse()
    }

    @Test
    fun notificationSummaryReturnsSuccess() {
        val step = TaskStep("notif", StepType.SUMMARIZE_NOTIFICATIONS, "Summarize notifications")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SUCCESS)
        assertThat(result.executorName).isEqualTo("AccessibilityExecutor")
        assertThat(result.message).contains("Notification")
    }

    @Test
    fun reportResultReturnsFallbackMessage() {
        val step = TaskStep("report", StepType.REPORT_RESULT, "General app control")
        val result = executor.execute(step, observation)

        assertThat(result.status).isEqualTo(StepStatus.SUCCESS)
        assertThat(result.message).contains("slower general app-control planner")
    }

    @Test
    fun noExternalActionsGenerated() {
        val step = TaskStep("notif", StepType.SUMMARIZE_NOTIFICATIONS, "Summarize")
        val result = executor.execute(step, observation)

        assertThat(result.externalAction).isNull()
    }
}
