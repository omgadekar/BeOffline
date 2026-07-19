package com.beoffline.app.accountability

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for the BeOffline accountability API (backend/).
 * Dates travel as ISO-8601 strings (server is .NET, 7-digit fractions —
 * parse with java.time.Instant, never SimpleDateFormat).
 */
interface ApiService {

    // ── Pairing ───────────────────────────────────────────────────────────────
    @POST("api/pairing/invites")
    suspend fun createInvite(): InviteResponseDto

    @POST("api/pairing/invites/accept")
    suspend fun acceptInvite(@Body body: AcceptInviteBody): PairingDto

    @GET("api/pairing")
    suspend fun listPairings(): List<PairingDto>

    @DELETE("api/pairing/{id}")
    suspend fun removePairing(@Path("id") pairingId: String): PairingDto

    // ── Unlock requests ───────────────────────────────────────────────────────
    @POST("api/requests")
    suspend fun createRequest(@Body body: CreateRequestBody): UnlockRequestDto

    @POST("api/requests/{id}/respond")
    suspend fun respondToRequest(@Path("id") id: String, @Body body: RespondBody): UnlockRequestDto

    @GET("api/requests")
    suspend fun listRequests(@Query("role") role: String): List<UnlockRequestDto>

    // ── Devices / tamper ──────────────────────────────────────────────────────
    @PUT("api/devices")
    suspend fun registerDevice(@Body body: RegisterDeviceBody)

    @POST("api/devices/heartbeat")
    suspend fun heartbeat(@Body body: HeartbeatBody)

    @POST("api/tamper")
    suspend fun reportTamper(@Body body: TamperBody)
}

// ── Bodies ───────────────────────────────────────────────────────────────────
data class AcceptInviteBody(val code: String)
data class CreateRequestBody(
    val clientRequestId: String,
    val packageName: String,
    val appLabel: String,
    val pairingId: String?
)
data class RespondBody(val verdict: String, val durationMinutes: Int?)
data class RegisterDeviceBody(val deviceId: String, val fcmToken: String, val model: String?)
data class HeartbeatBody(val deviceId: String)
data class TamperBody(
    val clientEventId: String,
    val type: String,
    val packageName: String?,
    val occurredAtUtc: String
)

// ── Responses ────────────────────────────────────────────────────────────────
data class InviteResponseDto(val code: String, val expiresAtUtc: String)

data class PairingDto(
    val id: String,
    val partnerUid: String,
    val partnerName: String?,
    val status: String,
    val createdAtUtc: String,
    val canApproveAfterUtc: String,
    val removalPending: Boolean,
    val removalRequestedByUid: String?,
    val removalEffectiveAtUtc: String?
)

data class UnlockRequestDto(
    val id: String,
    val pairingId: String,
    val requesterUid: String,
    val requesterName: String?,
    val packageName: String,
    val appLabel: String,
    val status: String,
    val requestedAtUtc: String,
    val expiresAtUtc: String,
    val resolvedByUid: String?,
    val grantedDurationMinutes: Int?,
    val grantedUntilUtc: String?
)
