package com.example.android.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

data class CapturedNotification(
    val id: Int,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postTime: Long
)

class JarvisNotificationListenerService : NotificationListenerService() {

    private val tag = "JarvisNotificationSvc"

    companion object {
        var isServiceRunning: Boolean = false
            private set

        private val recentNotifications = mutableListOf<CapturedNotification>()

        fun getRecentNotifications(): List<CapturedNotification> {
            synchronized(recentNotifications) {
                return recentNotifications.toList()
            }
        }

        fun clearNotifications() {
            synchronized(recentNotifications) {
                recentNotifications.clear()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceRunning = true
        Log.d(tag, "Notification Listener connected.")
        loadActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceRunning = false
        Log.d(tag, "Notification Listener disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString()
            val text = extras.getCharSequence("android.text")?.toString()

            if (!title.isNullOrBlank() || !text.isNullOrBlank()) {
                val captured = CapturedNotification(
                    id = sbn.id,
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    postTime = sbn.postTime
                )
                synchronized(recentNotifications) {
                    recentNotifications.add(0, captured)
                    if (recentNotifications.size > 50) {
                        recentNotifications.removeAt(recentNotifications.size - 1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error reading posted notification: ${e.message}")
        }
    }

    private fun loadActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            synchronized(recentNotifications) {
                recentNotifications.clear()
                for (sbn in active) {
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence("android.title")?.toString()
                    val text = extras.getCharSequence("android.text")?.toString()
                    if (!title.isNullOrBlank() || !text.isNullOrBlank()) {
                        recentNotifications.add(
                            CapturedNotification(
                                id = sbn.id,
                                packageName = sbn.packageName,
                                title = title,
                                text = text,
                                postTime = sbn.postTime
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error reading active notifications: ${e.message}")
        }
    }
}
