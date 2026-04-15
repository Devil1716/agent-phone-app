package com.gemma.agentphone.agent

import com.gemma.agentphone.accessibility.ScreenStateReader
import com.gemma.agentphone.actions.ActionDispatcher
import kotlinx.coroutines.delay

class ExecutorAgent(
    private val plannerAgent: PlannerAgent,
    private val verifierAgent: VerifierAgent,
    private val actionDispatcher: ActionDispatcher,
    private val screenStateReader: ScreenStateReader
) {
    companion object {
        private const val MAX_TOTAL_STEPS = 50
        private const val MAX_STEP_RETRIES = 2
    }

    suspend fun execute(
        command: String,
        initialPlan: List<AgentStep>,
        emitStatus: (AgentStatus) -> Unit,
        emitLog: (AgentLogEntry) -> Unit,
        shouldStop: () -> Boolean
    ): AgentExecutionSummary {
        var steps = initialPlan.toMutableList()
        var index = 0
        var executedSteps = 0

        while (index < steps.size && executedSteps < MAX_TOTAL_STEPS) {
            if (shouldStop()) {
                return AgentExecutionSummary(false, "Execution stopped by the user.")
            }

            val step = steps[index]
            if (step.normalizedAction == "DONE") {
                val completionCheck = verifierAgent.confirmCompletion(command, screenStateReader.read())
                emitLog(
                    AgentLogEntry(
                        "Complete?",
                        completionCheck.reason.ifBlank { "Gemma believes the task is complete." },
                        success = completionCheck.success,
                        step = step
                    )
                )
                if (completionCheck.success) {
                    emitLog(AgentLogEntry("Completed", step.summary(), success = true, step = step))
                    return AgentExecutionSummary(true, step.reason.ifBlank { "Task completed." })
                }
                emitLog(
                    AgentLogEntry(
                        "Replan",
                        completionCheck.nextHint.ifBlank { "Task still looks incomplete. Replanning." },
                        success = null
                    )
                )
                steps = plannerAgent.replan(command, screenStateReader.read(), step, completionCheck.nextHint).toMutableList()
                index = 0
                continue
            }

            emitStatus(AgentStatus.Executing(step, index + 1, steps.size))
            var attempt = 0
            var stepCompleted = false
            var lastFailure = "Unable to complete ${step.summary()}."

            while (attempt <= MAX_STEP_RETRIES && !stepCompleted) {
                val actionResult = actionDispatcher.dispatch(step)
                emitLog(
                    AgentLogEntry(
                        title = step.normalizedAction,
                        detail = if (attempt == 0) {
                            actionResult.message
                        } else {
                            "${actionResult.message} Retry ${attempt}/${MAX_STEP_RETRIES}."
                        },
                        success = actionResult.success,
                        step = step
                    )
                )
                if (!actionResult.success) {
                    lastFailure = actionResult.message
                    attempt += 1
                    if (attempt > MAX_STEP_RETRIES) {
                        return AgentExecutionSummary(false, actionResult.message)
                    }
                    delay(600L)
                    continue
                }

                delay(500L)
                val screenState = screenStateReader.read()
                val verification = verifierAgent.verify(screenState, step)
                emitLog(
                    AgentLogEntry(
                        title = "Verify",
                        detail = verification.reason.ifBlank { "Step verified." },
                        success = verification.success,
                        step = step
                    )
                )

                if (verification.success) {
                    stepCompleted = true
                } else {
                    lastFailure = verification.reason.ifBlank { "Step verification failed." }
                    attempt += 1
                    if (attempt > MAX_STEP_RETRIES) {
                        emitLog(
                            AgentLogEntry(
                                title = "Replan",
                                detail = verification.nextHint.ifBlank { "Gemma requested a new plan." },
                                success = null
                            )
                        )
                        steps = plannerAgent.replan(command, screenState, step, verification.nextHint).toMutableList()
                        index = 0
                        executedSteps += 1
                        break
                    }
                    delay(700L)
                }
            }

            if (!stepCompleted && index == 0 && executedSteps > 0) {
                continue
            }
            if (!stepCompleted && attempt > MAX_STEP_RETRIES) {
                return AgentExecutionSummary(false, lastFailure)
            }
            index += 1
            executedSteps += 1
        }

        return AgentExecutionSummary(
            success = false,
            summary = "Reached the 50-step safety limit. Please confirm before continuing."
        )
    }
}
