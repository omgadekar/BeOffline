package com.beoffline.app.accountability

import android.util.Log
import com.beoffline.app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live delivery while the app is foregrounded (M4): a SignalR connection to
 * the accountability hub, carrying the same ("event", type, payloadJson)
 * envelope as FCM data messages — both feed [AccountabilityRepository.handleEvent].
 *
 * FCM remains the delivery path when backgrounded, so this connection is a
 * best-effort latency upgrade (chat feels instant), never a correctness
 * requirement. Connection failures are silent and retried while wanted.
 */
@Singleton
class RealtimeClient @Inject constructor(
    private val repository: AccountabilityRepository
) {
    companion object {
        private const val TAG = "RealtimeClient"
        private const val RETRY_DELAY_MS = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connecting = AtomicBoolean(false)

    @Volatile
    private var wantConnected = false

    @Volatile
    private var connection: HubConnection? = null

    /** Called when the app comes to the foreground (and after sign-in). */
    fun start() {
        wantConnected = true
        ensureConnected()
    }

    /** Called when the app leaves the foreground — FCM takes over. */
    fun stop() {
        wantConnected = false
        val hub = connection
        connection = null
        if (hub != null) {
            scope.launch { runCatching { hub.stop().blockingAwait() } }
        }
    }

    private fun ensureConnected() {
        if (!wantConnected || connection != null) return
        if (FirebaseAuth.getInstance().currentUser == null) return
        if (!connecting.compareAndSet(false, true)) return

        scope.launch {
            try {
                val hub = HubConnectionBuilder
                    .create(BuildConfig.ACCOUNTABILITY_API_BASE_URL + "hubs/accountability")
                    .withAccessTokenProvider(firebaseToken())
                    .build()
                hub.on(
                    "event",
                    { type: String, payloadJson: String ->
                        scope.launch {
                            try {
                                repository.handleEvent(type, payloadJson)
                            } catch (e: Exception) {
                                Log.w(TAG, "Event $type failed: ${e.message}")
                            }
                        }
                    },
                    String::class.java, String::class.java
                )
                hub.onClosed {
                    connection = null
                    if (wantConnected) {
                        scope.launch {
                            delay(RETRY_DELAY_MS)
                            ensureConnected()
                        }
                    }
                }
                hub.start().blockingAwait()
                if (wantConnected) {
                    connection = hub
                } else {
                    runCatching { hub.stop().blockingAwait() }
                }
            } catch (e: Exception) {
                Log.d(TAG, "SignalR connect failed (will retry): ${e.message}")
                if (wantConnected) {
                    scope.launch {
                        delay(RETRY_DELAY_MS)
                        ensureConnected()
                    }
                }
            } finally {
                connecting.set(false)
            }
        }
    }

    /**
     * Token per (re)connect. getIdToken(false) serves from cache; an empty
     * token just means the server rejects the handshake and we retry later.
     */
    private fun firebaseToken(): Single<String> = Single.defer {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return@defer Single.just("")
        Single.create { emitter ->
            user.getIdToken(false)
                .addOnSuccessListener { emitter.onSuccess(it.token ?: "") }
                .addOnFailureListener { emitter.onSuccess("") }
        }
    }
}
