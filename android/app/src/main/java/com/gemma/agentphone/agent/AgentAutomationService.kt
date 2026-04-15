package com.gemma.agentphone.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gemma.agentphone.R
import com.gemma.agentphone.diagnostics.AppLogger
import com.gemma.agentphone.model.GemmaInferenceEngine
import com.gemma.agentphone.model.GemmaModelManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AgentAutomationService : LifecycleService() {
    companion object {
        private const val TAG = "AgentAutomationService"
        private const val CHANNEL_ID = "atlas-agent-runtime"
        private const val NOTIFICATION_ID = 41
        private const val EXTRA_GOAL = "extra_goal"
        private const val ACTION_START = "com.gemma.agentphone.agent.START"
        private const val ACTION_STOP = "com.gemma.agentphone.agent.STOP"

        fun buildStartIntent(context: Context, goal: String): Intent {
            return Intent(context, AgentAutomationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GOAL, goal)
            }
        }

        fun buildStopIntent(context: Context): Intent {
            return Intent(context, AgentAutomationService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    private var executionJob: Job? = null
    @Volatile
    private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRequested = true
                executionJob?.cancel()
                AgentSessionStore.updateStatus(AgentStatus.Stopped("Execution stopped."))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> {
                val goal = intent?.getStringExtra(EXTRA_GOAL).orEmpty().trim()
                if (goal.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification("Observing the screen"))
                startExecution(goal, startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startExecution(goal: String, startId: Int) {
        stopRequested = false
        executionJob?.cancel()
        AgentSessionStore.begin(goal)

        executionJob = lifecycleScope.launch {
            try {
                val loop = AccessibilityAgentLoop(
                    context = applicationContext,
                    engine = GemmaInferenceEngine(
                        context = applicationContext,
                        modelManager = GemmaModelManager(applicationContext)
                    )
                )
                val summary = loop.execute(
                    goal = goal,
                    emitStatus = { status ->
                        AgentSessionStore.updateStatus(status)
                        updateNotification(status.notificationLabel())
                    },
                    emitLog = AgentSessionStore::appendLog,
                    shouldStop = { stopRequested }
                )

                AgentSessionStore.updateStatus(
                    when {
                        summary.success -> AgentStatus.Completed(summary.summary)
                        stopRequested -> AgentStatus.Stopped(summary.summary)
                        else -> AgentStatus.Failed(summary.summary)
                    }
                )
                updateNotification(summary.summary)
            } catch (throwable: Throwable) {
                AppLogger.e(applicationContext, TAG, "Background agent execution failed.", throwable)
                val message = throwable.localizedMessage ?: "Agent execution failed."
                AgentSessionStore.appendLog(
                    AgentLogEntry(
                        title = "Error",
                        detail = message,
                        success = false
                    )
                )
                AgentSessionStore.updateStatus(AgentStatus.Failed(message))
                updateNotification(message)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Atlas Agent Runtime",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground execution for the Atlas on-device accessibility agent."
        }
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Atlas is running")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun AgentStatus.notificationLabel(): String {
        return when (this) {
            AgentStatus.Idle -> "Standing by"
            is AgentStatus.Downloading -> progressLabel
            is AgentStatus.Planning -> "Planning the next step"
            is AgentStatus.Executing -> step.summary()
            is AgentStatus.Completed -> summary
            is AgentStatus.Failed -> reason
            is AgentStatus.Stopped -> reason
        }
    }
}
