package com.example.android.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

data class SamsungDeviceSpecs(
    val manufacturer: String,
    val model: String,
    val isSamsung: Boolean,
    val isS21Ultra: Boolean,
    val androidVersion: String,
    val sdkInt: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isBatteryOptimizationIgnored: Boolean,
    val activeAudioOutput: String,
    val displaySummary: String
)

object SamsungDeviceProfiler {
    private const val TAG = "SamsungProfiler"

    /**
     * Inspects real runtime device specs and capabilities.
     */
    fun getDeviceSpecs(context: Context): SamsungDeviceSpecs {
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val model = Build.MODEL ?: "Unknown"
        val product = Build.PRODUCT ?: ""
        val device = Build.DEVICE ?: ""

        val isSamsung = manufacturer.equals("Samsung", ignoreCase = true)
        val isS21Ultra = isSamsung && (
            model.contains("G998", ignoreCase = true) ||
            model.contains("S21 Ultra", ignoreCase = true) ||
            product.contains("p3s", ignoreCase = true) ||
            device.contains("p3s", ignoreCase = true)
        )

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        val audioOutput = getActiveAudioOutput(context)
        val displaySummary = "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels} (${context.resources.displayMetrics.densityDpi} dpi)"

        return SamsungDeviceSpecs(
            manufacturer = manufacturer,
            model = model,
            isSamsung = isSamsung,
            isS21Ultra = isS21Ultra,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            sdkInt = Build.VERSION.SDK_INT,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
            activeAudioOutput = audioOutput,
            displaySummary = displaySummary
        )
    }

    private fun getActiveAudioOutput(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "Built-in Speaker"
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (d in devices) {
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> return "Bluetooth A2DP (${d.productName})"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> return "Bluetooth Headset/SCO (${d.productName})"
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> return "Wired 3.5mm Headset"
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> return "USB-C Audio / DAC"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> return "Stereo Speakers (AKG Tuned)"
            }
        }
        return "Built-in Speaker"
    }

    /**
     * Creates an Intent to request Battery Optimization Exemption to protect Wake Word
     * and Live Voice services from being killed by Samsung One UI App Sleep / Smart Manager.
     */
    fun createIgnoreBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an Intent to open the device's Text-To-Speech settings screen
     * (e.g. to install Russian voice packs in Samsung TTS or Google TTS).
     */
    fun createTtsSettingsIntent(): Intent {
        return Intent("com.android.settings.TTS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an Intent to trigger Android TTS voice data installation.
     */
    fun createInstallTtsVoiceDataIntent(): Intent {
        return Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
