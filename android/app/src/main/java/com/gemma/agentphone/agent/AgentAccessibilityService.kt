package com.gemma.agentphone.agent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AgentAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service will become the observation and action bridge for the agent.
        // Phase 1 only establishes the entry point on normal Android devices.
    }

    override fun onInterrupt() {
        // Required by the framework.
    }
}
