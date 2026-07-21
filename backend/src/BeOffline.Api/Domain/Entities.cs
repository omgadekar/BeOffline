namespace BeOffline.Api.Domain;

/// <summary>
/// A BeOffline account. The primary key IS the Firebase Auth UID — stable
/// across reinstalls, which is what makes pairings survive them.
/// </summary>
public sealed class AppUser
{
    public required string Uid { get; set; }
    public string? DisplayName { get; set; }
    public string? Email { get; set; }
    public DateTime CreatedAtUtc { get; set; }
    public DateTime LastSeenAtUtc { get; set; }

    /// <summary>Set once when partners were told this user's app went silent (uninstall suspected).</summary>
    public DateTime? UninstallNotifiedAtUtc { get; set; }
}

public sealed class Device
{
    public Guid Id { get; set; }
    public required string Uid { get; set; }
    /// <summary>Client-generated stable id, so one user can have several devices.</summary>
    public required string DeviceId { get; set; }
    public string? Model { get; set; }
    public required string FcmToken { get; set; }
    public DateTime LastHeartbeatAtUtc { get; set; }
}

public sealed class InviteCode
{
    public required string Code { get; set; }
    public required string IssuerUid { get; set; }
    public DateTime CreatedAtUtc { get; set; }
    public DateTime ExpiresAtUtc { get; set; }
    public string? ConsumedByUid { get; set; }
}

public enum PairingStatus
{
    Active,
    Removed
}

/// <summary>
/// A reciprocal accountability pairing: either party can request, either can
/// approve the other's requests.
///
/// Two abuse guards live here (see the accountability plan, deliverable 6):
///  - Removal is not instant: RemovalEffectiveAtUtc = request + cooldown, and
///    the pairing keeps working (restriction stays enforced) until then. The
///    partner is notified the moment removal is requested.
///  - Anti-puppet: a pairing created while either party already has another
///    pairing cannot approve until CanApproveAfterUtc (blocks "add a puppet
///    approver, then remove the real one").
/// </summary>
public sealed class Pairing
{
    public Guid Id { get; set; }
    public required string UserAUid { get; set; }
    public required string UserBUid { get; set; }
    public PairingStatus Status { get; set; }
    public DateTime CreatedAtUtc { get; set; }
    public DateTime CanApproveAfterUtc { get; set; }

    public string? RemovalRequestedByUid { get; set; }
    public DateTime? RemovalRequestedAtUtc { get; set; }
    public DateTime? RemovalEffectiveAtUtc { get; set; }

    public bool Involves(string uid) => UserAUid == uid || UserBUid == uid;
    public string PartnerOf(string uid) => UserAUid == uid ? UserBUid : UserAUid;
}

public enum GroupMemberStatus
{
    Active,
    Removed
}

/// <summary>
/// An approver group (M4): any active member can request an unlock, any OTHER
/// active member can approve it — first decisive response wins (quorum v1).
/// </summary>
public sealed class ApproverGroup
{
    public Guid Id { get; set; }
    public required string Name { get; set; }
    /// <summary>Gates removing other members; transferred by the sweep if the owner leaves.</summary>
    public required string OwnerUid { get; set; }
    public DateTime CreatedAtUtc { get; set; }
}

/// <summary>
/// Membership carries the same two abuse guards as <see cref="Pairing"/>:
/// anti-puppet activation delay (CanApproveAfterUtc) and a non-instant,
/// loudly-announced removal cooldown.
/// </summary>
public sealed class GroupMember
{
    public Guid Id { get; set; }
    public Guid GroupId { get; set; }
    public ApproverGroup? Group { get; set; }
    public required string Uid { get; set; }
    public GroupMemberStatus Status { get; set; }
    public DateTime JoinedAtUtc { get; set; }
    public DateTime CanApproveAfterUtc { get; set; }

    public string? RemovalRequestedByUid { get; set; }
    public DateTime? RemovalRequestedAtUtc { get; set; }
    public DateTime? RemovalEffectiveAtUtc { get; set; }
}

/// <summary>Separate table from pairing invites so the two flows can't cross-consume codes.</summary>
public sealed class GroupInviteCode
{
    public required string Code { get; set; }
    public Guid GroupId { get; set; }
    public required string IssuerUid { get; set; }
    public DateTime CreatedAtUtc { get; set; }
    public DateTime ExpiresAtUtc { get; set; }
    public string? ConsumedByUid { get; set; }
}

/// <summary>
/// Group chat (M4). ConversationKey is "group:{groupId}"; the pair:{a}:{b}
/// form is reserved for 1:1 chat later, so the table needs no schema change then.
/// </summary>
public sealed class ChatMessage
{
    public Guid Id { get; set; }
    public required string ConversationKey { get; set; }
    public required string SenderUid { get; set; }
    public required string Body { get; set; }
    /// <summary>Client idempotency key — the offline outbox may retry a send.</summary>
    public required string ClientMessageId { get; set; }
    public DateTime SentAtUtc { get; set; }
}

public enum UnlockRequestStatus
{
    Pending,
    Approved,
    Denied,
    Expired,
    Cancelled
}

/// <summary>Scoped to exactly one of PairingId (1:1) or GroupId (fan-out, first responder wins).</summary>
public sealed class UnlockRequest
{
    public Guid Id { get; set; }
    public Guid? PairingId { get; set; }
    public Pairing? Pairing { get; set; }
    public Guid? GroupId { get; set; }
    public ApproverGroup? Group { get; set; }
    public required string RequesterUid { get; set; }
    public required string PackageName { get; set; }
    public required string AppLabel { get; set; }
    /// <summary>Client idempotency key — offline outbox retries must not duplicate.</summary>
    public required string ClientRequestId { get; set; }
    public UnlockRequestStatus Status { get; set; }
    public DateTime RequestedAtUtc { get; set; }
    public DateTime ExpiresAtUtc { get; set; }
    public string? ResolvedByUid { get; set; }
    public DateTime? ResolvedAtUtc { get; set; }
    public int? GrantedDurationMinutes { get; set; }
    public DateTime? GrantedUntilUtc { get; set; }
}

public enum ApprovalVerdict
{
    Approve,
    Deny
}

/// <summary>Audit row per approver response (also the quorum substrate for M4 groups).</summary>
public sealed class RequestApproval
{
    public Guid Id { get; set; }
    public Guid RequestId { get; set; }
    public required string ApproverUid { get; set; }
    public ApprovalVerdict Verdict { get; set; }
    public int? DurationMinutes { get; set; }
    public DateTime RespondedAtUtc { get; set; }
}

/// <summary>Server-side mirror of granted allowances (audit; the device enforces locally).</summary>
public sealed class AllowanceRecord
{
    public Guid Id { get; set; }
    public required string Uid { get; set; }
    public required string PackageName { get; set; }
    public Guid? RequestId { get; set; }
    public DateTime GrantedUntilUtc { get; set; }
    public required string Source { get; set; }
    public DateTime CreatedAtUtc { get; set; }
}

/// <summary>
/// The accountability log: every detectable bypass the device reports.
/// "Make bypass visible and socially costly" — this is that feature's data.
/// </summary>
public sealed class TamperEvent
{
    public Guid Id { get; set; }
    public required string Uid { get; set; }
    /// <summary>PROTECTION_STOPPED, ACCESSIBILITY_DISABLED, APP_UNINSTALLED_SUSPECTED, CLOCK_TAMPER_SUSPECTED, ...</summary>
    public required string Type { get; set; }
    public string? PackageName { get; set; }
    public required string ClientEventId { get; set; }
    public DateTime OccurredAtUtc { get; set; }
    public DateTime ReportedAtUtc { get; set; }
}

/// <summary>
/// One row per handled HTTP request (operational logging). Deliberately stores
/// metadata only — never request/response bodies, auth headers, or tokens — so
/// the log itself introduces no new sensitive-data surface.
/// </summary>
public sealed class ActivityLog
{
    public long Id { get; set; }
    /// <summary>Firebase UID if the caller was authenticated; null otherwise.</summary>
    public string? Uid { get; set; }
    public required string Method { get; set; }
    public required string Path { get; set; }
    public int StatusCode { get; set; }
    public long DurationMs { get; set; }
    public string? IpAddress { get; set; }
    public DateTime TimestampUtc { get; set; }
}

/// <summary>One row per unhandled exception that reached the pipeline.</summary>
public sealed class ErrorLog
{
    public long Id { get; set; }
    public string? Uid { get; set; }
    public string? Method { get; set; }
    public string? Path { get; set; }
    public required string Message { get; set; }
    public string? ExceptionType { get; set; }
    public string? StackTrace { get; set; }
    public DateTime TimestampUtc { get; set; }
}
