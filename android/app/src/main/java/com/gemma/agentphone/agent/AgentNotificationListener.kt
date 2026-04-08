package com.gemma.agentphone.agent

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AgentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Notifications are automatically available via getActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op, we query on demand
    }

    companion object {
        private var instance: AgentNotificationListener? = null

        fun getActiveNotificationSummaries(): List<String> {
            val service = instance ?: return listOf("Notification access not available. Enable it in settings.")
            return try {
                service.activeNotifications
                    ?.filter { it.notification.extras != null }
                    ?.mapNotNull { sbn ->
                        val extras = sbn.notification.extras
                        val title = extras.getString("android.title") ?: ""
                        val text = extras.getCharSequence("android.text")?.toString() ?: ""
                        if (title.isNotBlank() || text.isNotBlank()) {
                            "${sbn.packageName}: $title — $text"
                        } else null
                    }
                    ?.ifEmpty { listOf("No active notifications.") }
                    ?: listOf("No active notifications.")
            } catch (e: Exception) {
                listOf("Could not read notifications: ${e.message}")
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }
}
