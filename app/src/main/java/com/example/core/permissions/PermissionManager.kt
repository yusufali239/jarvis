package com.example.core.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.android.accessibility.JarvisAccessibilityService
import com.example.android.notifications.JarvisNotificationListenerService

data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isSystemSetting: Boolean = false,
    val requiredPermission: String? = null
)

class PermissionManager(private val context: Context) {

    fun isMicrophoneGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isCameraGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedService = ComponentName(context, JarvisAccessibilityService::class.java).flattenToString()
        return enabledServices.any {
            val cn = ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name)
            cn.flattenToString().equals(expectedService, ignoreCase = true)
        } || JarvisAccessibilityService.isServiceRunning
    }

    fun isNotificationListenerEnabled(): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true || JarvisNotificationListenerService.isServiceRunning
    }

    fun getAllPermissionsStatus(): List<PermissionItem> {
        return listOf(
            PermissionItem(
                id = "mic",
                title = "Microphone",
                description = "Required for real-time voice commands and speech recognition.",
                isGranted = isMicrophoneGranted(),
                requiredPermission = Manifest.permission.RECORD_AUDIO
            ),
            PermissionItem(
                id = "camera",
                title = "Camera",
                description = "Enables J.A.R.V.I.S. vision analysis to identify real-world objects and text.",
                isGranted = isCameraGranted(),
                requiredPermission = Manifest.permission.CAMERA
            ),
            PermissionItem(
                id = "notifications",
                title = "Notification Alerts",
                description = "Allows J.A.R.V.I.S. to notify you of completed background tasks and alerts.",
                isGranted = isNotificationPermissionGranted(),
                requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
            ),
            PermissionItem(
                id = "accessibility",
                title = "Accessibility Automation",
                description = "Allows J.A.R.V.I.S. to read on-screen UI elements, tap buttons, scroll, and type.",
                isGranted = isAccessibilityServiceEnabled(),
                isSystemSetting = true
            ),
            PermissionItem(
                id = "notification_listener",
                title = "Notification Listener",
                description = "Allows J.A.R.V.I.S. to read incoming alerts and summarize messages.",
                isGranted = isNotificationListenerEnabled(),
                isSystemSetting = true
            )
        )
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
