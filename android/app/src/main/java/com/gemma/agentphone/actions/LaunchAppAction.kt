package com.gemma.agentphone.actions

import android.content.Context
import android.content.Intent
import com.gemma.agentphone.MainActivity
import com.gemma.agentphone.accessibility.PhoneControlService
import com.gemma.agentphone.agent.AgentStep
import com.gemma.agentphone.agent.IntentSpec
import kotlinx.coroutines.delay

class LaunchAppAction(
    private val context: Context,
    private val serviceProvider: () -> PhoneControlService?
) {
    suspend fun execute(step: AgentStep): ActionResult {
        val appName = step.target.ifBlank { step.value }
        if (appName.isBlank()) {
            return ActionResult(false, "No app name was provided.")
        }

        val launched = serviceProvider()?.launchApp(appName) ?: launchFromContext(appName)
        if (!launched) {
            return ActionResult(false, "Unable to launch $appName.")
        }
        delay(1_500L)
        return ActionResult(true, "Launched $appName.")
    }

    private fun launchFromContext(appName: String): Boolean {
        val packageManager = context.packageManager
        val launchables = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        )
        val match = launchables.firstOrNull { info ->
            val label = info.loadLabel(packageManager)?.toString().orEmpty().lowercase()
            val packageName = info.activityInfo?.packageName.orEmpty().lowercase()
            val normalized = appName.lowercase()
            label == normalized || label.contains(normalized) || packageName.contains(normalized)
        } ?: return false

        val packageName = match.activityInfo?.packageName ?: return false
        if (context is MainActivity) {
            MainActivity.externalActionLauncher.launch(
                context,
                IntentSpec(action = Intent.ACTION_MAIN, packageName = packageName)
            )
            return true
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
