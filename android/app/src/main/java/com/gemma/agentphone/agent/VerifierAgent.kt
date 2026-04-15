package com.gemma.agentphone.agent

import com.gemma.agentphone.accessibility.ScreenState
import com.google.gson.JsonParser

class VerifierAgent(
    private val engine: TextGenerationEngine
) {
    suspend fun verify(screenState: ScreenState, lastAction: AgentStep): VerificationResult {
        if (lastAction.normalizedAction == "WAIT" || lastAction.normalizedAction == "DONE") {
            return VerificationResult(true, "No verification needed.", "")
        }

        val response = engine.generate(
            """
            Did this action succeed?
            Screen now shows:
            ${screenState.visibleText.joinToString(separator = "\n")}

            Current package: ${screenState.packageName}
            Current activity: ${screenState.activityName}

            Last action was:
            ${lastAction.summary()}

            Reply ONLY with JSON:
            {"success": bool, "reason": string, "next_hint": string}
            """.trimIndent()
        )

        return parseVerification(response) ?: fallbackStepVerification(screenState, lastAction)
    }

    suspend fun confirmCompletion(command: String, screenState: ScreenState): VerificationResult {
        val response = engine.generate(
            """
            Decide whether the user command has been fully completed.
            User command:
            $command

            Screen now shows:
            ${screenState.visibleText.joinToString(separator = "\n")}

            Current package: ${screenState.packageName}
            Current activity: ${screenState.activityName}

            Reply ONLY with JSON:
            {"success": bool, "reason": string, "next_hint": string}
            Use success=false if the task seems incomplete or still needs another step.
            """.trimIndent()
        )

        return parseVerification(response) ?: fallbackCompletionVerification(command, screenState)
    }

    private fun parseVerification(raw: String): VerificationResult? {
        return runCatching {
            val jsonStart = raw.indexOf('{')
            val jsonEnd = raw.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd < jsonStart) {
                return null
            }
            val json = JsonParser.parseString(raw.substring(jsonStart, jsonEnd + 1)).asJsonObject
            VerificationResult(
                success = json.get("success")?.asBoolean ?: true,
                reason = json.get("reason")?.asString.orEmpty(),
                nextHint = json.get("next_hint")?.asString.orEmpty()
            )
        }.getOrNull()
    }

    private fun fallbackStepVerification(
        screenState: ScreenState,
        lastAction: AgentStep
    ): VerificationResult {
        val visibleText = screenState.visibleText.joinToString("\n").lowercase()
        return when (lastAction.normalizedAction) {
            "LAUNCH_APP" -> {
                val target = lastAction.target.lowercase()
                val launched = screenState.packageName.contains(target) ||
                    visibleText.contains(target) ||
                    (target.contains("play store") && screenState.packageName.contains("vending"))
                VerificationResult(
                    success = launched,
                    reason = if (launched) {
                        "The requested app appears to be open."
                    } else {
                        "The target app does not appear to be open yet."
                    },
                    nextHint = if (launched) "" else "Launch the requested app and verify the screen changed."
                )
            }

            else -> VerificationResult(
                success = false,
                reason = "Gemma did not return valid verification JSON.",
                nextHint = "Inspect the live screen and continue the task from what is visible."
            )
        }
    }

    private fun fallbackCompletionVerification(
        command: String,
        screenState: ScreenState
    ): VerificationResult {
        val normalizedCommand = command.lowercase()
        val visibleText = screenState.visibleText.map { it.lowercase() }
        if (mentionsPlayStoreInstall(normalizedCommand)) {
            val looksInstalled = visibleText.any { it == "installed" } ||
                (visibleText.any { it == "open" } && visibleText.any { it == "uninstall" })
            return VerificationResult(
                success = looksInstalled,
                reason = if (looksInstalled) {
                    "The Play Store shows the installed/open state."
                } else {
                    "The Play Store does not show the installed/open state yet."
                },
                nextHint = if (looksInstalled) "" else "Stay in the Play Store flow until Install changes to Open or Installed."
            )
        }
        return VerificationResult(
            success = false,
            reason = "Gemma did not return valid completion JSON.",
            nextHint = "Continue until the result is visibly complete on screen."
        )
    }

    private fun mentionsPlayStoreInstall(command: String): Boolean {
        val mentionsStore = command.contains("play store") || command.contains("google play") || command.contains("playstore")
        val mentionsInstall = command.contains("download") || command.contains("install")
        return mentionsStore && mentionsInstall
    }
}
