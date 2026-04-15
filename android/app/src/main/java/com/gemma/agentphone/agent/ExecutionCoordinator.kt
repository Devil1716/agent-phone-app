package com.gemma.agentphone.agent

import kotlinx.coroutines.delay

class ExecutionCoordinator(
    private val goalInterpreter: GoalInterpreter,
    private val taskPlanner: TaskPlanner,
    private val policyEngine: PolicyEngine,
    private val observationService: ObservationService,
    private val executors: List<ActionExecutor>,
    private val externalActionDispatcher: ((ExternalActionRequest) -> Unit)? = null
) {
    companion object {
        private const val MAX_AUTONOMOUS_STEPS = 5
    }

    suspend fun run(
        command: String,
        onProgress: ((TraceEntry) -> Unit)? = null
    ): ExecutionTrace {
        val goal = goalInterpreter.interpret(command)
        var observation = observationService.capture()
        val plan = taskPlanner.plan(goal, observation)
        val entries = mutableListOf<TraceEntry>()
        val pendingExternalActions = mutableListOf<ExternalActionRequest>()
        val understandingEntry = TraceEntry(
            stepId = "understand_request",
            description = "Understand the request",
            status = StepStatus.SUCCESS,
            executorName = "GoalInterpreter",
            thought = goal.understanding ?: "Analyzing the command and selecting the safest execution path.",
            detail = buildGoalSummary(goal)
        )
        entries += understandingEntry
        onProgress?.invoke(understandingEntry)

        for (step in plan.steps) {
            val decision = policyEngine.classify(step)
            when (decision.action) {
                PolicyAction.BLOCK -> {
                    val entry = TraceEntry(
                        stepId = step.id,
                        description = step.description,
                        status = StepStatus.BLOCKED,
                        executorName = "PolicyEngine",
                        detail = decision.reason
                    )
                    entries += entry
                    onProgress?.invoke(entry)
                    return ExecutionTrace(goal, plan.strategy, entries, "Blocked: ${step.description}")
                }

                PolicyAction.REQUIRE_CONFIRMATION -> {
                    val entry = TraceEntry(
                        stepId = step.id,
                        description = step.description,
                        status = StepStatus.PENDING_CONFIRMATION,
                        executorName = "PolicyEngine",
                        detail = decision.reason
                        )
                        entries += entry
                        onProgress?.invoke(entry)
                        return ExecutionTrace(
                            goal = goal,
                            strategy = plan.strategy,
                            entries = entries,
                            finalMessage = "Waiting for user confirmation before continuing.",
                            awaitingConfirmation = true,
                            externalActions = pendingExternalActions
                        )
                    }

                PolicyAction.ALLOW -> {
                    val executor = executors.firstOrNull { it.canExecute(step) }
                    if (executor == null) {
                        val entry = TraceEntry(
                            stepId = step.id,
                            description = step.description,
                            status = StepStatus.SKIPPED,
                            executorName = "ExecutionCoordinator",
                            detail = "No executor available"
                        )
                        entries += entry
                        onProgress?.invoke(entry)
                        continue
                    }

                    if (step.type == StepType.EXECUTE_AUTONOMOUSLY) {
                        // Iterative loop for autonomous control
                        var currentStepObservation = observation
                        for (i in 1..MAX_AUTONOMOUS_STEPS) {
                            try {
                                val result = executor.execute(step, currentStepObservation)
                                dispatchOrQueue(result.externalAction, pendingExternalActions)
                                val entry = TraceEntry(
                                    stepId = "${step.id}_$i",
                                    description = "[Step $i] ${step.description}",
                                    status = result.status,
                                    executorName = result.executorName,
                                    thought = result.thought,
                                    detail = result.message
                                )
                                entries += entry
                                onProgress?.invoke(entry)

                                if (result.status != StepStatus.SUCCESS) break
                                if (result.message.contains("ACTION: DONE", ignoreCase = true)) break
                                if (result.message.contains("ACTION: WAIT", ignoreCase = true)) {
                                    delay(2000) // Non-blocking wait for animations
                                }

                                // Capture new state for next iteration
                                currentStepObservation = observationService.capture()
                            } catch (e: Exception) {
                                entries += TraceEntry(
                                    stepId = step.id,
                                    description = step.description,
                                    status = StepStatus.SKIPPED,
                                    executorName = executor.javaClass.simpleName,
                                    detail = e.localizedMessage ?: "Iterative step failed"
                                )
                                break
                            }
                        }
                        // Update global observation for subsequent steps
                        observation = observationService.capture()
                    } else {
                        try {
                            val result = executor.execute(step, observation)
                            dispatchOrQueue(result.externalAction, pendingExternalActions)
                            val entry = TraceEntry(
                                stepId = step.id,
                                description = step.description,
                                status = result.status,
                                executorName = result.executorName,
                                thought = result.thought,
                                detail = result.message
                            )
                            entries += entry
                            onProgress?.invoke(entry)
                            
                            // Update observation if the step succeeded
                            if (result.status == StepStatus.SUCCESS) {
                                observation = observationService.capture()
                            }
                        } catch (exception: Exception) {
                            entries += TraceEntry(
                                stepId = step.id,
                                description = step.description,
                                status = StepStatus.SKIPPED,
                                executorName = executor.javaClass.simpleName,
                                detail = exception.localizedMessage ?: "Executor failed while preparing this step."
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
            externalActions = pendingExternalActions
        )
    }

    private fun dispatchOrQueue(
        action: ExternalActionRequest?,
        pendingExternalActions: MutableList<ExternalActionRequest>
    ) {
        if (action == null) {
            return
        }

        if (externalActionDispatcher == null) {
            pendingExternalActions += action
            return
        }

        externalActionDispatcher.invoke(action)
    }

    private fun buildGoalSummary(goal: com.gemma.agentphone.model.UserGoal): String {
        val target = goal.targetApp?.let { " | target app: $it" }.orEmpty()
        val targetValue = goal.targetValue?.takeIf { it.isNotBlank() }?.let { " | target value: $it" }.orEmpty()
        val launchHint = if (goal.shouldOpenAppFirst) " | open app first" else ""
        return "Category: ${goal.category}$target$targetValue$launchHint"
    }
}
