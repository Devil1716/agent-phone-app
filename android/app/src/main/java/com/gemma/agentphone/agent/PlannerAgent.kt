package com.gemma.agentphone.agent

import com.gemma.agentphone.accessibility.ScreenState
import com.google.gson.Gson
import com.google.gson.JsonParser

class PlannerAgent(
    private val engine: TextGenerationEngine,
    private val gson: Gson = Gson()
) {
    private val playStoreSearchTargets = listOf(
        "Search apps & games",
        "Search for apps & games",
        "Search"
    )

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
        templatePlanFor(command)?.let { return it }
        return requestPlan(buildPrompt(command, null, null, ""))
    }

    suspend fun replan(
        command: String,
        screenState: ScreenState,
        failedStep: AgentStep,
        verifierHint: String
    ): List<AgentStep> {
        templatePlanFor(command, verifierHint)?.let { return adaptTemplateForScreen(it, screenState) }
        return requestPlan(buildPrompt(command, screenState, failedStep, verifierHint))
    }

    private fun templatePlanFor(command: String, verifierHint: String = ""): List<AgentStep>? {
        val normalized = command.trim().lowercase()
        return when {
            mentionsPlayStoreInstall(normalized) -> buildPlayStoreInstallPlan(command, verifierHint)
            else -> null
        }
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

    private fun mentionsPlayStoreInstall(command: String): Boolean {
        val mentionsStore = command.contains("play store") || command.contains("google play") || command.contains("playstore")
        val mentionsInstall = command.contains("download") || command.contains("install")
        return mentionsStore && mentionsInstall
    }

    private fun buildPlayStoreInstallPlan(command: String, verifierHint: String): List<AgentStep> {
        val appName = extractPlayStoreAppName(command)
        val searchTarget = playStoreSearchTargets.joinToString(separator = "|")
        val steps = mutableListOf(
            AgentStep("LAUNCH_APP", "Play Store", "", "Open the Play Store first."),
            AgentStep("SEARCH", searchTarget, appName, "Search Play Store for $appName."),
            AgentStep("TAP_TEXT", appName, "", "Open the $appName app listing."),
            AgentStep("TAP_TEXT", "Install|Update|Pre-register", "", "Start installing $appName from the Play Store."),
            AgentStep("WAIT", "", "", "Wait for the Play Store install state to update.")
        )
        if (verifierHint.contains("open", ignoreCase = true) || verifierHint.contains("installed", ignoreCase = true)) {
            steps += AgentStep("TAP_TEXT", "Open", "", "Open $appName after installation if the button is available.")
        }
        steps += AgentStep("DONE", "", "", "The Play Store flow is complete once install or open state is visible.")
        return steps
    }

    private fun extractPlayStoreAppName(command: String): String {
        val cleaned = command
            .replace(Regex("(?i)^open\\s+(the\\s+)?(google\\s+play|play\\s*store)\\s+(and|then)\\s+"), "")
            .replace(Regex("(?i)(download|install|get)\\s+"), "")
            .replace(Regex("(?i)\\s+(from|on|using)\\s+(the\\s+)?(google\\s+play|play\\s*store)"), "")
            .replace(Regex("(?i)\\s+app\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', ',', '"', '\'')
        return cleaned.ifBlank { "the requested app" }
    }

    private fun adaptTemplateForScreen(
        template: List<AgentStep>,
        screenState: ScreenState
    ): List<AgentStep> {
        val inPlayStore = screenState.packageName.contains("vending", ignoreCase = true)
        val visibleText = screenState.visibleText.joinToString("\n").lowercase()
        return template.filterNot { step ->
            step.normalizedAction == "LAUNCH_APP" && inPlayStore
        }.filterNot { step ->
            step.normalizedAction == "SEARCH" && visibleText.contains("install")
        }
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
            If the user asks to open an app and then do something inside it, the plan must continue inside that app.
            Never stop after only LAUNCH_APP unless the user's whole request was just opening the app.
            For Play Store install requests, include opening Play Store, searching, opening the app listing, tapping Install, waiting for install progress, and only then DONE.

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
