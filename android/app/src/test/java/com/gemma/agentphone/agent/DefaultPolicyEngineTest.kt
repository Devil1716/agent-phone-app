package com.gemma.agentphone.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultPolicyEngineTest {
    private val policyEngine = DefaultPolicyEngine()

    @Test
    fun lowRiskActionsAreAllowed() {
        val decision = policyEngine.classify(
            TaskStep("wifi", StepType.OPEN_SYSTEM_SETTINGS, "Open Wi-Fi settings")
        )

        assertThat(decision.action).isEqualTo(PolicyAction.ALLOW)
    }

    @Test
    fun confirmationActionsPauseExecution() {
        val decision = policyEngine.classify(
            TaskStep(
                "confirm_send",
                StepType.REQUEST_CONFIRMATION,
                "Confirm before sending",
                riskLevel = RiskLevel.CONFIRM_REQUIRED
            )
        )

        assertThat(decision.action).isEqualTo(PolicyAction.REQUIRE_CONFIRMATION)
    }
}
