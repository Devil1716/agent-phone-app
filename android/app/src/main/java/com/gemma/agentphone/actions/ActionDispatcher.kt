package com.gemma.agentphone.actions

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.gemma.agentphone.R
import com.gemma.agentphone.accessibility.PhoneControlService
import com.gemma.agentphone.agent.AgentStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ActionResult(
    val success: Boolean,
    val message: String
)

class ActionDispatcher(
    private val context: Context,
    private val serviceProvider: () -> PhoneControlService? = { PhoneControlService.instance }
) {
    private val launchAppAction = LaunchAppAction(context, serviceProvider)
    private val tapAction = TapAction(serviceProvider)
    private val inputTextAction = InputTextAction(serviceProvider)
    private val scrollDownAction = ScrollAction(ScrollDirection.DOWN, serviceProvider)
    private val scrollUpAction = ScrollAction(ScrollDirection.UP, serviceProvider)
    private val backAction = BackAction(serviceProvider)
    private val homeAction = HomeAction(serviceProvider)
    private val waitAction = WaitAction()

    suspend fun dispatch(step: AgentStep): ActionResult {
        blockedReason(step)?.let { return ActionResult(false, it) }
        announce(step)
        val result = withTimeoutOrNull(3_000L) {
            when (step.normalizedAction) {
                "LAUNCH_APP" -> launchAppAction.execute(step)
                "TAP_TEXT", "TAP_COORDS" -> tapAction.execute(step)
                "INPUT_TEXT" -> inputTextAction.execute(step)
                "SCROLL_DOWN" -> scrollDownAction.execute(step)
                "SCROLL_UP" -> scrollUpAction.execute(step)
                "PRESS_BACK" -> backAction.execute(step)
                "PRESS_HOME" -> homeAction.execute(step)
                "WAIT" -> waitAction.execute(step)
                "SEARCH" -> executeSearch(step)
                "DONE" -> ActionResult(true, "Task complete.")
                else -> ActionResult(false, "Unsupported action: ${step.action}")
            }
        }
        return result ?: ActionResult(false, "Timed out while executing ${step.normalizedAction}.")
    }

    private suspend fun announce(step: AgentStep) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.about_to_action, step.summary()),
                Toast.LENGTH_SHORT
            ).show()
        }
        delay(2_000L)
    }

    private suspend fun executeSearch(step: AgentStep): ActionResult {
        val targetField = step.target.ifBlank { "Search|Search apps & games|Search for apps & games" }
        tapAction.execute(step.copy(action = "TAP_TEXT", target = targetField))
        val inputResult = inputTextAction.execute(step.copy(action = "INPUT_TEXT"))
        if (!inputResult.success) {
            return inputResult
        }
        val service = serviceProvider()
        if (service != null) {
            val submitted = service.tapByText("Search") || service.tapByText("Go") || service.tapByText("Enter")
            if (submitted) {
                return ActionResult(true, "Typed query and submitted the search.")
            }
        }
        return ActionResult(true, "Typed search query.")
    }

    private fun blockedReason(step: AgentStep): String? {
        val combined = listOf(step.action, step.target, step.value, step.reason)
            .joinToString(" ")
            .lowercase()

        return when {
            combined.contains("uninstall") -> "Uninstall actions are blocked."
            combined.contains("delete photo") || combined.contains("delete file") || combined.contains("delete") ->
                "Deleting files or photos is blocked."
            combined.contains("bank") || combined.contains("payment") || combined.contains("upi") ->
                "Banking and payment flows are blocked."
            combined.contains("password") || combined.contains("pin") ->
                "Changing passwords or PINs is blocked."
            combined.contains("send sms") || combined.contains("make call") ->
                "Sending SMS or placing calls requires explicit confirmation."
            containsUnsafeDownloadUrl(step) ->
                "This download source is not on the allowed safe-domain list."
            else -> null
        }
    }

    private fun containsUnsafeDownloadUrl(step: AgentStep): Boolean {
        val candidates = listOf(step.target, step.value)
        val allowedHosts = setOf("github.com", "apkmirror.com", "f-droid.org")
        val blockedHosts = setOf("apkmod", "malware", "downloadfreeapk", "cracked")
        return candidates
            .mapNotNull { value -> runCatching { Uri.parse(value) }.getOrNull() }
            .mapNotNull(Uri::getHost)
            .any { host ->
                val normalized = host.lowercase()
                blockedHosts.any(normalized::contains) ||
                    allowedHosts.none { normalized == it || normalized.endsWith(".$it") }
            }
    }
}
