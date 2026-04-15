package com.gemma.agentphone.agent

import com.gemma.agentphone.accessibility.ScreenState
import com.google.gson.Gson
import com.google.gson.JsonParser

class PlannerAgent(
    private val engine: TextGenerationEngine,
    private val gson: Gson = Gson()
) {
    private val allowedActions = setOf(
        "LAUNCH_APP",
        "TAP_TEXT",
        "TAP_COORDS",
        "INPUT_TEXT",
        "SCROLL_DOWN",
        "SCROLL_UP",
        "PRESS_BACK",
        "PRESS_HOME",
        "WAIT",
        "DONE",
        "SEARCH"
    )

    suspend fun plan(command: String): List<AgentStep> {
        return requestPlan(buildPrompt(command, null, null, ""))
    }

    suspend fun replan(
        command: String,
        screenState: ScreenState,
        failedStep: AgentStep,
        verifierHint: String
    ): List<AgentStep> {
        return requestPlan(buildPrompt(command, screenState, failedStep, verifierHint))
    }

    private suspend fun requestPlan(prompt: String): List<AgentStep> {
        var lastResponse = ""
        repeat(3) { attempt ->
            lastResponse = engine.generate(
                if (attempt == 0) {
                    prompt
                } else {
                    """
                    Your previous answer was invalid JSON.
                    Rewrite it as ONLY a JSON array of steps with fields:
                    action, target, value, reason.
                    Previous answer:
                    $lastResponse
                    """.trimIndent()
                }
            )
            parseSteps(lastResponse)?.takeIf(::isPlanCompleteEnough)?.let { return it }
        }
        throw IllegalStateException("Gemma 4 returned malformed planning JSON.")
    }

    private fun parseSteps(raw: String): List<AgentStep>? {
        return runCatching {
            val jsonStart = raw.indexOf('[')
            val jsonEnd = raw.lastIndexOf(']')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd < jsonStart) {
                return null
            }
            val array = JsonParser.parseString(raw.substring(jsonStart, jsonEnd + 1)).asJsonArray
            val parsed = array.mapNotNull { element ->
                val obj = element.asJsonObject
                AgentStep(
                    action = obj.get("action")?.asString.orEmpty(),
                    target = obj.get("target")?.asString.orEmpty(),
                    value = obj.get("value")?.asString.orEmpty(),
                    reason = obj.get("reason")?.asString.orEmpty()
                ).takeIf { it.normalizedAction in allowedActions }
            }
            if (parsed.isEmpty()) {
                null
            } else if (parsed.last().normalizedAction != "DONE") {
                parsed + AgentStep("DONE", "", "", "Task complete")
            } else {
                parsed
            }
        }.getOrNull()
    }

    private fun isPlanCompleteEnough(steps: List<AgentStep>): Boolean {
        if (steps.isEmpty()) {
            return false
        }
        if (steps.size == 1 && steps.single().normalizedAction == "DONE") {
            return false
        }
        val hasRealWork = steps.any { it.normalizedAction != "DONE" }
        return hasRealWork
    }

    private fun buildPrompt(
        command: String,
        screenState: ScreenState?,
        failedStep: AgentStep?,
        verifierHint: String
    ): String {
        val contextBlock = buildString {
            if (screenState != null) {
                appendLine("Current package: ${screenState.packageName}")
                appendLine("Current activity: ${screenState.activityName}")
                appendLine("Visible screen text:")
                appendLine(screenState.visibleText.joinToString(separator = "\n"))
                appendLine()
            }
            if (failedStep != null) {
                appendLine("Last failed step: ${gson.toJson(failedStep)}")
                appendLine("Verifier hint: $verifierHint")
                appendLine()
            }
        }

        return """
            You are a phone control agent powered by Gemma 4.
            Output ONLY a JSON array of steps.
            Every step must match:
            {"action": string, "target": string, "value": string, "reason": string}
            Do not truncate the JSON. Finish the entire plan before stopping.
            Prefer complete, moderate-length plans that finish the task instead of short partial plans.

            Valid actions:
            LAUNCH_APP, TAP_TEXT, TAP_COORDS, INPUT_TEXT, SCROLL_DOWN, SCROLL_UP,
            PRESS_BACK, PRESS_HOME, WAIT, DONE, SEARCH

            Safety rules:
            - Never uninstall apps.
            - Never open banking or payment apps.
            - Never send SMS or place calls.
            - Never delete files or photos.
            - Never change passwords or PINs.
            - For APK downloads, prefer GitHub, APKMirror, or F-Droid only.

            User command:
            $command

            $contextBlock
            Example:
            [
              {"action":"LAUNCH_APP","target":"chrome","value":"","reason":"Open Chrome browser"},
              {"action":"TAP_TEXT","target":"Search or type URL","value":"","reason":"Focus the address bar"},
              {"action":"INPUT_TEXT","target":"Search or type URL","value":"cats","reason":"Type the search query"},
              {"action":"TAP_TEXT","target":"Go","value":"","reason":"Run the search"},
              {"action":"DONE","target":"","value":"","reason":"Task complete"}
            ]
        """.trimIndent()
    }
}
