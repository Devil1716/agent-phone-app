package com.gemma.agentphone

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gemma.agentphone.agent.AgentRuntimeFactory
import com.gemma.agentphone.agent.AgentOrchestrator
import com.gemma.agentphone.agent.ExecutionTrace
import com.gemma.agentphone.agent.IntentSpec
import com.gemma.agentphone.agent.StepStatus
import com.gemma.agentphone.agent.TraceEntry
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettingsRepository
import com.gemma.agentphone.model.ExecutionHistoryEntry
import com.gemma.agentphone.model.ExecutionHistoryRepository
import com.gemma.agentphone.model.SharedPreferencesKeyValueStore
import java.util.Locale

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
    private lateinit var historyRepository: ExecutionHistoryRepository

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                commandInput.setText(spoken)
                commandInput.setSelection(spoken.length)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        historyRepository = ExecutionHistoryRepository(
            SharedPreferencesKeyValueStore(
                getSharedPreferences("execution_history", MODE_PRIVATE)
            )
        )

        statusText = findViewById(R.id.statusText)
        traceText = findViewById(R.id.traceText)
        commandInput = findViewById(R.id.commandInput)

        findViewById<android.view.View>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.runCommandButton).setOnClickListener {
            runCommand()
        }
        findViewById<android.view.View>(R.id.stopCommandButton).setOnClickListener {
            traceText.text = "Execution canceled by user."
        }
        findViewById<android.view.View>(R.id.voiceInputButton).setOnClickListener {
            launchVoiceInput()
        }
        findViewById<android.view.View>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Handle prefilled command from history
        intent?.getStringExtra("prefill_command")?.let { prefill ->
            commandInput.setText(prefill)
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
        val command = commandInput.text.toString()
        if (command.isBlank()) return

        val trace = runtimeFactory.createCoordinator().run(command)

        // Save to history
        historyRepository.add(
            ExecutionHistoryEntry(
                timestampMs = System.currentTimeMillis(),
                commandText = command,
                category = trace.goal.category.name,
                strategy = trace.strategy.name,
                resultSummary = trace.finalMessage,
                awaitedConfirmation = trace.awaitingConfirmation
            )
        )

        traceText.text = renderTrace(trace)

        if (trace.awaitingConfirmation) {
            showConfirmationDialog(trace)
        } else {
            trace.externalActions.forEach { externalActionLauncher.launch(this, it.spec) }
        }
    }

    private fun showConfirmationDialog(trace: ExecutionTrace) {
        val pendingEntry = trace.entries.firstOrNull { it.status == StepStatus.PENDING_CONFIRMATION }
        val dialog = ConfirmationDialogFragment.newInstance(
            stepDescription = pendingEntry?.description ?: "Continue with this action?",
            stepReason = pendingEntry?.detail ?: ""
        )
        dialog.onConfirm = {
            traceText.append("\n\n✅ User confirmed. Launching external actions.")
            trace.externalActions.forEach { externalActionLauncher.launch(this, it.spec) }
        }
        dialog.onCancel = {
            traceText.append("\n\n❌ User canceled execution.")
        }
        dialog.show(supportFragmentManager, ConfirmationDialogFragment.TAG)
    }

    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command…")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            traceText.text = "Voice input is not available on this device."
        }
    }

    private fun renderTrace(trace: ExecutionTrace): String {
        return buildString {
            appendLine("Goal: ${trace.goal.text}")
            appendLine("Category: ${trace.goal.category}")
            appendLine("Strategy: ${trace.strategy}")
            appendLine()
            trace.entries.forEach { entry ->
                appendLine("${statusEmoji(entry)} [${entry.status}] ${entry.description}")
                appendLine("   Executor: ${entry.executorName}")
                appendLine("   Detail: ${entry.detail}")
                appendLine()
            }
            appendLine(trace.finalMessage)
            if (trace.awaitingConfirmation) {
                appendLine("⏸️ Confirmation required before continuing.")
            }
        }
    }

    private fun statusEmoji(entry: TraceEntry): String {
        return when (entry.status) {
            StepStatus.SUCCESS -> "✅"
            StepStatus.PENDING_CONFIRMATION -> "⏳"
            StepStatus.BLOCKED -> "🚫"
            StepStatus.SKIPPED -> "⏭️"
        }
    }
}
