package com.beoffline.app.background

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class BackgroundProtectionStatus(
    val batteryOptimizationIgnored: Boolean,
    val exactAlarmAllowed: Boolean,
    val manufacturer: String,
    val manufacturerInstructions: List<String>
) {
    val needsAttention: Boolean
        get() = !batteryOptimizationIgnored || !exactAlarmAllowed
}

@Singleton
class BackgroundProtectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getStatus(): BackgroundProtectionStatus {
        return BackgroundProtectionStatus(
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(),
            exactAlarmAllowed = canScheduleExactAlarms(),
            manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() },
            manufacturerInstructions = manufacturerInstructions()
        )
    }

    fun openBatteryOptimizationFlow() {
        val packageUri = Uri.parse("package:${context.packageName}")
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        tryStart(requestIntent, fallbackIntent)
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val packageUri = Uri.parse("package:${context.packageName}")
        val requestIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        tryStart(requestIntent, fallbackIntent)
    }

    fun openAppDetailsSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tryStart(intent)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    private fun manufacturerInstructions(): List<String> {
        return when (Build.MANUFACTURER.orEmpty().lowercase()) {
            "xiaomi", "redmi", "poco" -> listOf(
                "Set Battery saver to No restrictions for BeOffline.",
                "Enable Auto start for BeOffline in App settings.",
                "Lock BeOffline in Recents so MIUI does not clear it."
            )
            "samsung" -> listOf(
                "Set Battery to Unrestricted for BeOffline.",
                "Remove BeOffline from Sleeping apps and Deep sleeping apps.",
                "Allow background activity in App battery settings."
            )
            "oneplus", "oppo", "realme" -> listOf(
                "Allow Auto-launch or Background activity for BeOffline.",
                "Set App battery usage to Allow background activity or No restrictions.",
                "Exclude BeOffline from app cleanup if the launcher offers it."
            )
            "vivo", "iqoo" -> listOf(
                "Enable Background power consumption for BeOffline.",
                "Allow Auto start and keep it out of app cleanup.",
                "Turn off launcher battery optimization for BeOffline."
            )
            "huawei", "honor" -> listOf(
                "Allow BeOffline under App launch management.",
                "Disable battery optimization for BeOffline.",
                "Keep BeOffline locked in Recents if EMUI offers that option."
            )
            "motorola" -> listOf(
                "Turn off Battery optimization for BeOffline.",
                "Disable background restrictions in the app battery screen."
            )
            else -> emptyList()
        }
    }

    private fun tryStart(primary: Intent, fallback: Intent? = null) {
        try {
            context.startActivity(primary)
        } catch (_: ActivityNotFoundException) {
            if (fallback != null) {
                context.startActivity(fallback)
            }
        }
    }
}
