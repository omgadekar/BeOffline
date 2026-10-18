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
import com.beoffline.app.data.local.ChatMessageDao
import com.beoffline.app.data.local.GroupCacheDao
import com.beoffline.app.data.local.OutboxDao
import com.beoffline.app.data.local.PartnerDao
import com.beoffline.app.data.local.UnlockRequestCacheDao
import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.AllowanceSource
import com.beoffline.app.data.model.CachedUnlockRequest
import com.beoffline.app.data.model.ChatMessageCache
import com.beoffline.app.data.model.GroupCache
import com.beoffline.app.data.model.OutboxItem
import com.beoffline.app.data.model.Partner
import com.beoffline.app.util.firstName
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
    private val allowanceDao: AllowanceDao,
    private val groupCacheDao: GroupCacheDao,
    private val chatMessageDao: ChatMessageDao
) {
    companion object {
        private const val TAG = "AccountabilityRepo"
        private const val CHANNEL_ID = "beoffline_accountability"
        private const val PREFS = "accountability"
        private const val KEY_DEVICE_ID = "device_id"
        private const val OUTBOX_UNLOCK_REQUEST = "UNLOCK_REQUEST"
        private const val OUTBOX_TAMPER = "TAMPER_EVENT"
        private const val OUTBOX_CHAT = "CHAT_MESSAGE"
        private const val OUTBOX_SOLO_UNLOCK = "SOLO_UNLOCK"

        /** Notification → NavGraph deep-link. Read by MainActivity. */
        const val EXTRA_NAV_ROUTE = "beoffline.nav_route"
        const val ROUTE_ACCOUNTABILITY = "accountability"
        const val ROUTE_GROUPS = "groups"

        fun conversationKeyFor(groupId: String) = "group:$groupId"
        fun groupChatRoute(groupId: String) = "group_chat/$groupId"
    }

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Conversation currently on screen — its incoming messages skip the notification. */
    @Volatile
    var activeConversationKey: String? = null

    val partners: Flow<List<Partner>> = partnerDao.getActive()
    val allRequests: Flow<List<CachedUnlockRequest>> = requestCacheDao.getAll()
    val incomingPending: Flow<List<CachedUnlockRequest>> = requestCacheDao.getIncomingPending()
    val groups: Flow<List<GroupCache>> = groupCacheDao.getAll()

    fun myUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    suspend fun hasPartner(): Boolean =
        FirebaseAuth.getInstance().currentUser != null && partnerDao.getActiveOnce().isNotEmpty()

    suspend fun hasGroup(): Boolean =
        FirebaseAuth.getInstance().currentUser != null && groupCacheDao.getAllOnce().isNotEmpty()

    /** Oldest-joined group — the overlay's "Ask my group" target (v1: no chooser). */
    suspend fun firstGroup(): GroupCache? = groupCacheDao.getAllOnce().firstOrNull()

    /** The single partner's stored display name, for the overlay's "Ask …" button. */
    suspend fun firstPartnerName(): String? =
        if (FirebaseAuth.getInstance().currentUser == null) null
        else partnerDao.getActiveOnce().firstOrNull()?.partnerName

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

    // ── Account ───────────────────────────────────────────────────────────────

    /**
     * Deletes the account server-side, then clears all local accountability
     * state and signs out. Only touches local data on success — a failed server
     * call leaves the account intact so the user can retry.
     */
    suspend fun deleteAccount() {
        api.deleteAccount()
        partnerDao.clear()
        requestCacheDao.clearAll()
        groupCacheDao.clear()
        chatMessageDao.clearAll()
        outboxDao.clearAll()
        allowanceDao.deleteRemoteGrants()
        FirebaseAuth.getInstance().signOut()
    }

    // ── Groups (M4) ───────────────────────────────────────────────────────────

    suspend fun createGroup(name: String): GroupDto {
        val dto = api.createGroup(CreateGroupBody(name))
        refreshGroups()
        return dto
    }

    suspend fun createGroupInvite(groupId: String): InviteResponseDto = api.createGroupInvite(groupId)

    suspend fun joinGroup(code: String): GroupDto {
        val dto = api.joinGroup(JoinGroupBody(code))
        refreshGroups()
        return dto
    }

    /** Leave (memberUid == mine) or, as owner, remove someone — starts the visible cooldown. */
    suspend fun removeGroupMember(groupId: String, memberUid: String) {
        api.removeGroupMember(groupId, memberUid)
        refreshGroups()
    }

    suspend fun refreshGroups() {
        val dtos = api.listGroups()
        groupCacheDao.clear()
        groupCacheDao.upsertAll(dtos.map { it.toEntity() })
    }

    fun membersOf(group: GroupCache): List<GroupMemberDto> = try {
        gson.fromJson(group.membersJson, Array<GroupMemberDto>::class.java).toList()
    } catch (_: Exception) {
        emptyList()
    }

    // ── Chat (M4) ─────────────────────────────────────────────────────────────

    fun chatMessages(groupId: String): Flow<List<ChatMessageCache>> =
        chatMessageDao.forConversation(conversationKeyFor(groupId))

    /**
     * Offline-first send: a pending local echo appears immediately; the outbox
     * delivers when the network allows and swaps in the server's copy.
     */
    suspend fun sendChatMessage(groupId: String, text: String, mentionedUids: List<String> = emptyList()) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        val clientMessageId = UUID.randomUUID().toString()
        chatMessageDao.upsert(
            ChatMessageCache(
                id = clientMessageId,
                conversationKey = conversationKeyFor(groupId),
                senderUid = user.uid,
                senderName = user.displayName,
                body = body,
                sentAtUtc = System.currentTimeMillis(),
                pending = true,
                mentionedUids = mentionedUids.joinToString(",").ifEmpty { null }
            )
        )
        outboxDao.insert(
            OutboxItem(
                type = OUTBOX_CHAT,
                clientKey = clientMessageId,
                payloadJson = gson.toJson(ChatOutboxPayload(groupId, clientMessageId, body, mentionedUids))
            )
        )
        OutboxWorker.enqueue(context)
    }

    suspend fun refreshChat(groupId: String) {
        val dtos = api.listChatMessages(groupId)
        chatMessageDao.upsertAll(dtos.map { it.toEntity() })
        chatMessageDao.pruneOlderThan(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
    }

    // ── Unlock requests ───────────────────────────────────────────────────────

    /**
     * Queues a request for the partner or a group (offline-safe). Returns the
     * client id; status is visible in the cache ("Queued" until the server acks).
     */
    suspend fun enqueueUnlockRequest(packageName: String, appLabel: String, groupId: String? = null): String {
        val clientRequestId = UUID.randomUUID().toString()
        val body = CreateRequestBody(clientRequestId, packageName, appLabel, pairingId = null, groupId = groupId)
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
                grantedUntilUtc = null,
                groupId = groupId
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

    /** Clears resolved request history (keeps active incoming / queued outgoing). */
    suspend fun clearRequestHistory() = requestCacheDao.clearResolved()

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

    // ── Unlock challenges ─────────────────────────────────────────────────────

    /**
     * The server's escalation level for this rule's current focus session, or
     * null when signed out or unreachable. Never throws for the signed-out
     * case: solo challenges work perfectly well with no account at all, and
     * that path must not pay for a network round trip.
     */
    suspend fun fetchChallengeLevel(ruleKey: String, sessionKey: String): Int? {
        if (FirebaseAuth.getInstance().currentUser == null) return null
        return api.challengeLevel(ruleKey, sessionKey).level
    }

    /**
     * Queues a solved solo challenge. Goes through the outbox like every other
     * write: the unlock has already been granted locally, so this must never be
     * the thing that fails and it must never be sent twice.
     */
    suspend fun reportSoloUnlock(
        ruleKey: String,
        sessionKey: String,
        level: Int,
        kind: String,
        packageName: String,
        appLabel: String,
        grantedMinutes: Int
    ) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        val clientEventId = "$ruleKey-$sessionKey-$level"
        val body = SoloUnlockBody(
            clientEventId = clientEventId,
            ruleKey = ruleKey,
            sessionKey = sessionKey,
            level = level,
            kind = kind,
            packageName = packageName,
            appLabel = appLabel,
            grantedMinutes = grantedMinutes,
            occurredAtUtc = Instant.now().toString()
        )
        outboxDao.insert(
            OutboxItem(
                type = OUTBOX_SOLO_UNLOCK,
                clientKey = clientEventId,
                payloadJson = gson.toJson(body)
            )
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
                    OUTBOX_SOLO_UNLOCK -> {
                        val body = gson.fromJson(item.payloadJson, SoloUnlockBody::class.java)
                        api.recordSoloUnlock(body)
                    }
                    OUTBOX_CHAT -> {
                        val payload = gson.fromJson(item.payloadJson, ChatOutboxPayload::class.java)
                        val dto = api.sendChatMessage(
                            payload.groupId,
                            SendChatBody(payload.clientMessageId, payload.body, payload.mentionedUids)
                        )
                        // Server copy replaces the pending local echo.
                        chatMessageDao.delete(payload.clientMessageId)
                        chatMessageDao.upsert(dto.toEntity())
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
                    "${dto.requesterName.firstName()} asks to open ${dto.appLabel}",
                    "Open BeOffline to approve or deny.",
                    route = ROUTE_ACCOUNTABILITY
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
                    "Approved for ${dto.grantedDurationMinutes} minutes. Open it now.",
                    route = ROUTE_ACCOUNTABILITY
                )
            }
            "REQUEST_DENIED", "REQUEST_EXPIRED" -> {
                val dto = gson.fromJson(payloadJson, UnlockRequestDto::class.java)
                requestCacheDao.upsert(dto.toEntity("OUTGOING"))
                notify(
                    dto.id.hashCode(),
                    if (type == "REQUEST_DENIED") "Request denied" else "Request expired",
                    "${dto.appLabel} stays blocked.",
                    route = ROUTE_ACCOUNTABILITY
                )
            }
            "REQUEST_RESOLVED" -> {
                // Group scope: someone else already answered ("resolved by X").
                val dto = gson.fromJson(payloadJson, UnlockRequestDto::class.java)
                requestCacheDao.upsert(dto.toEntity("INCOMING"))
                notify(
                    dto.id.hashCode(),
                    "Request resolved",
                    "${dto.resolvedByName.firstName(fallback = "Another member")} already responded for ${dto.appLabel}.",
                    route = ROUTE_ACCOUNTABILITY
                )
            }
            "INVITE_ACCEPTED", "PARTNER_REMOVAL_STARTED", "PARTNER_REMOVED" -> {
                try { refreshPartners() } catch (_: Exception) { }
                val message = when (type) {
                    "INVITE_ACCEPTED" -> "Your invite was accepted — you're now accountability partners."
                    "PARTNER_REMOVAL_STARTED" -> "Your partner started removing you. The pairing stays active for the cooldown period."
                    else -> "An accountability pairing has ended."
                }
                notify(type.hashCode(), "Accountability update", message, route = ROUTE_ACCOUNTABILITY)
            }
            "GROUP_MEMBER_JOINED", "GROUP_MEMBER_REMOVAL_STARTED", "GROUP_MEMBER_LEFT" -> {
                try { refreshGroups() } catch (_: Exception) { }
                val map = try { gson.fromJson(payloadJson, Map::class.java) } catch (_: Exception) { null }
                val groupName = map?.get("groupName")?.toString() ?: "Your group"
                val message = when (type) {
                    "GROUP_MEMBER_JOINED" -> "A new member joined $groupName."
                    "GROUP_MEMBER_REMOVAL_STARTED" -> "A member is leaving $groupName. Their membership stays active for the cooldown period."
                    else -> "A membership in $groupName has ended."
                }
                notify((type + groupName).hashCode(), groupName, message, route = ROUTE_GROUPS)
            }
            "CHAT_MESSAGE" -> {
                val dto = gson.fromJson(payloadJson, ChatMessageDto::class.java)
                chatMessageDao.upsert(dto.toEntity())
                // No notification for your own echo or the conversation on screen.
                if (dto.senderUid != myUid() && dto.conversationKey != activeConversationKey) {
                    val preview = if (dto.body.length > 120) dto.body.take(120) + "…" else dto.body
                    val groupId = dto.conversationKey.substringAfter("group:", "")
                    val route = if (groupId.isNotEmpty()) groupChatRoute(groupId) else ROUTE_GROUPS
                    val mentionedMe = dto.mentionedUids?.contains(myUid()) == true
                    if (mentionedMe) {
                        notify(
                            dto.conversationKey.hashCode(),
                            "${dto.senderName.firstName(fallback = "Someone")} mentioned you",
                            preview,
                            route = route,
                            highlight = true
                        )
                    } else {
                        notify(
                            dto.conversationKey.hashCode(),
                            dto.senderName.firstName(fallback = "Group chat"),
                            preview,
                            route = route
                        )
                    }
                }
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
                ruleId = 0, // remote grants are not rule-scoped
                grantedUntil = grantedUntil,
                source = if (request.groupId != null) AllowanceSource.GROUP else AllowanceSource.PARTNER
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

    /**
     * [route] deep-links the tap to a NavGraph destination (e.g. the
     * Accountability screen for an incoming request). Null just opens the app.
     * The id doubles as the PendingIntent request code so each notification
     * carries its own route instead of clobbering a shared intent.
     */
    private fun notify(id: Int, title: String, body: String, route: String? = null, highlight: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Accountability", NotificationManager.IMPORTANCE_HIGH)
        )
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (route != null) intent.putExtra(EXTRA_NAV_ROUTE, route)
        val contentIntent = PendingIntent.getActivity(
            context, id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        if (highlight) {
            // @-mention: tint the notification accent so it stands out in the shade.
            builder.setColor(0xFF6C5CE7.toInt())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        }
        val notification = builder.build()
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
        grantedUntilUtc = grantedUntilUtc?.let { Instant.parse(it).toEpochMilli() },
        groupId = groupId,
        groupName = groupName,
        resolvedByName = resolvedByName
    )

    private fun GroupDto.toEntity() = GroupCache(
        groupId = id,
        name = name,
        ownerUid = ownerUid,
        membersJson = gson.toJson(members)
    )

    private fun ChatMessageDto.toEntity() = ChatMessageCache(
        id = id,
        conversationKey = conversationKey,
        senderUid = senderUid,
        senderName = senderName,
        body = body,
        sentAtUtc = Instant.parse(sentAtUtc).toEpochMilli(),
        pending = false,
        mentionedUids = mentionedUids?.takeIf { it.isNotEmpty() }?.joinToString(",")
    )
}

/** Outbox payload for a queued chat message. */
data class ChatOutboxPayload(
    val groupId: String,
    val clientMessageId: String,
    val body: String,
    val mentionedUids: List<String> = emptyList()
)
