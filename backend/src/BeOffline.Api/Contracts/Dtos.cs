using BeOffline.Api.Domain;

namespace BeOffline.Api.Contracts;

// ── Pairing ──────────────────────────────────────────────────────────────────

public sealed record CreateInviteResponse(string Code, DateTime ExpiresAtUtc);

public sealed record AcceptInviteRequest(string Code);

public sealed record PairingDto(
    Guid Id,
    string PartnerUid,
    string? PartnerName,
    string Status,
    DateTime CreatedAtUtc,
    DateTime CanApproveAfterUtc,
    bool RemovalPending,
    string? RemovalRequestedByUid,
    DateTime? RemovalEffectiveAtUtc)
{
    public static PairingDto From(Pairing p, string myUid, string? partnerName) => new(
        p.Id,
        p.PartnerOf(myUid),
        partnerName,
        p.Status.ToString(),
        p.CreatedAtUtc,
        p.CanApproveAfterUtc,
        p.RemovalRequestedAtUtc != null && p.Status == PairingStatus.Active,
        p.RemovalRequestedByUid,
        p.RemovalEffectiveAtUtc);
}

// ── Unlock requests ──────────────────────────────────────────────────────────

public sealed record CreateUnlockRequest(
    string ClientRequestId,
    string PackageName,
    string AppLabel,
    Guid? PairingId);

public sealed record RespondToRequest(string Verdict, int? DurationMinutes);

public sealed record UnlockRequestDto(
    Guid Id,
    Guid PairingId,
    string RequesterUid,
    string? RequesterName,
    string PackageName,
    string AppLabel,
    string Status,
    DateTime RequestedAtUtc,
    DateTime ExpiresAtUtc,
    string? ResolvedByUid,
    int? GrantedDurationMinutes,
    DateTime? GrantedUntilUtc)
{
    public static UnlockRequestDto From(UnlockRequest r, string? requesterName = null) => new(
        r.Id, r.PairingId, r.RequesterUid, requesterName, r.PackageName, r.AppLabel,
        r.Status.ToString(), r.RequestedAtUtc, r.ExpiresAtUtc,
        r.ResolvedByUid, r.GrantedDurationMinutes, r.GrantedUntilUtc);
}

// ── Devices / tamper ─────────────────────────────────────────────────────────

public sealed record RegisterDeviceRequest(string DeviceId, string FcmToken, string? Model);

public sealed record HeartbeatRequest(string DeviceId);

public sealed record ReportTamperRequest(
    string ClientEventId,
    string Type,
    string? PackageName,
    DateTime OccurredAtUtc);

public sealed record TamperEventDto(string Type, string? PackageName, DateTime OccurredAtUtc, DateTime ReportedAtUtc);
