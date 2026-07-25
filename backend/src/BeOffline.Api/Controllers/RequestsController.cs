using BeOffline.Api.Auth;
using BeOffline.Api.Contracts;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;
using BeOffline.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/requests")]
public sealed class RequestsController(AppDbContext db, INotificationService notifier, IConfiguration config)
    : ControllerBase
{
    [HttpPost]
    public async Task<ActionResult<UnlockRequestDto>> Create(CreateUnlockRequest request)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        // Idempotent: the offline outbox may retry after a lost response.
        var existing = await db.UnlockRequests
            .Include(r => r.Group)
            .FirstOrDefaultAsync(r => r.RequesterUid == uid && r.ClientRequestId == request.ClientRequestId);
        if (existing is not null)
            return UnlockRequestDto.From(existing);

        var expiryMinutes = config.GetValue("Accountability:RequestExpiryMinutes", 15);
        var unlockRequest = new UnlockRequest
        {
            Id = Guid.NewGuid(),
            RequesterUid = uid,
            PackageName = request.PackageName,
            AppLabel = request.AppLabel,
            ClientRequestId = request.ClientRequestId,
            Status = UnlockRequestStatus.Pending,
            RequestedAtUtc = now,
            ExpiresAtUtc = now.AddMinutes(expiryMinutes)
        };

        List<string> approverUids;
        string? groupName = null;
        if (request.GroupId is { } groupId)
        {
            var group = await db.Groups.FindAsync(groupId);
            var members = await db.GroupMembers
                .Where(m => m.GroupId == groupId && m.Status == GroupMemberStatus.Active)
                .ToListAsync();
            if (group is null || members.All(m => m.Uid != uid))
                return BadRequest(new { message = "You are not a member of this group." });
            approverUids = members.Where(m => m.Uid != uid).Select(m => m.Uid).ToList();
            if (approverUids.Count == 0)
                return BadRequest(new { message = "This group has no one else to ask yet — share an invite first." });
            unlockRequest.GroupId = groupId;
            groupName = group.Name;
        }
        else
        {
            Pairing? pairing;
            if (request.PairingId is { } pairingId)
            {
                pairing = await db.Pairings.FindAsync(pairingId);
            }
            else
            {
                var mine = await db.Pairings
                    .Where(p => p.Status == PairingStatus.Active && (p.UserAUid == uid || p.UserBUid == uid))
                    .ToListAsync();
                if (mine.Count > 1)
                    return BadRequest(new { message = "Multiple pairings — specify pairingId." });
                pairing = mine.SingleOrDefault();
            }
            if (pairing is null || pairing.Status != PairingStatus.Active || !pairing.Involves(uid))
                return BadRequest(new { message = "No active pairing to send this request to." });
            unlockRequest.PairingId = pairing.Id;
            approverUids = [pairing.PartnerOf(uid)];
        }

        db.UnlockRequests.Add(unlockRequest);
        await db.SaveChangesAsync();

        // First name only: this string is both the notification title and the
        // name the requester is shown by in the approver's list.
        var requesterName = Names.First((await db.Users.FindAsync(uid))?.DisplayName);
        var dto = UnlockRequestDto.From(unlockRequest, requesterName, groupName);
        foreach (var approverUid in approverUids)
        {
            await notifier.NotifyAsync(
                approverUid, "UNLOCK_REQUEST", dto,
                $"{requesterName} asks to open {request.AppLabel}",
                $"Approve or deny within {expiryMinutes} minutes.");
        }

        return dto;
    }

    [HttpPost("{id:guid}/respond")]
    public async Task<ActionResult<UnlockRequestDto>> Respond(Guid id, RespondToRequest response)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var request = await db.UnlockRequests
            .Include(r => r.Pairing)
            .Include(r => r.Group)
            .FirstOrDefaultAsync(r => r.Id == id);
        if (request is null) return NotFound();
        if (request.RequesterUid == uid)
            return Forbid();

        // Authorization + anti-puppet gate, per scope. A freshly-added approver
        // (pairing or group member) can't approve until its activation cooldown.
        List<string> otherApproverUids;
        if (request.GroupId is { } groupId)
        {
            var members = await db.GroupMembers
                .Where(m => m.GroupId == groupId && m.Status == GroupMemberStatus.Active)
                .ToListAsync();
            var me = members.FirstOrDefault(m => m.Uid == uid);
            if (me is null) return NotFound();
            if (me.CanApproveAfterUtc > now)
                return StatusCode(StatusCodes.Status403Forbidden,
                    new { message = $"You can approve requests in this group after {me.CanApproveAfterUtc:u}." });
            otherApproverUids = members
                .Where(m => m.Uid != uid && m.Uid != request.RequesterUid)
                .Select(m => m.Uid).ToList();
        }
        else
        {
            if (request.Pairing is null || !request.Pairing.Involves(uid))
                return NotFound();
            if (request.Pairing.CanApproveAfterUtc > now)
                return StatusCode(StatusCodes.Status403Forbidden,
                    new { message = $"This pairing can approve requests after {request.Pairing.CanApproveAfterUtc:u}." });
            otherApproverUids = [];
        }

        var isApprove = string.Equals(response.Verdict, "APPROVE", StringComparison.OrdinalIgnoreCase);
        var isDeny = string.Equals(response.Verdict, "DENY", StringComparison.OrdinalIgnoreCase);
        if (!isApprove && !isDeny)
            return BadRequest(new { message = "Verdict must be APPROVE or DENY." });
        // The approver freely sets the duration — deliberately no server-side cap.
        if (isApprove && response.DurationMinutes is not > 0)
            return BadRequest(new { message = "APPROVE requires durationMinutes > 0." });

        // Lazy expiry — don't depend on sweep timing.
        if (request.Status == UnlockRequestStatus.Pending && request.ExpiresAtUtc <= now)
        {
            request.Status = UnlockRequestStatus.Expired;
            request.ResolvedAtUtc = now;
            await db.SaveChangesAsync();
        }
        if (request.Status != UnlockRequestStatus.Pending)
            return Conflict(new { message = $"Request is already {request.Status}." });

        // First decisive response wins (group quorum v1). The guarded UPDATE is
        // the claim: with two simultaneous responders, exactly one matches the
        // Pending row — the loser gets the Conflict above on retry semantics.
        var newStatus = isApprove ? UnlockRequestStatus.Approved : UnlockRequestStatus.Denied;
        var grantedUntil = isApprove ? now.AddMinutes(response.DurationMinutes!.Value) : (DateTime?)null;
        var claimed = await db.UnlockRequests
            .Where(r => r.Id == id && r.Status == UnlockRequestStatus.Pending)
            .ExecuteUpdateAsync(s => s
                .SetProperty(r => r.Status, newStatus)
                .SetProperty(r => r.ResolvedByUid, uid)
                .SetProperty(r => r.ResolvedAtUtc, now)
                .SetProperty(r => r.GrantedDurationMinutes, isApprove ? response.DurationMinutes : null)
                .SetProperty(r => r.GrantedUntilUtc, grantedUntil));
        if (claimed == 0)
        {
            var current = await db.UnlockRequests.AsNoTracking().FirstAsync(r => r.Id == id);
            return Conflict(new { message = $"Request is already {current.Status}." });
        }
        // Mirror the claimed values onto the tracked entity for the DTO below.
        request.Status = newStatus;
        request.ResolvedByUid = uid;
        request.ResolvedAtUtc = now;
        request.GrantedDurationMinutes = isApprove ? response.DurationMinutes : null;
        request.GrantedUntilUtc = grantedUntil;

        db.RequestApprovals.Add(new RequestApproval
        {
            Id = Guid.NewGuid(),
            RequestId = request.Id,
            ApproverUid = uid,
            Verdict = isApprove ? ApprovalVerdict.Approve : ApprovalVerdict.Deny,
            DurationMinutes = isApprove ? response.DurationMinutes : null,
            RespondedAtUtc = now
        });
        if (isApprove)
        {
            db.Allowances.Add(new AllowanceRecord
            {
                Id = Guid.NewGuid(),
                Uid = request.RequesterUid,
                PackageName = request.PackageName,
                RequestId = request.Id,
                GrantedUntilUtc = request.GrantedUntilUtc!.Value,
                Source = request.GroupId != null ? "GROUP" : "PARTNER",
                CreatedAtUtc = now
            });
        }
        await db.SaveChangesAsync();

        var approverName = Names.First((await db.Users.FindAsync(uid))?.DisplayName);
        var dto = UnlockRequestDto.From(request, resolvedByName: approverName);
        await notifier.NotifyAsync(
            request.RequesterUid,
            isApprove ? "REQUEST_APPROVED" : "REQUEST_DENIED",
            dto,
            isApprove ? $"{request.AppLabel} unlocked" : "Request denied",
            isApprove
                ? $"{approverName} unlocked {request.AppLabel} for {response.DurationMinutes} minutes."
                : $"{approverName} denied your request to open {request.AppLabel}.");

        // Group scope: tell the other members it's been handled ("resolved by X").
        foreach (var otherUid in otherApproverUids)
        {
            await notifier.NotifyAsync(
                otherUid, "REQUEST_RESOLVED", dto,
                "Request resolved",
                $"{approverName} {(isApprove ? "approved" : "denied")} {request.AppLabel} for this request.");
        }

        return dto;
    }

    [HttpPost("{id:guid}/cancel")]
    public async Task<ActionResult<UnlockRequestDto>> Cancel(Guid id)
    {
        var uid = User.Uid();
        var request = await db.UnlockRequests.FirstOrDefaultAsync(r => r.Id == id && r.RequesterUid == uid);
        if (request is null) return NotFound();
        if (request.Status != UnlockRequestStatus.Pending)
            return Conflict(new { message = $"Request is already {request.Status}." });

        request.Status = UnlockRequestStatus.Cancelled;
        request.ResolvedAtUtc = DateTime.UtcNow;
        await db.SaveChangesAsync();
        return UnlockRequestDto.From(request);
    }

    [HttpGet]
    public async Task<ActionResult<List<UnlockRequestDto>>> List([FromQuery] string role = "outgoing")
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var query = role.Equals("incoming", StringComparison.OrdinalIgnoreCase)
            ? db.UnlockRequests.Include(r => r.Pairing).Include(r => r.Group).Where(r =>
                r.RequesterUid != uid &&
                ((r.PairingId != null && (r.Pairing!.UserAUid == uid || r.Pairing.UserBUid == uid)) ||
                 (r.GroupId != null && db.GroupMembers.Any(m =>
                     m.GroupId == r.GroupId && m.Uid == uid && m.Status == GroupMemberStatus.Active))))
            : db.UnlockRequests.Include(r => r.Group).Where(r => r.RequesterUid == uid);

        var requests = await query
            .OrderByDescending(r => r.RequestedAtUtc)
            .Take(50)
            .ToListAsync();

        // Lazy expiry so clients never see a stale PENDING.
        var changed = false;
        foreach (var r in requests.Where(r => r.Status == UnlockRequestStatus.Pending && r.ExpiresAtUtc <= now))
        {
            r.Status = UnlockRequestStatus.Expired;
            r.ResolvedAtUtc = now;
            changed = true;
        }
        if (changed) await db.SaveChangesAsync();

        var nameUids = requests.Select(r => r.RequesterUid)
            .Concat(requests.Where(r => r.ResolvedByUid != null).Select(r => r.ResolvedByUid!))
            .Distinct().ToList();
        var names = await db.Users
            .Where(u => nameUids.Contains(u.Uid))
            .ToDictionaryAsync(u => u.Uid, u => u.DisplayName);

        return requests.Select(r => UnlockRequestDto.From(
            r,
            names.GetValueOrDefault(r.RequesterUid),
            resolvedByName: r.ResolvedByUid is { } by ? names.GetValueOrDefault(by) : null)).ToList();
    }
}
