package com.gemma.agentphone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.gemma.agentphone.accessibility.PhoneControlService
import com.gemma.agentphone.agent.AgentOrchestrator
import com.gemma.agentphone.agent.AgentRuntime
import com.gemma.agentphone.agent.AgentStatus
import com.gemma.agentphone.ui.AtlasDashboardApp
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        @JvmStatic
        var externalActionLauncher: ExternalActionLauncher = DefaultExternalActionLauncher

        @JvmStatic
        var runtimeFactoryOverride: ((android.content.Context) -> AgentRuntime)? = null
    }

    private val agentRuntime: AgentRuntime by lazy {
        runtimeFactoryOverride?.invoke(this) ?: AgentOrchestrator(this)
    }

    private var commandText by mutableStateOf("")
    private var accessibilityEnabled by mutableStateOf(false)

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                commandText = spoken
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
        enableEdgeToEdge()
        accessibilityEnabled = PhoneControlService.isEnabled(this)
        requestStartupPermissions()

        setContent {
            val status by agentRuntime.status.collectAsState()
            val logs by agentRuntime.logs.collectAsState()
            val downloadState by agentRuntime.downloadState.collectAsState()

            AtlasDashboardApp(
                command = commandText,
                onCommandChange = { commandText = it },
                status = status,
                logs = logs,
                downloadState = downloadState,
                accessibilityEnabled = accessibilityEnabled,
                onRun = {
                    lifecycleScope.launch {
                        agentRuntime.execute(commandText)
                    }
                },
                onStop = agentRuntime::cancelExecution,
                onVoiceInput = ::launchVoiceInput,
                onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                onOpenHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                onDownloadModel = {
                    lifecycleScope.launch { agentRuntime.startModelDownload() }
                },
                onImportModel = { importModelLauncher.launch(arrayOf("*/*")) },
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled = PhoneControlService.isEnabled(this)
        if (!accessibilityEnabled) {
            Toast.makeText(
                this,
                "Enable the Atlas accessibility service to allow autonomous control.",
                Toast.LENGTH_LONG
            ).show()
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

    internal fun setCommandForTesting(value: String) {
        commandText = value
    }

    internal fun runCurrentCommandForTesting() {
        lifecycleScope.launch {
            agentRuntime.execute(commandText)
        }
    }

    internal fun openSettingsForTesting() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
