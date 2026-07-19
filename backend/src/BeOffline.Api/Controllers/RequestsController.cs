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
        var existing = await db.UnlockRequests.FirstOrDefaultAsync(r =>
            r.RequesterUid == uid && r.ClientRequestId == request.ClientRequestId);
        if (existing is not null)
            return UnlockRequestDto.From(existing);

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

        var expiryMinutes = config.GetValue("Accountability:RequestExpiryMinutes", 15);
        var unlockRequest = new UnlockRequest
        {
            Id = Guid.NewGuid(),
            PairingId = pairing.Id,
            RequesterUid = uid,
            PackageName = request.PackageName,
            AppLabel = request.AppLabel,
            ClientRequestId = request.ClientRequestId,
            Status = UnlockRequestStatus.Pending,
            RequestedAtUtc = now,
            ExpiresAtUtc = now.AddMinutes(expiryMinutes)
        };
        db.UnlockRequests.Add(unlockRequest);
        await db.SaveChangesAsync();

        var requesterName = (await db.Users.FindAsync(uid))?.DisplayName ?? "Your partner";
        await notifier.NotifyAsync(
            pairing.PartnerOf(uid), "UNLOCK_REQUEST",
            UnlockRequestDto.From(unlockRequest, requesterName),
            $"{requesterName} asks to open {request.AppLabel}",
            $"Approve or deny within {expiryMinutes} minutes.");

        return UnlockRequestDto.From(unlockRequest, requesterName);
    }

    [HttpPost("{id:guid}/respond")]
    public async Task<ActionResult<UnlockRequestDto>> Respond(Guid id, RespondToRequest response)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var request = await db.UnlockRequests.Include(r => r.Pairing).FirstOrDefaultAsync(r => r.Id == id);
        if (request?.Pairing is null || !request.Pairing.Involves(uid))
            return NotFound();
        if (request.RequesterUid == uid)
            return Forbid();

        // Lazy expiry — don't depend on sweep timing.
        if (request.Status == UnlockRequestStatus.Pending && request.ExpiresAtUtc <= now)
        {
            request.Status = UnlockRequestStatus.Expired;
            request.ResolvedAtUtc = now;
            await db.SaveChangesAsync();
        }
        if (request.Status != UnlockRequestStatus.Pending)
            return Conflict(new { message = $"Request is already {request.Status}." });

        // Anti-puppet: a freshly-created pairing can't approve yet.
        if (request.Pairing.CanApproveAfterUtc > now)
            return StatusCode(StatusCodes.Status403Forbidden,
                new { message = $"This pairing can approve requests after {request.Pairing.CanApproveAfterUtc:u}." });

        var isApprove = string.Equals(response.Verdict, "APPROVE", StringComparison.OrdinalIgnoreCase);
        var isDeny = string.Equals(response.Verdict, "DENY", StringComparison.OrdinalIgnoreCase);
        if (!isApprove && !isDeny)
            return BadRequest(new { message = "Verdict must be APPROVE or DENY." });
        // The approver freely sets the duration — deliberately no server-side cap.
        if (isApprove && response.DurationMinutes is not > 0)
            return BadRequest(new { message = "APPROVE requires durationMinutes > 0." });

        request.Status = isApprove ? UnlockRequestStatus.Approved : UnlockRequestStatus.Denied;
        request.ResolvedByUid = uid;
        request.ResolvedAtUtc = now;
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
            request.GrantedDurationMinutes = response.DurationMinutes;
            request.GrantedUntilUtc = now.AddMinutes(response.DurationMinutes!.Value);
            db.Allowances.Add(new AllowanceRecord
            {
                Id = Guid.NewGuid(),
                Uid = request.RequesterUid,
                PackageName = request.PackageName,
                RequestId = request.Id,
                GrantedUntilUtc = request.GrantedUntilUtc.Value,
                Source = "PARTNER",
                CreatedAtUtc = now
            });
        }
        await db.SaveChangesAsync();

        var approverName = (await db.Users.FindAsync(uid))?.DisplayName ?? "Your partner";
        await notifier.NotifyAsync(
            request.RequesterUid,
            isApprove ? "REQUEST_APPROVED" : "REQUEST_DENIED",
            UnlockRequestDto.From(request),
            isApprove ? $"{request.AppLabel} unlocked" : "Request denied",
            isApprove
                ? $"{approverName} unlocked {request.AppLabel} for {response.DurationMinutes} minutes."
                : $"{approverName} denied your request to open {request.AppLabel}.");

        return UnlockRequestDto.From(request);
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
            ? db.UnlockRequests.Include(r => r.Pairing).Where(r =>
                r.RequesterUid != uid &&
                (r.Pairing!.UserAUid == uid || r.Pairing.UserBUid == uid))
            : db.UnlockRequests.Where(r => r.RequesterUid == uid);

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

        var requesterUids = requests.Select(r => r.RequesterUid).Distinct().ToList();
        var names = await db.Users
            .Where(u => requesterUids.Contains(u.Uid))
            .ToDictionaryAsync(u => u.Uid, u => u.DisplayName);

        return requests.Select(r => UnlockRequestDto.From(r, names.GetValueOrDefault(r.RequesterUid))).ToList();
    }
}
