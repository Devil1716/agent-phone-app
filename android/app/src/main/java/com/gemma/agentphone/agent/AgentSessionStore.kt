package com.gemma.agentphone.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AgentSessionStore {
    private val _status = MutableStateFlow<AgentStatus>(AgentStatus.Idle)
    private val _logs = MutableStateFlow<List<AgentLogEntry>>(emptyList())

    val status: StateFlow<AgentStatus> = _status.asStateFlow()
    val logs: StateFlow<List<AgentLogEntry>> = _logs.asStateFlow()

    fun begin(command: String) {
        _logs.value = emptyList()
        _status.value = AgentStatus.Planning(command)
    }

    fun updateStatus(status: AgentStatus) {
        _status.value = status
    }

    fun appendLog(entry: AgentLogEntry) {
        _logs.update { current -> current + entry }
    }

    fun clear() {
        _logs.value = emptyList()
        _status.value = AgentStatus.Idle
    }
}
