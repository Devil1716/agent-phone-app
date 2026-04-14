package com.gemma.agentphone

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gemma.agentphone.agent.AgentRuntimeFactory
import com.gemma.agentphone.agent.AgentOrchestrator
import com.gemma.agentphone.agent.ExecutionTrace
import com.gemma.agentphone.agent.ModelDownloadManager
import com.gemma.agentphone.agent.ModelDownloadStatus
import com.gemma.agentphone.agent.StepStatus
import com.gemma.agentphone.agent.TraceEntry
import com.gemma.agentphone.agent.UpdateManager
import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettingsRepository
import com.gemma.agentphone.model.ExecutionHistoryEntry
import com.gemma.agentphone.model.ExecutionHistoryRepository
import com.gemma.agentphone.model.SharedPreferencesKeyValueStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var modelDownloadManager: ModelDownloadManager
    private val modelStatusHandler = Handler(Looper.getMainLooper())

    private val modelStatusRunnable = object : Runnable {
        override fun run() {
            val isStillDownloading = refreshModelStatus()
            if (isStillDownloading) {
                modelStatusHandler.postDelayed(this, 1_000)
            }
        }
    }

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

    private val importModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = modelDownloadManager.importModel(uri)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, R.string.model_import_success, Toast.LENGTH_SHORT).show()
                    refreshModelStatus()
                } else {
                    Toast.makeText(this@MainActivity, R.string.model_import_failure, Toast.LENGTH_SHORT).show()
                }
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
        modelDownloadManager = ModelDownloadManager(this)

        statusText = findViewById(R.id.statusText)
        traceText = findViewById(R.id.traceText)
        commandInput = findViewById(R.id.commandInput)

        findViewById<android.view.View>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.runCommandButton).setOnClickListener {
            runCommand()
        }
        findViewById<android.view.View>(R.id.voiceInputButton).setOnClickListener {
            launchVoiceInput()
        }
        findViewById<android.view.View>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        applyPrefillCommand(intent)
    }

    override fun onResume() {
        super.onResume()
        val settings = AiSettingsRepository(this).load()
        val orchestrator = AgentOrchestrator(
            settings = settings,
            providerRegistry = providerRegistry
        )

        statusText.text = "Manus-style Controller Active"
        if (traceText.text.isNullOrBlank()) {
            traceText.text = "Standby..."
        }
        refreshModelStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPrefillCommand(intent)
    }

    override fun onPause() {
        super.onPause()
        modelStatusHandler.removeCallbacks(modelStatusRunnable)
    }

    private fun runCommand() {
        val command = commandInput.text.toString()
        if (command.isBlank()) return

        val runCommandButton = findViewById<android.view.View>(R.id.runCommandButton)
        val thinkingOverlay = findViewById<android.view.View>(R.id.thinkingOverlay)
        val liveThoughtText = findViewById<TextView>(R.id.liveThoughtText)
        val liveActionText = findViewById<TextView>(R.id.liveActionText)

        runCommandButton.isEnabled = false
        thinkingOverlay.visibility = android.view.View.VISIBLE
        liveThoughtText.text = "Analyzing request..."
        liveActionText.text = ""

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val settings = AiSettingsRepository(this@MainActivity).load()
                val trace = runtimeFactory.createCoordinator(this@MainActivity, settings, providerRegistry).run(command) { progress ->
                    runOnUiThread {
                        liveThoughtText.text = progress.thought ?: progress.description
                        liveActionText.text = "ACTION: ${progress.executorName}"
                    }
                }

                withContext(Dispatchers.Main) {
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
                    thinkingOverlay.visibility = android.view.View.GONE

                    if (trace.awaitingConfirmation) {
                        showConfirmationDialog(trace)
                    } else {
                        trace.externalActions.forEach { externalActionLauncher.launch(this@MainActivity, it.spec) }
                    }
                    runCommandButton.isEnabled = true
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    thinkingOverlay.visibility = android.view.View.GONE
                    traceText.text =
                        "Error running the agent: ${exception.localizedMessage ?: "Unknown error"}\n\n" +
                        "Check that the local model is ready or that your fallback provider is configured."
                    runCommandButton.isEnabled = true
                }
            }
        }
    }

    private fun startModelDownload() {
        val settings = AiSettingsRepository(this).load()
        val result = modelDownloadManager.startDownload(
            downloadUrl = settings.modelDownloadUrl,
            huggingFaceToken = settings.huggingFaceToken
        )
        if (result.isSuccess) {
            Toast.makeText(this, R.string.model_download_started, Toast.LENGTH_SHORT).show()
            refreshModelStatus()
        } else {
            val messageRes = if (settings.modelDownloadUrl.isBlank()) {
                R.string.model_download_missing_url
            } else {
                R.string.model_download_failure
            }
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshModelStatus(): Boolean {
        val modelStatusTextView = findViewById<TextView>(R.id.modelStatusText)

        return when (val status = modelDownloadManager.getStatus()) {
            is ModelDownloadStatus.Ready -> {
                modelStatusTextView.text = "Gemma Model Active"
                false
            }

            is ModelDownloadStatus.Downloading -> {
                modelStatusTextView.text = "Downloading Model (${status.progress}%)"
                modelStatusHandler.removeCallbacks(modelStatusRunnable)
                modelStatusHandler.postDelayed(modelStatusRunnable, 1_000)
                true
            }

            is ModelDownloadStatus.Failed -> {
                modelStatusTextView.text = "Model Sync Error"
                false
            }

            ModelDownloadStatus.Missing -> {
                modelStatusTextView.text = "Model Missing"
                false
            }
        }
    }

    private fun checkForUpdates() {
        val updateManager = UpdateManager()
        updateManager.checkForUpdates { version, url ->
            runOnUiThread {
                val updateCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.updateCard)
                val updateText = findViewById<TextView>(R.id.updateText)
                val downloadButton = findViewById<MaterialButton>(R.id.downloadUpdateButton)

                updateText.text = getString(R.string.update_available, version)
                downloadButton.text = getString(R.string.update_action)
                updateCard.visibility = android.view.View.VISIBLE
                downloadButton.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            }
        }
    }

    private fun showConfirmationDialog(trace: ExecutionTrace) {
        val pendingEntry = trace.entries.firstOrNull { it.status == StepStatus.PENDING_CONFIRMATION }
        val dialog = ConfirmationDialogFragment.newInstance(
            stepDescription = pendingEntry?.description ?: "Continue with this action?",
            stepReason = pendingEntry?.detail ?: ""
        )
        dialog.onConfirm = {
            traceText.append("\n\n[confirmed] User approved the pending action.")
            trace.externalActions.forEach { externalActionLauncher.launch(this, it.spec) }
        }
        dialog.onCancel = {
            traceText.append("\n\n[canceled] User canceled execution.")
        }
        dialog.show(supportFragmentManager, ConfirmationDialogFragment.TAG)
    }

    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            traceText.text = "Voice input is not available on this device."
        }
    }

    private fun applyPrefillCommand(intent: Intent?) {
        intent?.getStringExtra("prefill_command")?.let { prefill ->
            commandInput.setText(prefill)
            commandInput.setSelection(prefill.length)
        }
    }

    private fun renderTrace(trace: ExecutionTrace): String {
        return buildString {
            appendLine("Goal: ${trace.goal.text}")
            appendLine("Category: ${trace.goal.category}")
            appendLine("Strategy: ${trace.strategy}")
            appendLine()
            trace.entries.forEach { entry ->
                appendLine("${statusPrefix(entry)} [${entry.status}] ${entry.description}")
                appendLine("   Executor: ${entry.executorName}")
                if (!entry.thought.isNullOrBlank()) {
                    appendLine("   Thought: ${entry.thought}")
                }
                appendLine("   Detail: ${entry.detail}")
                appendLine()
            }
            appendLine(trace.finalMessage)
            if (trace.awaitingConfirmation) {
                appendLine("[pause] Confirmation required before continuing.")
            }
        }
    }

    private fun statusPrefix(entry: TraceEntry): String {
        return when (entry.status) {
            StepStatus.SUCCESS -> "[ok]"
            StepStatus.PENDING_CONFIRMATION -> "[wait]"
            StepStatus.BLOCKED -> "[blocked]"
            StepStatus.SKIPPED -> "[skip]"
        }
    }
}
