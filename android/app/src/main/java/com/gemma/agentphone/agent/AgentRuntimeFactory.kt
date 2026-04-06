package com.gemma.agentphone.agent

class AgentRuntimeFactory {
    fun createCoordinator(): ExecutionCoordinator {
        return ExecutionCoordinator(
            goalInterpreter = RuleBasedGoalInterpreter(),
            taskPlanner = TemplateTaskPlanner(),
            policyEngine = DefaultPolicyEngine(),
            observationService = DefaultObservationService(),
            executors = listOf(
                IntentExecutor(),
                BrowserExecutor(),
                AccessibilityExecutor()
            )
        )
    }
}
