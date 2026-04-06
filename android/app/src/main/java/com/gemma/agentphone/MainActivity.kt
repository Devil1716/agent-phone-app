package com.gemma.agentphone

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gemma.agentphone.agent.AgentOrchestrator
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettingsRepository

class MainActivity : AppCompatActivity() {
    private val providerRegistry = AiProviderRegistry()
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val settings = AiSettingsRepository(this).load()
        val orchestrator = AgentOrchestrator(
            settings = settings,
            providerRegistry = providerRegistry
        )

        statusText.text = buildString {
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
    }
}
