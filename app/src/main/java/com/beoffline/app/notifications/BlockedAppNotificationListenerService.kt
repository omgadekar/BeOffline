package com.beoffline.app.notifications

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

@AndroidEntryPoint
class BlockedAppNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "BlockedNotifListener"
    }

    @Inject
    lateinit var repository: BlockRuleRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            repository.getActiveRules().collectLatest { rules ->
                blockedPackages = rules
                    .flatMap { it.blockedPackages }
                    .toSet()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val blocked = blockedPackages
        if (blocked.isEmpty()) return

        val postingPackage = notification.packageName
        val operationPackage = notification.opPkg
        if (postingPackage !in blocked && operationPackage !in blocked) return

        try {
            cancelNotification(notification.key)
            Log.d(
                TAG,
                "Dismissed notification from blocked package. package=$postingPackage opPkg=$operationPackage"
            )
        } catch (error: Exception) {
            Log.w(TAG, "Failed to dismiss blocked notification for $postingPackage", error)
        }
    }
}
