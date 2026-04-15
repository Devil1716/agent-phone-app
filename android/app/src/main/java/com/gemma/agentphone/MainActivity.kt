package com.gemma.agentphone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gemma.agentphone.accessibility.PhoneControlService
import com.gemma.agentphone.agent.AgentOrchestrator
import com.gemma.agentphone.agent.AgentRuntime
import com.gemma.agentphone.agent.AgentStatus
import com.gemma.agentphone.ui.AgentLogAdapter
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        var externalActionLauncher: ExternalActionLauncher = DefaultExternalActionLauncher

        @JvmStatic
        var runtimeFactoryOverride: ((android.content.Context) -> AgentRuntime)? = null
    }

    private val agentRuntime: AgentRuntime by lazy {
        runtimeFactoryOverride?.invoke(this) ?: AgentOrchestrator(this)
    }

    private lateinit var statusText: TextView
    private lateinit var traceText: TextView
    private lateinit var commandInput: EditText
    private lateinit var modelStatusText: TextView
    private lateinit var modelDownloadCard: View
    private lateinit var modelDownloadProgress: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var modelDownloadPercent: TextView
    private lateinit var modelDownloadBytes: TextView
    private lateinit var runCommandButton: View
    private lateinit var stopAgentButton: View
    private lateinit var logAdapter: AgentLogAdapter
    private lateinit var logRecycler: RecyclerView
    private lateinit var stepCountText: TextView

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
        if (uri != null) {
            lifecycleScope.launch { agentRuntime.importModel(uri) }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        traceText = findViewById(R.id.traceText)
        commandInput = findViewById(R.id.commandInput)
        modelStatusText = findViewById(R.id.modelStatusText)
        modelDownloadCard = findViewById(R.id.modelDownloadCardMain)
        modelDownloadProgress = findViewById(R.id.modelDownloadProgressBar)
        modelDownloadPercent = findViewById(R.id.modelDownloadPercent)
        modelDownloadBytes = findViewById(R.id.modelDownloadBytes)
        runCommandButton = findViewById(R.id.runCommandButton)
        stopAgentButton = findViewById(R.id.stopAgentButton)
        logRecycler = findViewById(R.id.cotRecycler)
        stepCountText = findViewById(R.id.cotStepCount)

        logAdapter = AgentLogAdapter()
        logRecycler.layoutManager = LinearLayoutManager(this)
        logRecycler.adapter = logAdapter

        runCommandButton.setOnClickListener { runCommand() }
        stopAgentButton.setOnClickListener { agentRuntime.cancelExecution() }
        findViewById<View>(R.id.voiceInputButton).setOnClickListener { launchVoiceInput() }
        findViewById<View>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.downloadModelButtonMain).setOnClickListener {
            lifecycleScope.launch { agentRuntime.startModelDownload() }
        }
        findViewById<View>(R.id.importModelButtonMain).setOnClickListener {
            importModelLauncher.launch(arrayOf("*/*"))
        }
        modelStatusText.setOnClickListener {
            if (!agentRuntime.isModelReady()) {
                lifecycleScope.launch { agentRuntime.startModelDownload() }
            }
        }
        modelStatusText.setOnLongClickListener {
            importModelLauncher.launch(arrayOf("*/*"))
            true
        }

        requestStartupPermissions()
        observeRuntime()
    }

    override fun onResume() {
        super.onResume()
        if (!PhoneControlService.isEnabled(this)) {
            Toast.makeText(
                this,
                "Enable the Atlas accessibility service to allow autonomous control.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun observeRuntime() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    agentRuntime.status.collect { status -> renderStatus(status) }
                }
                launch {
                    agentRuntime.logs.collect { entries ->
                        logAdapter.submit(entries)
                        stepCountText.text = "${entries.size} steps"
                        if (entries.isEmpty()) {
                            traceText.text = getString(R.string.agent_log_empty)
                        } else {
                            traceText.text = entries.last().detail
                            logRecycler.scrollToPosition(entries.lastIndex)
                        }
                    }
                }
                launch {
                    agentRuntime.downloadState.collect { state ->
                        val totalMb = state.totalBytes / 1024f / 1024f
                        val downloadedMb = state.downloadedBytes / 1024f / 1024f
                        modelStatusText.text = when {
                            state.isReady -> "Atlas Brain - Ready"
                            state.isVerifying -> getString(R.string.model_verifying)
                            state.isDownloading -> state.message.ifBlank { "Atlas Brain - Downloading" }
                            state.message.isNotBlank() -> state.message
                            else -> "Atlas Brain - Tap to Download"
                        }
                        modelDownloadCard.visibility = if (state.isReady) View.GONE else View.VISIBLE
                        modelDownloadProgress.progress = state.progressPercent
                        modelDownloadPercent.text = getString(R.string.model_download_percent, state.progressPercent)
                        modelDownloadBytes.text = getString(
                            R.string.model_download_mb,
                            downloadedMb,
                            totalMb
                        )
                    }
                }
            }
        }
    }

    private fun runCommand() {
        val command = commandInput.text.toString().trim()
        if (command.isBlank()) {
            return
        }
        lifecycleScope.launch {
            agentRuntime.execute(command)
        }
    }

    private fun renderStatus(status: AgentStatus) {
        when (status) {
            AgentStatus.Idle -> {
                statusText.text = getString(R.string.agent_status_idle)
                runCommandButton.isEnabled = true
                stopAgentButton.visibility = View.GONE
            }

            is AgentStatus.Downloading -> {
                statusText.text = status.progressLabel
                runCommandButton.isEnabled = false
                stopAgentButton.visibility = View.GONE
            }

            is AgentStatus.Planning -> {
                statusText.text = getString(R.string.agent_status_planning)
                runCommandButton.isEnabled = false
                stopAgentButton.visibility = View.VISIBLE
            }

            is AgentStatus.Executing -> {
                statusText.text = "Step ${status.stepIndex}/${status.total}: ${status.step.summary()}"
                runCommandButton.isEnabled = false
                stopAgentButton.visibility = View.VISIBLE
            }

            is AgentStatus.Completed -> {
                statusText.text = "${getString(R.string.agent_status_done)} OK"
                traceText.text = status.summary
                runCommandButton.isEnabled = true
                stopAgentButton.visibility = View.GONE
            }

            is AgentStatus.Failed -> {
                statusText.text = getString(R.string.agent_status_failed)
                traceText.text = status.reason
                runCommandButton.isEnabled = true
                stopAgentButton.visibility = View.GONE
            }

            is AgentStatus.Stopped -> {
                statusText.text = status.reason
                runCommandButton.isEnabled = true
                stopAgentButton.visibility = View.GONE
            }
        }
    }

    private fun requestStartupPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= 28) {
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
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
}
