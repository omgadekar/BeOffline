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

    // ── Groups (M4) ───────────────────────────────────────────────────────────
    @POST("api/groups")
    suspend fun createGroup(@Body body: CreateGroupBody): GroupDto

    @POST("api/groups/{id}/invites")
    suspend fun createGroupInvite(@Path("id") groupId: String): InviteResponseDto

    @POST("api/groups/join")
    suspend fun joinGroup(@Body body: JoinGroupBody): GroupDto

    @GET("api/groups")
    suspend fun listGroups(): List<GroupDto>

    @DELETE("api/groups/{id}/members/{uid}")
    suspend fun removeGroupMember(@Path("id") groupId: String, @Path("uid") memberUid: String): GroupDto

    // ── Chat (M4) ─────────────────────────────────────────────────────────────
    @POST("api/chat/groups/{id}/messages")
    suspend fun sendChatMessage(@Path("id") groupId: String, @Body body: SendChatBody): ChatMessageDto

    @GET("api/chat/groups/{id}/messages")
    suspend fun listChatMessages(
        @Path("id") groupId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 50
    ): List<ChatMessageDto>

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
data class CreateGroupBody(val name: String)
data class JoinGroupBody(val code: String)
data class SendChatBody(val clientMessageId: String, val body: String)
data class CreateRequestBody(
    val clientRequestId: String,
    val packageName: String,
    val appLabel: String,
    val pairingId: String?,
    val groupId: String? = null
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
    val pairingId: String?,
    val groupId: String?,
    val groupName: String?,
    val requesterUid: String,
    val requesterName: String?,
    val packageName: String,
    val appLabel: String,
    val status: String,
    val requestedAtUtc: String,
    val expiresAtUtc: String,
    val resolvedByUid: String?,
    val resolvedByName: String?,
    val grantedDurationMinutes: Int?,
    val grantedUntilUtc: String?
)

data class GroupMemberDto(
    val uid: String,
    val displayName: String?,
    val status: String,
    val joinedAtUtc: String,
    val canApproveAfterUtc: String,
    val removalPending: Boolean,
    val removalEffectiveAtUtc: String?,
    /** Coarse presence: last device heartbeat. */
    val lastSeenAtUtc: String?
)

data class GroupDto(
    val id: String,
    val name: String,
    val ownerUid: String,
    val createdAtUtc: String,
    val members: List<GroupMemberDto>
)

data class ChatMessageDto(
    val id: String,
    val conversationKey: String,
    val senderUid: String,
    val senderName: String?,
    val body: String,
    val sentAtUtc: String
)
