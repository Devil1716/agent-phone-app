package com.gemma.agentphone.agent

import android.net.Uri
import com.gemma.agentphone.model.ModelDownloadState
import kotlinx.coroutines.flow.StateFlow

data class AgentStep(
    val action: String,
    val target: String,
    val value: String,
    val reason: String
) {
    val normalizedAction: String
        get() = action.trim().uppercase()

    fun summary(): String {
        return when {
            reason.isNotBlank() -> reason
            target.isNotBlank() -> "$normalizedAction $target"
            value.isNotBlank() -> "$normalizedAction $value"
            else -> normalizedAction
        }
    }
}

data class AgentLogEntry(
    val title: String,
    val detail: String,
    val success: Boolean? = null,
    val step: AgentStep? = null
)

data class VerificationResult(
    val success: Boolean,
    val reason: String,
    val nextHint: String
)

data class AgentExecutionSummary(
    val success: Boolean,
    val summary: String
)

sealed class AgentStatus {
    data object Idle : AgentStatus()
    data class Downloading(val progressLabel: String) : AgentStatus()
    data class Planning(val command: String) : AgentStatus()
    data class Executing(val step: AgentStep, val stepIndex: Int, val total: Int) : AgentStatus()
    data class Completed(val summary: String) : AgentStatus()
    data class Failed(val reason: String) : AgentStatus()
    data class Stopped(val reason: String) : AgentStatus()
}

interface TextGenerationEngine {
    suspend fun generate(prompt: String): String
}

interface AgentRuntime {
    val status: StateFlow<AgentStatus>
    val logs: StateFlow<List<AgentLogEntry>>
    val downloadState: StateFlow<ModelDownloadState>

    fun isModelReady(): Boolean
    suspend fun execute(command: String)
    suspend fun startModelDownload()
    suspend fun importModel(uri: Uri)
    suspend fun tryQuickImport(): Boolean
    fun cancelExecution()
}
