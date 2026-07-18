package com.beoffline.app.background

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.content.ComponentName
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.beoffline.app.notifications.BlockedAppNotificationListenerService
import com.beoffline.app.openblock.OpenBlockAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class BackgroundProtectionStatus(
    val batteryOptimizationIgnored: Boolean,
    val notificationsEnabled: Boolean,
    val notificationAccessEnabled: Boolean,
    val exactAlarmAllowed: Boolean,
    val overlayAllowed: Boolean,
    val accessibilityEnabled: Boolean,
    val manufacturer: String,
    val manufacturerInstructions: List<String>
) {
    val needsAttention: Boolean
        get() = !batteryOptimizationIgnored || !notificationsEnabled
}

@Singleton
class BackgroundProtectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getStatus(): BackgroundProtectionStatus {
        return BackgroundProtectionStatus(
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(),
            notificationsEnabled = areNotificationsEnabled(),
            notificationAccessEnabled = isNotificationAccessEnabled(),
            exactAlarmAllowed = canScheduleExactAlarms(),
            overlayAllowed = canDrawOverlays(),
            accessibilityEnabled = isAccessibilityServiceEnabled(),
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

    fun openNotificationSettings() {
        val packageUri = Uri.parse("package:${context.packageName}")
        val primaryIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            data = packageUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            packageUri
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        tryStart(primaryIntent, fallbackIntent)
    }

    fun openNotificationAccessSettings() {
        val componentName = ComponentName(context, BlockedAppNotificationListenerService::class.java)
        val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName.flattenToString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        tryStart(detailIntent, fallbackIntent)
    }

    /** Open-block engine: "Display over other apps" permission for the block overlay. */
    fun openOverlayPermissionSettings() {
        val packageUri = Uri.parse("package:${context.packageName}")
        val requestIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        tryStart(requestIntent, fallbackIntent)
    }

    /**
     * Open-block engine: system Accessibility settings, where the user enables
     * the BeOffline app-block service. Only navigate here AFTER the in-app
     * prominent disclosure has been accepted (Play policy).
     */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tryStart(intent)
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

    private fun areNotificationsEnabled(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return notificationsEnabled
        }
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return notificationsEnabled && permissionGranted
    }

    private fun isNotificationAccessEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    /**
     * True when the open-block accessibility service is enabled in system
     * Accessibility settings. Read from Secure settings — robust to the user
     * (or Android 17 Advanced Protection Mode) revoking it at any time.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, OpenBlockAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == expected
        }
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
