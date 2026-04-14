package com.gemma.agentphone

import android.app.Activity
import android.content.Intent

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gemma.agentphone.agent.AgentRuntimeFactory

import com.gemma.agentphone.agent.ExecutionTrace
import com.gemma.agentphone.agent.ModelDownloadManager
import com.gemma.agentphone.agent.ModelDownloadStatus
import com.gemma.agentphone.agent.StepStatus
import com.gemma.agentphone.agent.TraceEntry

import com.gemma.agentphone.model.AiProviderRegistry
import com.gemma.agentphone.model.AiSettingsRepository
import com.gemma.agentphone.model.ExecutionHistoryEntry
import com.gemma.agentphone.model.ExecutionHistoryRepository
import com.gemma.agentphone.model.SharedPreferencesKeyValueStore

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
    private lateinit var commandInput: EditText
    private lateinit var historyRepository: ExecutionHistoryRepository
    private lateinit var modelDownloadManager: ModelDownloadManager
    private lateinit var cotAdapter: CotStepAdapter
    private lateinit var cotRecycler: RecyclerView
    private lateinit var cotStepCount: TextView
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
        commandInput = findViewById(R.id.commandInput)
        cotStepCount = findViewById(R.id.cotStepCount)

        // Set up the Chain of Thought RecyclerView (the mini-window)
        cotRecycler = findViewById(R.id.cotRecycler)
        cotAdapter = CotStepAdapter()
        cotRecycler.layoutManager = LinearLayoutManager(this)
        cotRecycler.adapter = cotAdapter

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
        statusText.text = "Active"
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
        val cotEmptyState = findViewById<android.view.View>(R.id.cotEmptyState)

        runCommandButton.isEnabled = false
        thinkingOverlay.visibility = android.view.View.VISIBLE
        liveThoughtText.text = "Analyzing request..."
        liveActionText.text = ""

        // Clear previous CoT steps and hide empty state
        cotAdapter.clear()
        cotEmptyState.visibility = android.view.View.GONE
        cotRecycler.visibility = android.view.View.VISIBLE
        cotStepCount.text = "0 steps"

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val settings = AiSettingsRepository(this@MainActivity).load()
                val trace = runtimeFactory.createCoordinator(this@MainActivity, settings, providerRegistry).run(command) { progress ->
                    runOnUiThread {
                        liveThoughtText.text = progress.thought ?: progress.description
                        liveActionText.text = "ACTION: ${progress.executorName}"

                        // Add the step to the CoT mini-window in real time
                        cotAdapter.addStep(progress)
                        cotStepCount.text = "${cotAdapter.stepCount()} steps"
                        cotRecycler.smoothScrollToPosition(cotAdapter.stepCount() - 1)
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

                    // Show the final trace in the CoT panel
                    if (cotAdapter.stepCount() == 0) {
                        cotAdapter.submitAll(trace.entries)
                        cotStepCount.text = "${trace.entries.size} steps"
                    }

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

                    // Show the error as a "step" in the CoT panel
                    val errorEntry = TraceEntry(
                        stepId = "error",
                        description = "Agent execution failed",
                        status = StepStatus.BLOCKED,
                        executorName = "Runtime",
                        thought = exception.localizedMessage ?: "Unknown error",
                        detail = "Check that the local model is ready or that your fallback provider is configured."
                    )
                    cotAdapter.addStep(errorEntry)
                    cotStepCount.text = "${cotAdapter.stepCount()} steps"
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
                modelStatusTextView.text = "Gemma 3 · Ready"
                false
            }

            is ModelDownloadStatus.Downloading -> {
                modelStatusTextView.text = "Downloading · ${status.progress}%"
                modelStatusHandler.removeCallbacks(modelStatusRunnable)
                modelStatusHandler.postDelayed(modelStatusRunnable, 1_000)
                true
            }

            is ModelDownloadStatus.Failed -> {
                modelStatusTextView.text = "Model · Error"
                false
            }

            ModelDownloadStatus.Missing -> {
                modelStatusTextView.text = "Model · Not Found"
                false
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
            cotAdapter.addStep(TraceEntry(
                stepId = "confirmed",
                description = "User approved the pending action",
                status = StepStatus.SUCCESS,
                executorName = "User",
                detail = "Confirmed"
            ))
            trace.externalActions.forEach { externalActionLauncher.launch(this, it.spec) }
        }
        dialog.onCancel = {
            cotAdapter.addStep(TraceEntry(
                stepId = "canceled",
                description = "User canceled execution",
                status = StepStatus.SKIPPED,
                executorName = "User",
                detail = "Canceled"
            ))
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
            Toast.makeText(this, "Voice input is not available on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPrefillCommand(intent: Intent?) {
        intent?.getStringExtra("prefill_command")?.let { prefill ->
            commandInput.setText(prefill)
            commandInput.setSelection(prefill.length)
        }
    }
}
