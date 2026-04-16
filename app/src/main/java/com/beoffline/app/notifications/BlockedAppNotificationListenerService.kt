package com.beoffline.app.notifications

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.beoffline.app.data.repository.BlockRuleRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class BlockedAppNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "BlockedNotifListener"
    }

    @Inject
    lateinit var repository: BlockRuleRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()
    @Volatile
    private var isListenerConnected: Boolean = false

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            repository.getActiveRules().collectLatest { rules ->
                blockedPackages = rules
                    .flatMap { it.blockedPackages }
                    .toSet()
                if (isListenerConnected) {
                    mainScope.launch {
                        cancelMatchingActiveNotifications()
                    }
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        mainScope.launch {
            cancelMatchingActiveNotifications()
        }
    }

    override fun onListenerDisconnected() {
        isListenerConnected = false
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mainScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (!matchesBlockedNotification(notification)) return
        dismissNotification(notification, reason = "posted")
    }

    private fun matchesBlockedNotification(notification: StatusBarNotification): Boolean {
        val blocked = blockedPackages
        if (blocked.isEmpty()) return false

        val postingPackage = notification.packageName
        val operationPackage = notification.opPkg
        if (postingPackage in blocked || operationPackage in blocked) {
            return true
        }

        // Some delegated/proxied notifications encode the originating package in the key or tag.
        val key = notification.key
        val tag = notification.tag.orEmpty()
        return blocked.any { blockedPackage ->
            key.contains(blockedPackage, ignoreCase = true) ||
                tag.contains(blockedPackage, ignoreCase = true)
        }
    }

    private fun dismissNotification(notification: StatusBarNotification, reason: String) {
        try {
            cancelNotification(notification.key)
            Log.d(
                TAG,
                "Dismissed $reason notification. package=${notification.packageName} opPkg=${notification.opPkg} key=${notification.key}"
            )
        } catch (error: Exception) {
            Log.w(
                TAG,
                "Failed to dismiss $reason notification for ${notification.packageName} key=${notification.key}",
                error
            )
        }
    }

    private fun cancelMatchingActiveNotifications() {
        if (!isListenerConnected) return
        val activeNotifications = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activeNotifications
            } else {
                getActiveNotifications()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to inspect active notifications.", error)
            return
        }

        activeNotifications
            .filter(::matchesBlockedNotification)
            .forEach { notification ->
                dismissNotification(notification, reason = "active")
            }
    }
}
