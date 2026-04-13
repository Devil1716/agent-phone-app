package com.gemma.agentphone.agent

import kotlinx.coroutines.delay

class ExecutionCoordinator(
    private val goalInterpreter: GoalInterpreter,
    private val taskPlanner: TaskPlanner,
    private val policyEngine: PolicyEngine,
    private val observationService: ObservationService,
    private val executors: List<ActionExecutor>
) {
    companion object {
        private const val MAX_AUTONOMOUS_STEPS = 5
    }

    suspend fun run(command: String): ExecutionTrace {
        val goal = goalInterpreter.interpret(command)
        var observation = observationService.capture()
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

                    if (step.type == StepType.EXECUTE_AUTONOMOUSLY) {
                        // Iterative loop for autonomous control
                        var currentStepObservation = observation
                        for (i in 1..MAX_AUTONOMOUS_STEPS) {
                            try {
                                val result = executor.execute(step, currentStepObservation)
                                result.externalAction?.let(externalActions::add)
                                entries += TraceEntry(
                                    stepId = "${step.id}_$i",
                                    description = "[Step $i] ${step.description}",
                                    status = result.status,
                                    executorName = result.executorName,
                                    thought = result.thought,
                                    detail = result.message
                                )

                                if (result.status != StepStatus.SUCCESS) break
                                if (result.message.contains("ACTION: DONE", ignoreCase = true)) break
                                if (result.message.contains("ACTION: WAIT", ignoreCase = true)) {
                                    delay(2000) // Non-blocking wait for animations
                                }

                                // Capture new state for next iteration
                                currentStepObservation = observationService.capture()
                            } catch (e: Exception) {
                                entries += TraceEntry(step.id, step.description, StepStatus.SKIPPED, executor.javaClass.simpleName, e.localizedMessage ?: "Iterative step failed")
                                break
                            }
                        }
                        // Update global observation for subsequent steps
                        observation = observationService.capture()
                    } else {
                        try {
                            val result = executor.execute(step, observation)
                            result.externalAction?.let(externalActions::add)
                            entries += TraceEntry(step.id, step.description, result.status, result.executorName, result.thought, result.message)
                            
                            // Update observation if the step succeeded
                            if (result.status == StepStatus.SUCCESS) {
                                observation = observationService.capture()
                            }
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
        }

        return ExecutionTrace(
            goal = goal,
            strategy = plan.strategy,
            entries = entries,
            finalMessage = "Execution completed. Iterative steps performed where necessary.",
            externalActions = externalActions
        )
    }
}
