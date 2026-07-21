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

// ── Groups ───────────────────────────────────────────────────────────────────

public sealed record CreateGroupRequest(string Name);

public sealed record JoinGroupRequest(string Code);

public sealed record GroupMemberDto(
    string Uid,
    string? DisplayName,
    string Status,
    DateTime JoinedAtUtc,
    DateTime CanApproveAfterUtc,
    bool RemovalPending,
    DateTime? RemovalEffectiveAtUtc,
    DateTime? LastSeenAtUtc)
{
    public static GroupMemberDto From(GroupMember m, string? displayName, DateTime? lastSeenAtUtc) => new(
        m.Uid, displayName, m.Status.ToString(), m.JoinedAtUtc, m.CanApproveAfterUtc,
        m.RemovalRequestedAtUtc != null && m.Status == GroupMemberStatus.Active,
        m.RemovalEffectiveAtUtc, lastSeenAtUtc);
}

public sealed record GroupDto(
    Guid Id,
    string Name,
    string OwnerUid,
    DateTime CreatedAtUtc,
    List<GroupMemberDto> Members);

// ── Chat ─────────────────────────────────────────────────────────────────────

public sealed record SendChatMessageRequest(string ClientMessageId, string Body, List<string>? MentionedUids = null);

public sealed record ChatMessageDto(
    Guid Id,
    string ConversationKey,
    string SenderUid,
    string? SenderName,
    string Body,
    List<string> MentionedUids,
    DateTime SentAtUtc)
{
    public static ChatMessageDto From(ChatMessage m, string? senderName = null) => new(
        m.Id, m.ConversationKey, m.SenderUid, senderName, m.Body,
        string.IsNullOrEmpty(m.MentionedUids)
            ? new List<string>()
            : m.MentionedUids.Split(',', StringSplitOptions.RemoveEmptyEntries).ToList(),
        m.SentAtUtc);
}

// ── Unlock requests ──────────────────────────────────────────────────────────

public sealed record CreateUnlockRequest(
    string ClientRequestId,
    string PackageName,
    string AppLabel,
    Guid? PairingId,
    Guid? GroupId = null);

public sealed record RespondToRequest(string Verdict, int? DurationMinutes);

public sealed record UnlockRequestDto(
    Guid Id,
    Guid? PairingId,
    Guid? GroupId,
    string? GroupName,
    string RequesterUid,
    string? RequesterName,
    string PackageName,
    string AppLabel,
    string Status,
    DateTime RequestedAtUtc,
    DateTime ExpiresAtUtc,
    string? ResolvedByUid,
    string? ResolvedByName,
    int? GrantedDurationMinutes,
    DateTime? GrantedUntilUtc)
{
    public static UnlockRequestDto From(
        UnlockRequest r, string? requesterName = null, string? groupName = null, string? resolvedByName = null) => new(
        r.Id, r.PairingId, r.GroupId, groupName ?? r.Group?.Name, r.RequesterUid, requesterName,
        r.PackageName, r.AppLabel, r.Status.ToString(), r.RequestedAtUtc, r.ExpiresAtUtc,
        r.ResolvedByUid, resolvedByName, r.GrantedDurationMinutes, r.GrantedUntilUtc);
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
