package com.gemma.agentphone.agent

class DefaultPolicyEngine : PolicyEngine {
    override fun classify(step: TaskStep): PolicyDecision {
        return when (step.riskLevel) {
            RiskLevel.LOW -> PolicyDecision(PolicyAction.ALLOW, "Low-risk action")
            RiskLevel.CONFIRM_REQUIRED -> PolicyDecision(PolicyAction.REQUIRE_CONFIRMATION, "User confirmation required")
            RiskLevel.BLOCKED -> PolicyDecision(PolicyAction.BLOCK, "Blocked until a safer supported path exists")
        }
    }
}
