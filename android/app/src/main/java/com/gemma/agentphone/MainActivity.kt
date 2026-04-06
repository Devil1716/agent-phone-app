package com.gemma.agentphone

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gemma.agentphone.agent.AgentRuntimeFactory
import com.gemma.agentphone.agent.AgentOrchestrator
import com.gemma.agentphone.agent.ExecutionTrace
import com.gemma.agentphone.agent.IntentSpec
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettingsRepository

class MainActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        var externalActionLauncher: ExternalActionLauncher = DefaultExternalActionLauncher
    }

    private val providerRegistry = AiProviderRegistry()
    private val runtimeFactory = AgentRuntimeFactory()
    private lateinit var statusText: TextView
    private lateinit var traceText: TextView
    private lateinit var commandInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        traceText = findViewById(R.id.traceText)
        commandInput = findViewById(R.id.commandInput)
        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.runCommandButton).setOnClickListener {
            runCommand()
        }
        findViewById<Button>(R.id.stopCommandButton).setOnClickListener {
            traceText.text = "Execution canceled by user."
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
            appendLine("Professional alpha track: Android 12-14 app-control agent")
            appendLine()
            orchestrator.summaryLines().forEach(::appendLine)
            appendLine()
            appendLine("Available providers:")
            providerRegistry.listProviders().forEach { provider ->
                appendLine("- ${provider.displayName}: ${provider.models.joinToString()}")
            }
        }
        if (traceText.text.isNullOrBlank()) {
            traceText.text = getString(R.string.trace_idle)
        }
    }

    private fun runCommand() {
        val trace = runtimeFactory.createCoordinator().run(commandInput.text.toString())
        traceText.text = renderTrace(trace)
        trace.externalActions.forEach { externalActionLauncher.launch(this, it.spec) }
    }

    private fun renderTrace(trace: ExecutionTrace): String {
        return buildString {
            appendLine("Goal: ${trace.goal.text}")
            appendLine("Category: ${trace.goal.category}")
            appendLine("Strategy: ${trace.strategy}")
            appendLine()
            trace.entries.forEach { entry ->
                appendLine("[${entry.status}] ${entry.description}")
                appendLine("Executor: ${entry.executorName}")
                appendLine("Detail: ${entry.detail}")
                appendLine()
            }
            appendLine(trace.finalMessage)
            if (trace.awaitingConfirmation) {
                appendLine("Confirmation required before continuing.")
            }
        }
    }

}
