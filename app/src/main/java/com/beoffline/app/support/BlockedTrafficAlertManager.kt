package com.beoffline.app.support

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.beoffline.app.MainActivity

@Singleton
class BlockedTrafficAlertManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "blocked_traffic_alerts"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_NOTIFICATION_AT = "last_notification_at"
        private const val KEY_SESSION_STARTED_AT = "session_started_at"
        private const val THROTTLE_MS = 3 * 60 * 1000L
        private const val WARM_UP_MS = 3 * 60 * 1000L
        private const val CHANNEL_ID = "beoffline_blocked_traffic_alerts"
        private const val NOTIFICATION_ID = 1002
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    @Volatile
    private var enabledCache: Boolean = preferences.getBoolean(KEY_ENABLED, true)
    @Volatile
    private var lastNotificationAtCache: Long = preferences.getLong(KEY_LAST_NOTIFICATION_AT, 0L)
    @Volatile
    private var sessionStartedAtCache: Long = preferences.getLong(KEY_SESSION_STARTED_AT, 0L)

    fun isEnabled(): Boolean = enabledCache

    fun setEnabled(enabled: Boolean) {
        enabledCache = enabled
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun markSessionStarted(startedAt: Long = System.currentTimeMillis()) {
        sessionStartedAtCache = startedAt
        preferences.edit()
            .putLong(KEY_SESSION_STARTED_AT, startedAt)
            .putLong(KEY_LAST_NOTIFICATION_AT, 0L)
            .apply()
        lastNotificationAtCache = 0L
    }

    fun markSessionStopped() {
        sessionStartedAtCache = 0L
        clearAlertNotification()
        preferences.edit().putLong(KEY_SESSION_STARTED_AT, 0L).apply()
    }

    fun notifyBlockedTrafficAttempt(blockedPackages: List<String>) {
        if (!enabledCache) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val now = System.currentTimeMillis()
        if (sessionStartedAtCache == 0L || (now - sessionStartedAtCache) < WARM_UP_MS) return
        if ((now - lastNotificationAtCache) < THROTTLE_MS) return
        if (isAlertNotificationStillVisible()) return

        lastNotificationAtCache = now
        preferences.edit().putLong(KEY_LAST_NOTIFICATION_AT, now).apply()

        createNotificationChannel()

        val (title, body) = buildNotificationCopy(blockedPackages)
        val contentIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun isAlertNotificationStillVisible(): Boolean {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.activeNotifications.any { it.id == NOTIFICATION_ID }
    }

    private fun clearAlertNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun buildNotificationCopy(blockedPackages: List<String>): Pair<String, String> {
        if (blockedPackages.size == 1) {
            val appName = resolveAppName(blockedPackages.first())
            return "$appName is offline" to
                "BeOffline blocked a connection attempt, so $appName stays offline until your rule ends."
        }

        return "Blocked app stayed offline" to
            "A blocked app tried to connect, and BeOffline kept it offline because your rule is active."
    }

    private fun resolveAppName(packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(pm).toString()
        }.getOrElse {
            packageName.substringAfterLast('.').replaceFirstChar { char -> char.uppercase() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Blocked app alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when a blocked app tries to connect while BeOffline is active."
            setShowBadge(false)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
