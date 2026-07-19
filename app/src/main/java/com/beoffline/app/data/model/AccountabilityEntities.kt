package com.beoffline.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache + offline queue for the accountability layer (M3).
 * Server (ASP.NET Core API) is the cross-user source of truth; these tables
 * exist so the UI works offline and the outbox can retry safely.
 */

/** Cached accountability pairing (server: Pairing). */
@Entity(tableName = "partners")
data class Partner(
    /** Server pairing id (GUID string). */
    @PrimaryKey val pairingId: String,
    val partnerUid: String,
    val partnerName: String?,
    val status: String,
    val canApproveAfterUtc: Long,
    val removalPending: Boolean,
    val removalEffectiveAtUtc: Long?,
    val syncedAt: Long = System.currentTimeMillis()
)

/** Cached unlock request (either direction), for UI + status tracking. */
@Entity(tableName = "unlock_request_cache")
data class CachedUnlockRequest(
    /** Server request id (GUID string) — or the clientRequestId while queued offline. */
    @PrimaryKey val id: String,
    val direction: String,          // OUTGOING | INCOMING
    val packageName: String,
    val appLabel: String,
    val status: String,             // Queued | Pending | Approved | Denied | Expired | Cancelled
    val requesterName: String?,
    val requestedAtUtc: Long,
    val expiresAtUtc: Long?,
    val grantedUntilUtc: Long?
)

/**
 * Offline outbox: anything that must reach the server eventually (unlock
 * requests, tamper events). Fail-closed by design — a queued unlock request
 * does NOT unblock anything until the server + partner respond.
 */
@Entity(tableName = "outbox_items")
data class OutboxItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,               // UNLOCK_REQUEST | TAMPER_EVENT
    /** Idempotency key sent to the server (safe retries). */
    val clientKey: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0
)
