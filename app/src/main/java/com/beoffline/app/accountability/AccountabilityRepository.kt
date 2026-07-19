package com.beoffline.app.accountability

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.beoffline.app.MainActivity
import com.beoffline.app.data.local.AllowanceDao
import com.beoffline.app.data.local.OutboxDao
import com.beoffline.app.data.local.PartnerDao
import com.beoffline.app.data.local.UnlockRequestCacheDao
import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.AllowanceSource
import com.beoffline.app.data.model.CachedUnlockRequest
import com.beoffline.app.data.model.OutboxItem
import com.beoffline.app.data.model.Partner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client side of the accountability protocol.
 *
 * Fail-closed rule: creating an unlock request NEVER unblocks anything.
 * Requests go through the outbox; only a REQUEST_APPROVED event (push or
 * refresh) inserts a local [Allowance], which is what the M2 enforcement
 * check consults. No connectivity → the app simply stays blocked.
 */
@Singleton
class AccountabilityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiService,
    private val partnerDao: PartnerDao,
    private val requestCacheDao: UnlockRequestCacheDao,
    private val outboxDao: OutboxDao,
    private val allowanceDao: AllowanceDao
) {
    companion object {
        private const val TAG = "AccountabilityRepo"
        private const val CHANNEL_ID = "beoffline_accountability"
        private const val PREFS = "accountability"
        private const val KEY_DEVICE_ID = "device_id"
        private const val OUTBOX_UNLOCK_REQUEST = "UNLOCK_REQUEST"
        private const val OUTBOX_TAMPER = "TAMPER_EVENT"
    }

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val partners: Flow<List<Partner>> = partnerDao.getActive()
    val allRequests: Flow<List<CachedUnlockRequest>> = requestCacheDao.getAll()
    val incomingPending: Flow<List<CachedUnlockRequest>> = requestCacheDao.getIncomingPending()

    suspend fun hasPartner(): Boolean =
        FirebaseAuth.getInstance().currentUser != null && partnerDao.getActiveOnce().isNotEmpty()

    // ── Pairing ───────────────────────────────────────────────────────────────

    suspend fun createInvite(): InviteResponseDto = api.createInvite()

    suspend fun acceptInvite(code: String): PairingDto {
        val dto = api.acceptInvite(AcceptInviteBody(code))
        refreshPartners()
        return dto
    }

    suspend fun removePartner(pairingId: String) {
        api.removePairing(pairingId)
        refreshPartners()
    }

    suspend fun refreshPartners() {
        val dtos = api.listPairings()
        partnerDao.clear()
        partnerDao.upsertAll(dtos.map { it.toEntity() })
    }

    // ── Unlock requests ───────────────────────────────────────────────────────

    /**
     * Queues a request for the partner (offline-safe). Returns the client id;
     * status is visible in the request cache ("Queued" until the server acks).
     */
    suspend fun enqueueUnlockRequest(packageName: String, appLabel: String): String {
        val clientRequestId = UUID.randomUUID().toString()
        val body = CreateRequestBody(clientRequestId, packageName, appLabel, pairingId = null)
        outboxDao.insert(
            OutboxItem(type = OUTBOX_UNLOCK_REQUEST, clientKey = clientRequestId, payloadJson = gson.toJson(body))
        )
        requestCacheDao.upsert(
            CachedUnlockRequest(
                id = clientRequestId,
                direction = "OUTGOING",
                packageName = packageName,
                appLabel = appLabel,
                status = "Queued",
                requesterName = null,
                requestedAtUtc = System.currentTimeMillis(),
                expiresAtUtc = null,
                grantedUntilUtc = null
            )
        )
        OutboxWorker.enqueue(context)
        return clientRequestId
    }

    suspend fun respondToRequest(requestId: String, approve: Boolean, durationMinutes: Int?) {
        val dto = api.respondToRequest(
            requestId,
            RespondBody(if (approve) "APPROVE" else "DENY", durationMinutes)
        )
        requestCacheDao.upsert(dto.toEntity(direction = "INCOMING"))
    }

    suspend fun refreshRequests() {
        val outgoing = api.listRequests("outgoing").map { it.toEntity("OUTGOING") }
        val incoming = api.listRequests("incoming").map { it.toEntity("INCOMING") }
        requestCacheDao.upsertAll(outgoing + incoming)
        // An approval may have landed while push was unavailable.
        outgoing.filter { it.status == "Approved" }.forEach { ensureAllowance(it) }
        requestCacheDao.pruneOlderThan(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
    }

    // ── Tamper ────────────────────────────────────────────────────────────────

    /** [dedupeKey] collapses repeats (e.g. one ACCESSIBILITY_DISABLED per day). */
    suspend fun reportTamper(type: String, packageName: String?, dedupeKey: String) {
        val body = TamperBody(
            clientEventId = dedupeKey,
            type = type,
            packageName = packageName,
            occurredAtUtc = Instant.now().toString()
        )
        outboxDao.insert(
            OutboxItem(type = OUTBOX_TAMPER, clientKey = dedupeKey, payloadJson = gson.toJson(body))
        )
        OutboxWorker.enqueue(context)
    }

    // ── Devices ───────────────────────────────────────────────────────────────

    fun deviceId(): String = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString()
        .also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    suspend fun registerDevice(fcmToken: String? = null) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        try {
            val token = fcmToken ?: FirebaseMessaging.getInstance().token.await()
            api.registerDevice(RegisterDeviceBody(deviceId(), token, Build.MODEL))
        } catch (e: Exception) {
            Log.w(TAG, "Device registration failed (will retry on next heartbeat)", e)
        }
    }

    suspend fun heartbeat() {
        if (FirebaseAuth.getInstance().currentUser == null) return
        try {
            api.heartbeat(HeartbeatBody(deviceId()))
        } catch (e: Exception) {
            // 404 = device not registered yet (first run, or server reset) — fix it now.
            try { registerDevice() } catch (_: Exception) { /* next cycle */ }
            Log.d(TAG, "Heartbeat failed: ${e.message}")
        }
    }

    // ── Outbox drain (called by OutboxWorker on network) ─────────────────────

    /** Returns true when the outbox is empty afterwards. */
    suspend fun drainOutbox(): Boolean {
        val items = outboxDao.getAllOnce()
        var allSent = true
        for (item in items) {
            try {
                when (item.type) {
                    OUTBOX_UNLOCK_REQUEST -> {
                        val body = gson.fromJson(item.payloadJson, CreateRequestBody::class.java)
                        val dto = api.createRequest(body)
                        // Server id replaces the client id in the cache.
                        requestCacheDao.delete(body.clientRequestId)
                        requestCacheDao.upsert(dto.toEntity("OUTGOING"))
                        if (dto.status == "Approved") ensureAllowance(dto.toEntity("OUTGOING"))
                    }
                    OUTBOX_TAMPER -> {
                        val body = gson.fromJson(item.payloadJson, TamperBody::class.java)
                        api.reportTamper(body)
                    }
                }
                outboxDao.delete(item.id)
            } catch (e: Exception) {
                Log.w(TAG, "Outbox item ${item.id} (${item.type}) failed: ${e.message}")
                outboxDao.incrementAttempts(item.id)
                allSent = false
            }
        }
        return allSent
    }

    // ── Push / event handling (FCM + refresh share this) ─────────────────────

    suspend fun handleEvent(type: String, payloadJson: String) {
        Log.d(TAG, "Event: $type")
        when (type) {
            "UNLOCK_REQUEST" -> {
                val dto = gson.fromJson(payloadJson, UnlockRequestDto::class.java)
                requestCacheDao.upsert(dto.toEntity("INCOMING"))
                notify(
                    dto.id.hashCode(),
                    "${dto.requesterName ?: "Your partner"} asks to open ${dto.appLabel}",
                    "Open BeOffline to approve or deny."
                )
            }
            "REQUEST_APPROVED" -> {
                val dto = gson.fromJson(payloadJson, UnlockRequestDto::class.java)
                val entity = dto.toEntity("OUTGOING")
                requestCacheDao.upsert(entity)
                // THE integration point: approval becomes a local allowance,
                // and M2's per-event enforcement lifts the block automatically.
                ensureAllowance(entity)
                notify(
                    dto.id.hashCode(),
                    "${dto.appLabel} unlocked",
                    "Approved for ${dto.grantedDurationMinutes} minutes. Open it now."
                )
            }
            "REQUEST_DENIED", "REQUEST_EXPIRED" -> {
                val dto = gson.fromJson(payloadJson, UnlockRequestDto::class.java)
                requestCacheDao.upsert(dto.toEntity("OUTGOING"))
                notify(
                    dto.id.hashCode(),
                    if (type == "REQUEST_DENIED") "Request denied" else "Request expired",
                    "${dto.appLabel} stays blocked."
                )
            }
            "INVITE_ACCEPTED", "PARTNER_REMOVAL_STARTED", "PARTNER_REMOVED" -> {
                try { refreshPartners() } catch (_: Exception) { }
                val message = when (type) {
                    "INVITE_ACCEPTED" -> "Your invite was accepted — you're now accountability partners."
                    "PARTNER_REMOVAL_STARTED" -> "Your partner started removing you. The pairing stays active for the cooldown period."
                    else -> "An accountability pairing has ended."
                }
                notify(type.hashCode(), "Accountability update", message)
            }
            "TAMPER_ALERT" -> {
                notify(
                    ("tamper" + System.currentTimeMillis()).hashCode(),
                    "Protection alert",
                    parseTamperBody(payloadJson)
                )
            }
        }
    }

    private suspend fun ensureAllowance(request: CachedUnlockRequest) {
        val grantedUntil = request.grantedUntilUtc ?: return
        if (grantedUntil <= System.currentTimeMillis()) return
        allowanceDao.insert(
            Allowance(
                packageName = request.packageName,
                ruleId = 0, // partner grants are not rule-scoped
                grantedUntil = grantedUntil,
                source = AllowanceSource.PARTNER
            )
        )
    }

    private fun parseTamperBody(payloadJson: String): String = try {
        val map = gson.fromJson(payloadJson, Map::class.java)
        "Partner protection change: ${map["type"]}"
    } catch (_: Exception) {
        "A partner's protection state changed."
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun notify(id: Int, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Accountability", NotificationManager.IMPORTANCE_HIGH)
        )
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS revoked between check and notify — ignore.
            }
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun PairingDto.toEntity() = Partner(
        pairingId = id,
        partnerUid = partnerUid,
        partnerName = partnerName,
        status = status,
        canApproveAfterUtc = Instant.parse(canApproveAfterUtc).toEpochMilli(),
        removalPending = removalPending,
        removalEffectiveAtUtc = removalEffectiveAtUtc?.let { Instant.parse(it).toEpochMilli() }
    )

    private fun UnlockRequestDto.toEntity(direction: String) = CachedUnlockRequest(
        id = id,
        direction = direction,
        packageName = packageName,
        appLabel = appLabel,
        status = status,
        requesterName = requesterName,
        requestedAtUtc = Instant.parse(requestedAtUtc).toEpochMilli(),
        expiresAtUtc = Instant.parse(expiresAtUtc).toEpochMilli(),
        grantedUntilUtc = grantedUntilUtc?.let { Instant.parse(it).toEpochMilli() }
    )
}
