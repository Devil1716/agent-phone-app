package com.gemma.agentphone.agent

class DefaultObservationService : ObservationService {
    override fun capture(): ScreenObservation {
        return AgentAccessibilityService.latestObservation() ?: ScreenObservation(
            foregroundApp = "com.gemma.agentphone",
            visibleText = emptyList(),
            timestampMs = System.currentTimeMillis()
        )
    }
}
