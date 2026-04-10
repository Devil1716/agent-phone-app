package com.gemma.agentphone.model

import org.json.JSONArray
import org.json.JSONObject

data class ExecutionHistoryEntry(
    val timestampMs: Long,
    val commandText: String,
    val category: String,
    val strategy: String,
    val resultSummary: String,
    val awaitedConfirmation: Boolean
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("timestampMs", timestampMs)
            put("commandText", commandText)
            put("category", category)
            put("strategy", strategy)
            put("resultSummary", resultSummary)
            put("awaitedConfirmation", awaitedConfirmation)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ExecutionHistoryEntry {
            return ExecutionHistoryEntry(
                timestampMs = json.optLong("timestampMs", 0L),
                commandText = json.optString("commandText", ""),
                category = json.optString("category", ""),
                strategy = json.optString("strategy", ""),
                resultSummary = json.optString("resultSummary", ""),
                awaitedConfirmation = json.optBoolean("awaitedConfirmation", false)
            )
        }
    }
}

class ExecutionHistoryRepository(private val keyValueStore: KeyValueStore) {
    companion object {
        private const val KEY = "execution_history"
        private const val MAX_ENTRIES = 50
    }

    fun loadAll(): List<ExecutionHistoryEntry> {
        val raw = keyValueStore.getString(KEY, "[]")
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { ExecutionHistoryEntry.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(entry: ExecutionHistoryEntry) {
        val existing = loadAll().toMutableList()
        existing.add(0, entry)
        if (existing.size > MAX_ENTRIES) {
            existing.removeAt(existing.lastIndex)
        }
        val array = JSONArray()
        existing.forEach { array.put(it.toJson()) }
        keyValueStore.putString(KEY, array.toString())
    }

    fun clear() {
        keyValueStore.putString(KEY, "[]")
    }
}
