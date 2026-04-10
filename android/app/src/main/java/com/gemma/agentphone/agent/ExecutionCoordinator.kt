package com.gemma.agentphone.agent

class ExecutionCoordinator(
    private val goalInterpreter: GoalInterpreter,
    private val taskPlanner: TaskPlanner,
    private val policyEngine: PolicyEngine,
    private val observationService: ObservationService,
    private val executors: List<ActionExecutor>
) {
    fun run(command: String): ExecutionTrace {
        val goal = goalInterpreter.interpret(command)
        val observation = observationService.capture()
        val plan = taskPlanner.plan(goal, observation)
        val entries = mutableListOf<TraceEntry>()
        val externalActions = mutableListOf<ExternalActionRequest>()

        for (step in plan.steps) {
            val decision = policyEngine.classify(step)
            when (decision.action) {
                PolicyAction.BLOCK -> {
                    entries += TraceEntry(step.id, step.description, StepStatus.BLOCKED, "PolicyEngine", decision.reason)
                    return ExecutionTrace(goal, plan.strategy, entries, "Blocked: ${step.description}")
                }

                PolicyAction.REQUIRE_CONFIRMATION -> {
                    entries += TraceEntry(step.id, step.description, StepStatus.PENDING_CONFIRMATION, "PolicyEngine", decision.reason)
                    return ExecutionTrace(
                        goal = goal,
                        strategy = plan.strategy,
                        entries = entries,
                        finalMessage = "Waiting for user confirmation before continuing.",
                        awaitingConfirmation = true,
                        externalActions = externalActions
                    )
                }

                PolicyAction.ALLOW -> {
                    val executor = executors.firstOrNull { it.canExecute(step) }
                    if (executor == null) {
                        entries += TraceEntry(step.id, step.description, StepStatus.SKIPPED, "ExecutionCoordinator", "No executor available")
                        continue
                    }

                    try {
                        val result = executor.execute(step, observation)
                        result.externalAction?.let(externalActions::add)
                        entries += TraceEntry(step.id, step.description, result.status, result.executorName, result.message)
                    } catch (exception: Exception) {
                        entries += TraceEntry(
                            step.id,
                            step.description,
                            StepStatus.SKIPPED,
                            executor.javaClass.simpleName,
                            exception.localizedMessage ?: "Executor failed while preparing this step."
                        )
                    }
                }
            }
        }

        return ExecutionTrace(
            goal = goal,
            strategy = plan.strategy,
            entries = entries,
            finalMessage = "Execution plan prepared successfully.",
            externalActions = externalActions
        )
    }
}
