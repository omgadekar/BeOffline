package com.beoffline.app.accountability

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FCM entry point. The server always sends a data payload {type, payload}
 * (same envelope as SignalR), so events are handled even when the user swipes
 * the notification away or the app is backgrounded.
 */
@AndroidEntryPoint
class AccountabilityMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var repository: AccountabilityRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: return
        val payload = message.data["payload"] ?: "{}"
        scope.launch {
            try {
                repository.handleEvent(type, payload)
            } catch (e: Exception) {
                Log.e("AccountabilityFcm", "Failed handling $type", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        scope.launch {
            try {
                repository.registerDevice(token)
            } catch (e: Exception) {
                Log.w("AccountabilityFcm", "Token registration failed", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
