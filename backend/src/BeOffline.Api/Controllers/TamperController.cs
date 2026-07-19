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
[Route("api/tamper")]
public sealed class TamperController(AppDbContext db, INotificationService notifier) : ControllerBase
{
    /// <summary>
    /// Device-reported bypass signals (protection stopped, accessibility
    /// disabled, clock tamper…). Stored and immediately fanned out to every
    /// active partner — bypass detection is a first-class feature, not a log.
    /// </summary>
    [HttpPost]
    public async Task<IActionResult> Report(ReportTamperRequest request)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        // Idempotent per client event — the outbox may retry.
        var exists = await db.TamperEvents.AnyAsync(t => t.Uid == uid && t.ClientEventId == request.ClientEventId);
        if (exists) return NoContent();

        db.TamperEvents.Add(new TamperEvent
        {
            Id = Guid.NewGuid(),
            Uid = uid,
            Type = request.Type,
            PackageName = request.PackageName,
            ClientEventId = request.ClientEventId,
            OccurredAtUtc = request.OccurredAtUtc,
            ReportedAtUtc = now
        });
        await db.SaveChangesAsync();

        var name = (await db.Users.FindAsync(uid))?.DisplayName ?? "Your partner";
        var partners = await db.Pairings
            .Where(p => p.Status == PairingStatus.Active && (p.UserAUid == uid || p.UserBUid == uid))
            .ToListAsync();
        foreach (var pairing in partners)
        {
            await notifier.NotifyAsync(
                pairing.PartnerOf(uid), "TAMPER_ALERT",
                new { uid, type = request.Type, packageName = request.PackageName, occurredAtUtc = request.OccurredAtUtc },
                "Protection alert",
                $"{name}: {Describe(request.Type)}");
        }

        return NoContent();
    }

    /// <summary>A partner's recent tamper log — guarded by an active pairing.</summary>
    [HttpGet("partner/{partnerUid}")]
    public async Task<ActionResult<List<TamperEventDto>>> PartnerLog(string partnerUid)
    {
        var uid = User.Uid();
        var paired = await db.Pairings.AnyAsync(p =>
            p.Status == PairingStatus.Active &&
            ((p.UserAUid == uid && p.UserBUid == partnerUid) ||
             (p.UserAUid == partnerUid && p.UserBUid == uid)));
        if (!paired) return Forbid();

        return await db.TamperEvents
            .Where(t => t.Uid == partnerUid)
            .OrderByDescending(t => t.ReportedAtUtc)
            .Take(50)
            .Select(t => new TamperEventDto(t.Type, t.PackageName, t.OccurredAtUtc, t.ReportedAtUtc))
            .ToListAsync();
    }

    private static string Describe(string type) => type switch
    {
        "PROTECTION_STOPPED" => "app blocking was force-stopped",
        "ACCESSIBILITY_DISABLED" => "app-block protection was turned off",
        "OVERLAY_DISABLED" => "the block screen permission was revoked",
        "CLOCK_TAMPER_SUSPECTED" => "the device clock may have been changed to dodge a schedule",
        "RESTRICTION_DISABLED" => "a restriction was turned off mid-window",
        _ => type
    };
}
