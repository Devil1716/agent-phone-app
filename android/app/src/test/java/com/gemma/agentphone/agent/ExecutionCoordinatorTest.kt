package com.gemma.agentphone.agent

import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExecutionCoordinatorTest {

    private fun buildCoordinator(
        policyEngine: PolicyEngine = DefaultPolicyEngine(),
        executors: List<ActionExecutor> = listOf(IntentExecutor(), BrowserExecutor(), AccessibilityExecutor())
    ): ExecutionCoordinator {
        return ExecutionCoordinator(
            goalInterpreter = RuleBasedGoalInterpreter(),
            taskPlanner = TemplateTaskPlanner(),
            policyEngine = policyEngine,
            observationService = DefaultObservationService(),
            executors = executors
        )
    }

    @Test
    fun wifiCommandProducesSuccessTrace() {
        val coordinator = buildCoordinator()
        val trace = coordinator.run("open Wi-Fi settings")

        assertThat(trace.goal.category).isEqualTo(GoalCategory.OPEN_SETTINGS)
        assertThat(trace.strategy).isEqualTo(ExecutionStrategy.FAST_PATH)
        assertThat(trace.entries).hasSize(1)
        assertThat(trace.entries[0].status).isEqualTo(StepStatus.SUCCESS)
        assertThat(trace.externalActions).hasSize(1)
        assertThat(trace.externalActions[0].spec.action).isEqualTo("android.settings.WIFI_SETTINGS")
        assertThat(trace.awaitingConfirmation).isFalse()
    }

    @Test
    fun browserSearchSucceeds() {
        val coordinator = buildCoordinator()
        val trace = coordinator.run("search the web for Gemma")

        assertThat(trace.goal.category).isEqualTo(GoalCategory.WEB_SEARCH)
        assertThat(trace.entries).hasSize(1)
        assertThat(trace.entries[0].status).isEqualTo(StepStatus.SUCCESS)
        assertThat(trace.externalActions).hasSize(1)
        assertThat(trace.externalActions[0].spec.data).contains("google.com/search")
    }

    @Test
    fun messageCommandPausesAtConfirmation() {
        val coordinator = buildCoordinator()
        val trace = coordinator.run("message Rahul I am late")

        assertThat(trace.goal.category).isEqualTo(GoalCategory.DRAFT_MESSAGE)
        assertThat(trace.awaitingConfirmation).isTrue()
        assertThat(trace.entries.last().status).isEqualTo(StepStatus.PENDING_CONFIRMATION)
    }

    @Test
    fun unrecognizedCommandFallsBackToGeneralAppControl() {
        val coordinator = buildCoordinator()
        val trace = coordinator.run("do something completely unrecognized")

        assertThat(trace.goal.category).isEqualTo(GoalCategory.GENERAL_APP_CONTROL)
        assertThat(trace.entries).isNotEmpty()
        assertThat(trace.entries.last().status).isEqualTo(StepStatus.SUCCESS)
        assertThat(trace.entries.last().executorName).isEqualTo("AccessibilityExecutor")
    }

    @Test
    fun externalActionsAreCollectedAcrossMultipleSteps() {
        val coordinator = buildCoordinator()
        val trace = coordinator.run("navigate to central park")

        assertThat(trace.goal.category).isEqualTo(GoalCategory.OPEN_MAPS)
        assertThat(trace.externalActions).isNotEmpty()
    }

    @Test
    fun noExecutorAvailableSkipsStep() {
        // Coordinator with no executors
        val coordinator = buildCoordinator(executors = emptyList())
        val trace = coordinator.run("open Wi-Fi settings")

        assertThat(trace.entries).hasSize(1)
        assertThat(trace.entries[0].status).isEqualTo(StepStatus.SKIPPED)
        assertThat(trace.externalActions).isEmpty()
    }

    @Test
    fun callCommandRequiresConfirmation() {
        val coordinator = buildCoordinator()
        val trace = coordinator.run("call Mom")

        assertThat(trace.goal.category).isEqualTo(GoalCategory.PLACE_CALL)
        assertThat(trace.awaitingConfirmation).isTrue()
    }

    @Test
    fun playMediaUsesYouTubeSearch() {
        val coordinator = buildCoordinator()
        val trace = coordinator.run("play lo-fi music")

        assertThat(trace.goal.category).isEqualTo(GoalCategory.PLAY_MEDIA)
        assertThat(trace.externalActions).hasSize(1)
        assertThat(trace.externalActions[0].spec.data).contains("youtube.com")
    }
}
