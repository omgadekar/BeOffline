using BeOffline.Api.Auth;
using BeOffline.Api.Contracts;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;
using BeOffline.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Controllers;

/// <summary>
/// Solo unlock challenges.
///
/// The device runs the challenge and grants the allowance entirely on its own —
/// nothing here is on the critical path of getting into a locked app, and being
/// offline must never make an unlock easier or harder than it already was.
/// What the server adds is durability and visibility:
///
///  - the escalation ladder survives a reinstall or a "clear app data", because
///    the count lives here as well as on the device and the client uses the
///    higher of the two;
///  - a partner or group finds out you solved your way past a lock, which is
///    the same "make backing out visible" contract the disable cooldown keeps.
/// </summary>
[ApiController]
[Authorize]
[Route("api/challenges")]
public sealed class ChallengesController(AppDbContext db, INotificationService notifier) : ControllerBase
{
    /// <summary>Known challenge kinds — anything else is rejected rather than stored.</summary>
    private static readonly string[] Kinds = ["Arithmetic", "Retype", "Pattern", "Hold"];

    /// <summary>
    /// Unlocks recorded for one rule's current focus session. Zero for a session
    /// the server has never seen, which is exactly right: a new session starts
    /// the ladder over.
    /// </summary>
    [HttpGet("level")]
    public async Task<ActionResult<ChallengeLevelDto>> Level(
        [FromQuery] string ruleKey, [FromQuery] string sessionKey)
    {
        if (string.IsNullOrWhiteSpace(ruleKey) || string.IsNullOrWhiteSpace(sessionKey))
            return BadRequest(new { message = "ruleKey and sessionKey are required." });

        var uid = User.Uid();
        var level = await db.SoloUnlocks
            .Where(u => u.Uid == uid && u.RuleKey == ruleKey && u.SessionKey == sessionKey)
            .Select(u => (int?)u.Level)
            .MaxAsync() ?? 0;

        return new ChallengeLevelDto(ruleKey, sessionKey, level);
    }

    /// <summary>
    /// Records one solved challenge and tells whoever is holding this user
    /// accountable. Idempotent on ClientEventId — the offline outbox retries,
    /// and a retry that counted twice would silently ratchet the ladder.
    /// </summary>
    [HttpPost("unlocks")]
    public async Task<ActionResult<ChallengeLevelDto>> RecordUnlock(RecordSoloUnlockRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.RuleKey) || string.IsNullOrWhiteSpace(request.SessionKey))
            return BadRequest(new { message = "ruleKey and sessionKey are required." });
        if (!Kinds.Contains(request.Kind))
            return BadRequest(new { message = "Unknown challenge kind." });
        if (request.Level < 1)
            return BadRequest(new { message = "Level starts at 1." });

        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var existing = await db.SoloUnlocks
            .FirstOrDefaultAsync(u => u.Uid == uid && u.ClientEventId == request.ClientEventId);
        if (existing is not null)
            return await CurrentLevelAsync(uid, request.RuleKey, request.SessionKey);

        db.SoloUnlocks.Add(new SoloUnlock
        {
            Id = Guid.NewGuid(),
            Uid = uid,
            RuleKey = request.RuleKey,
            SessionKey = request.SessionKey,
            Level = request.Level,
            Kind = request.Kind,
            PackageName = request.PackageName,
            AppLabel = request.AppLabel,
            GrantedMinutes = request.GrantedMinutes,
            ClientEventId = request.ClientEventId,
            OccurredAtUtc = request.OccurredAtUtc,
            ReportedAtUtc = now
        });
        await db.SaveChangesAsync();

        await NotifyWatchersAsync(uid, request);
        return await CurrentLevelAsync(uid, request.RuleKey, request.SessionKey);
    }

    private async Task<ChallengeLevelDto> CurrentLevelAsync(string uid, string ruleKey, string sessionKey)
    {
        var level = await db.SoloUnlocks
            .Where(u => u.Uid == uid && u.RuleKey == ruleKey && u.SessionKey == sessionKey)
            .Select(u => (int?)u.Level)
            .MaxAsync() ?? 0;
        return new ChallengeLevelDto(ruleKey, sessionKey, level);
    }

    /// <summary>
    /// Partners and group co-members hear about it, stated flatly. This is the
    /// same audience the tamper alerts go to, and deliberately the same tone:
    /// a fact, not an accusation.
    /// </summary>
    private async Task NotifyWatchersAsync(string uid, RecordSoloUnlockRequest request)
    {
        var partnerUids = await db.Pairings
            .Where(p => p.Status == PairingStatus.Active && (p.UserAUid == uid || p.UserBUid == uid))
            .Select(p => p.UserAUid == uid ? p.UserBUid : p.UserAUid)
            .ToListAsync();

        var myGroupIds = await db.GroupMembers
            .Where(m => m.Uid == uid && m.Status == GroupMemberStatus.Active)
            .Select(m => m.GroupId)
            .ToListAsync();
        var coMemberUids = await db.GroupMembers
            .Where(m => myGroupIds.Contains(m.GroupId) && m.Uid != uid && m.Status == GroupMemberStatus.Active)
            .Select(m => m.Uid)
            .ToListAsync();

        var watchers = partnerUids.Concat(coMemberUids).Distinct().ToList();
        if (watchers.Count == 0) return;

        var name = Names.First((await db.Users.FindAsync(uid))?.DisplayName, "Your partner");
        var body = $"{name} solved a {request.Kind.ToLowerInvariant()} challenge to open " +
                   $"{request.AppLabel} for {request.GrantedMinutes} minutes.";

        foreach (var watcherUid in watchers)
        {
            await notifier.NotifyAsync(
                watcherUid, "SOLO_UNLOCK",
                new
                {
                    uid,
                    kind = request.Kind,
                    packageName = request.PackageName,
                    appLabel = request.AppLabel,
                    level = request.Level,
                    grantedMinutes = request.GrantedMinutes,
                    occurredAtUtc = request.OccurredAtUtc
                },
                "Unlock challenge passed", body);
        }
    }
}
