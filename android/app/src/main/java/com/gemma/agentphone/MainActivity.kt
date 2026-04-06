package com.gemma.agentphone

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gemma.agentphone.agent.AgentOrchestrator
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettings

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val providerRegistry = AiProviderRegistry()
        val orchestrator = AgentOrchestrator(
            settings = AiSettings.defaultGemma(),
            providerRegistry = providerRegistry
        )

        val status = TextView(this).apply {
            text = buildString {
                appendLine("Gemma Agent Phone")
                appendLine()
                appendLine("Phase 1: normal Android phone path")
                appendLine("Primary provider: ${orchestrator.settings.activeProvider}")
                appendLine("Primary model: ${orchestrator.settings.activeModel}")
                appendLine("Fallback provider: ${orchestrator.settings.fallbackProvider}")
                appendLine("Fallback model: ${orchestrator.settings.fallbackModel}")
                appendLine("Autonomy mode: ${orchestrator.settings.autonomyMode}")
                appendLine("Cloud fallback enabled: ${orchestrator.settings.allowCloudFallback}")
                appendLine()
                appendLine("Open-source providers:")
                providerRegistry.listProviders().forEach { provider ->
                    appendLine("- ${provider.displayName}: ${provider.models.joinToString()}")
                }
                appendLine()
                append("Next step: connect voice input, model runtime, and accessibility actions.")
            }
            textSize = 18f
            setPadding(48, 64, 48, 64)
        }

        setContentView(status)
    }
}
