package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal

class LlmGoalInterpreter(
    private val aiProvider: AiProvider?,
    private val fallback: GoalInterpreter = RuleBasedGoalInterpreter()
) : GoalInterpreter {

    override fun interpret(input: String): UserGoal {
        if (aiProvider == null) {
            return fallback.interpret(input)
        }

        val prompt = """
            You are an orchestration AI for an Android agent.
            Analyze the user command: "$input"
            
            Decide if this represents a simple OS intent (like DRAFT_MESSAGE, WEB_SEARCH, OPEN_SETTINGS, PLACE_CALL) or a complex task requiring multi-step autonomous screen control (GENERAL_APP_CONTROL).
            If the user asks to "open an app and do something" (like "open playstore and see latest game"), it is ALWAYS GENERAL_APP_CONTROL.
            If the request clearly names an app or service where the work should happen, set targetApp and set shouldOpenAppFirst to true.
            The understanding field must be a short user-visible sentence that explains what the agent believes the user wants.
            
            Reply ONLY with a strictly formatted JSON object. It MUST contain "category", "targetApp", "payload", "understanding", and "shouldOpenAppFirst". No markdown formatting or extra text.
            
            Example A:
            {"category": "GENERAL_APP_CONTROL", "targetApp": "Play Store", "payload": "$input", "understanding": "Open the Play Store and continue the requested flow there.", "shouldOpenAppFirst": true}
            
            Example B:
            {"category": "WEB_SEARCH", "targetApp": "browser", "payload": "search query here", "understanding": "Search the web for the requested topic.", "shouldOpenAppFirst": false}
        """.trimIndent()

        return try {
            val request = AiRequest(prompt = prompt, mode = "orchestrator", targetCategory = GoalCategory.GENERAL_APP_CONTROL)
            val response = aiProvider.infer(request)
            
            // Defensively extract JSON
            val rawText = response.summary
            val jsonStart = rawText.indexOf("{")
            val jsonEnd = rawText.lastIndexOf("}")
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd < jsonStart) {
                return fallback.interpret(input)
            }
            
            val jsonText = rawText.substring(jsonStart, jsonEnd + 1)
            val categoryStr = extractJsonString(jsonText, "category") ?: "GENERAL_APP_CONTROL"
            val category = runCatching { GoalCategory.valueOf(categoryStr) }.getOrDefault(GoalCategory.GENERAL_APP_CONTROL)
            val targetApp = extractJsonString(jsonText, "targetApp")
                ?: extractJsonString(jsonText, "target")
            val payload = extractJsonString(jsonText, "payload").orEmpty().ifBlank { input }
            val understanding = extractJsonString(jsonText, "understanding")
                .orEmpty()
                .ifBlank { buildFallbackUnderstanding(category, targetApp, payload) }
            val shouldOpenAppFirst = when (val booleanField = extractJsonBoolean(jsonText, "shouldOpenAppFirst")) {
                null -> category == GoalCategory.GENERAL_APP_CONTROL && !targetApp.isNullOrBlank()
                else -> booleanField
            }

            UserGoal(
                text = input,
                category = category,
                targetApp = targetApp,
                targetValue = payload,
                requiresFastPath = false,
                understanding = understanding,
                shouldOpenAppFirst = shouldOpenAppFirst
            )
        } catch (e: Exception) {
            // Fall back to simple parsing if the local LLM fails formatting
            fallback.interpret(input)
        }
    }

    private fun extractJsonString(jsonText: String, field: String): String? {
        val regex = Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(jsonText)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractJsonBoolean(jsonText: String, field: String): Boolean? {
        val regex = Regex("\"$field\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        return when (regex.find(jsonText)?.groupValues?.getOrNull(1)?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun buildFallbackUnderstanding(
        category: GoalCategory,
        targetApp: String?,
        payload: String
    ): String {
        return when (category) {
            GoalCategory.OPEN_SETTINGS -> "Open the requested Android settings screen."
            GoalCategory.DRAFT_MESSAGE -> "Prepare a message draft in ${targetApp ?: "the messaging app"}."
            GoalCategory.PLACE_CALL -> "Prepare a phone call and wait for confirmation."
            GoalCategory.WEB_SEARCH -> "Search the web for \"$payload\"."
            GoalCategory.OPEN_MAPS -> "Open maps for \"$payload\"."
            GoalCategory.PLAY_MEDIA -> "Search for media playback for \"$payload\"."
            GoalCategory.SUMMARIZE_NOTIFICATIONS -> "Summarize current notifications."
            GoalCategory.GENERAL_APP_CONTROL -> {
                if (targetApp.isNullOrBlank()) {
                    "Understand the request from the live screen and work through it safely."
                } else {
                    "Open $targetApp and continue the requested flow there."
                }
            }
            GoalCategory.UNSUPPORTED -> "The request is not safely automatable yet."
        }
    }
}
