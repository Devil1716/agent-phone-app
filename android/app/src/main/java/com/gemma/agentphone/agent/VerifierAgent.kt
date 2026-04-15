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

        return parseVerification(response) ?: VerificationResult(
            success = true,
            reason = "Gemma returned a non-JSON verification response. Continuing.",
            nextHint = ""
        )
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

        return parseVerification(response) ?: VerificationResult(
            success = true,
            reason = "Gemma returned a non-JSON completion response. Assuming the task is complete.",
            nextHint = ""
        )
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
}
