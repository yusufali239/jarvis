package com.example.android.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SystemController(private val context: Context) {
    private val tag = "SystemController"
    private var isTorchOn = false

    fun toggleFlashlight(enable: Boolean? = null): Boolean {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false

            val targetState = enable ?: !isTorchOn
            cameraManager.setTorchMode(cameraId, targetState)
            isTorchOn = targetState
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle flashlight: ${e.message}")
            return false
        }
    }

    fun isFlashlightOn(): Boolean = isTorchOn

    fun getBatteryInfo(): String {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct: Float = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()) else 100f

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return "Battery Level: ${batteryPct.toInt()}%, Charging: ${if (isCharging) "Yes" else "No"}"
    }

    fun getNetworkInfo(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Network: Unavailable"
        val activeNetwork = cm.activeNetwork ?: return "Network: Offline"
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return "Network: Offline"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Network: Wi-Fi Connected"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Network: Cellular Data Active"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Network: Ethernet Active"
            else -> "Network: Online"
        }
    }

    fun getDeviceInfo(): String {
        return buildString {
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append(getBatteryInfo()).append("\n")
            append(getNetworkInfo())
        }
    }

    fun getCurrentTimeFormatted(): String {
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy HH:mm:ss", Locale.getDefault())
        val tz = TimeZone.getDefault().displayName
        return "${sdf.format(Date())} ($tz)"
    }

    fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("JARVIS", text)
            clipboard?.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to copy to clipboard: ${e.message}")
            false
        }
    }

    fun getClipboardText(): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            item?.text?.toString()
        } catch (e: Exception) {
            null
        }
    }
}
