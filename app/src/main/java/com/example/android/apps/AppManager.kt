package com.example.android.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean
)

class AppManager(private val context: Context) {
    private val tag = "AppManager"

    fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val list = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            list.add(InstalledAppInfo(appName = label, packageName = pkg, isSystemApp = isSystem))
        }
        return list.sortedBy { it.appName.lowercase() }
    }

    fun openApp(targetNameOrPackage: String): Boolean {
        val pm = context.packageManager
        val query = targetNameOrPackage.trim().lowercase()

        // 1. Check direct package name match
        var launchIntent = pm.getLaunchIntentForPackage(targetNameOrPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return true
        }

        // 2. Search installed apps by name
        val apps = getInstalledApps()
        val exactMatch = apps.firstOrNull { it.appName.lowercase() == query || it.packageName.lowercase() == query }
        if (exactMatch != null) {
            launchIntent = pm.getLaunchIntentForPackage(exactMatch.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }

        val partialMatch = apps.firstOrNull { it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query) }
        if (partialMatch != null) {
            launchIntent = pm.getLaunchIntentForPackage(partialMatch.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }

        // 3. Fallback well-known aliases
        val wellKnownPackage = getWellKnownPackage(query)
        if (wellKnownPackage != null) {
            launchIntent = pm.getLaunchIntentForPackage(wellKnownPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }

        // 4. Intent URL fallback (e.g. YouTube web fallback if app is not installed)
        if (query.contains("youtube")) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
            return true
        }

        return false
    }

    private fun getWellKnownPackage(query: String): String? {
        return when {
            query.contains("youtube") -> "com.google.android.youtube"
            query.contains("chrome") || query.contains("browser") -> "com.android.chrome"
            query.contains("telegram") -> "org.telegram.messenger"
            query.contains("whatsapp") -> "com.whatsapp"
            query.contains("camera") -> "com.google.android.GoogleCamera"
            query.contains("maps") || query.contains("карт") -> "com.google.android.apps.maps"
            query.contains("spotify") || query.contains("музык") -> "com.spotify.music"
            query.contains("settings") || query.contains("настройк") -> "com.android.settings"
            query.contains("calendar") || query.contains("календар") -> "com.google.android.calendar"
            query.contains("clock") || query.contains("час") || query.contains("будильник") -> "com.google.android.deskclock"
            query.contains("calculator") || query.contains("калькулятор") -> "com.google.android.calculator"
            else -> null
        }
    }
}
