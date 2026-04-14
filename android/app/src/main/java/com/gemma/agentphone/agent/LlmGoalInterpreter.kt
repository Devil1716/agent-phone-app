package com.gemma.agentphone.agent

import com.gemma.agentphone.model.AiProvider
import com.gemma.agentphone.model.AiRequest
import com.gemma.agentphone.model.GoalCategory
import com.gemma.agentphone.model.UserGoal
import org.json.JSONObject

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
            
            Reply ONLY with a strictly formatted JSON object. It MUST contain "category", "target", and "payload". No markdown formatting or extra text.
            
            Example A:
            {"category": "GENERAL_APP_CONTROL", "target": "", "payload": "$input"}
            
            Example B:
            {"category": "WEB_SEARCH", "target": "browser", "payload": "search query here"}
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
            val jsonObject = JSONObject(jsonText)
            
            val categoryStr = jsonObject.optString("category", "GENERAL_APP_CONTROL")
            val category = runCatching { GoalCategory.valueOf(categoryStr) }.getOrDefault(GoalCategory.GENERAL_APP_CONTROL)
            
            UserGoal(
                text = input,
                category = category,
                targetApp = jsonObject.optString("target", "").ifBlank { null },
                targetValue = jsonObject.optString("payload", input).ifBlank { input },
                requiresFastPath = false
            )
        } catch (e: Exception) {
            // Fall back to simple parsing if the local LLM fails formatting
            fallback.interpret(input)
        }
    }
}
