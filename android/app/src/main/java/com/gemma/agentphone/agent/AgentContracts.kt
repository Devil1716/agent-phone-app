package com.gemma.agentphone.agent

import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal

data class TaskPlan(
    val strategy: ExecutionStrategy,
    val steps: List<TaskStep>
)

data class TaskStep(
    val id: String,
    val type: StepType,
    val description: String,
    val targetApp: String? = null,
    val payload: String? = null,
    val riskLevel: RiskLevel = RiskLevel.LOW
)

data class ScreenObservation(
    val foregroundApp: String,
    val visibleText: List<String>,
    val timestampMs: Long
)

data class PolicyDecision(
    val action: PolicyAction,
    val reason: String
)

data class StepResult(
    val stepId: String,
    val status: StepStatus,
    val message: String,
    val executorName: String,
    val externalAction: ExternalActionRequest? = null
)

data class TraceEntry(
    val stepId: String,
    val description: String,
    val status: StepStatus,
    val executorName: String,
    val detail: String
)

data class ExecutionTrace(
    val goal: UserGoal,
    val strategy: ExecutionStrategy,
    val entries: List<TraceEntry>,
    val finalMessage: String,
    val awaitingConfirmation: Boolean = false,
    val externalActions: List<ExternalActionRequest> = emptyList()
)

data class ExternalActionRequest(
    val spec: IntentSpec
)

data class IntentSpec(
    val action: String,
    val data: String? = null,
    val packageName: String? = null
)

data class BrowserTask(
    val urlOrQuery: String,
    val action: BrowserAction
)

data class BrowserPageState(
    val title: String,
    val url: String,
    val visibleText: List<String>
)

data class BrowserObservation(
    val currentState: BrowserPageState?,
    val confidence: Float
)

enum class ExecutionStrategy {
    FAST_PATH,
    SLOW_PATH
}

enum class StepType {
    OPEN_SYSTEM_SETTINGS,
    OPEN_MAPS,
    OPEN_BROWSER_SEARCH,
    OPEN_MEDIA_SEARCH,
    DRAFT_MESSAGE,
    REQUEST_CONFIRMATION,
    SUMMARIZE_NOTIFICATIONS,
    REPORT_RESULT,
    UNSUPPORTED
}

enum class RiskLevel {
    LOW,
    CONFIRM_REQUIRED,
    BLOCKED
}

enum class PolicyAction {
    ALLOW,
    REQUIRE_CONFIRMATION,
    BLOCK
}

enum class StepStatus {
    SUCCESS,
    PENDING_CONFIRMATION,
    BLOCKED,
    SKIPPED
}

enum class BrowserAction {
    SEARCH,
    OPEN_PAGE,
    SUMMARIZE,
    FILL_FORM
}

interface GoalInterpreter {
    fun interpret(input: String): UserGoal
}

interface TaskPlanner {
    fun plan(goal: UserGoal, observation: ScreenObservation): TaskPlan
}

interface PolicyEngine {
    fun classify(step: TaskStep): PolicyDecision
}

interface ObservationService {
    fun capture(): ScreenObservation
}

interface ActionExecutor {
    fun canExecute(step: TaskStep): Boolean
    fun execute(step: TaskStep, observation: ScreenObservation): StepResult
}

fun GoalCategory.defaultStrategy(): ExecutionStrategy {
    return when (this) {
        GoalCategory.GENERAL_APP_CONTROL, GoalCategory.UNSUPPORTED -> ExecutionStrategy.SLOW_PATH
        else -> ExecutionStrategy.FAST_PATH
    }
}
