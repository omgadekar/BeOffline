using BeOffline.Api.Contracts;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Services;

/// <summary>
/// Periodic housekeeping (single-instance deployment; runs in-process):
///  1. Expire PENDING unlock requests past their deadline (fail-closed: an
///     unanswered request simply lapses and the app stays blocked).
///  2. Finalize pairing removals whose cooldown has elapsed.
///  3. Uninstall inference: a paired user whose devices have all gone silent
///     past the threshold gets a TAMPER_ALERT sent to their partners — the
///     "bypass is visible" core mechanic (uninstall can't be blocked, only surfaced).
/// </summary>
public sealed class SweepService(IServiceScopeFactory scopeFactory, IConfiguration config, ILogger<SweepService> logger)
    : BackgroundService
{
    private DateTime _lastHeartbeatSweepUtc = DateTime.MinValue;

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var interval = TimeSpan.FromSeconds(config.GetValue("Accountability:SweepIntervalSeconds", 60));
        using var timer = new PeriodicTimer(interval);

        while (await timer.WaitForNextTickAsync(stoppingToken).ConfigureAwait(false))
        {
            try
            {
                await RunOnceAsync(stoppingToken);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Sweep iteration failed");
            }
        }
    }

    public async Task RunOnceAsync(CancellationToken ct)
    {
        await using var scope = scopeFactory.CreateAsyncScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var notifier = scope.ServiceProvider.GetRequiredService<INotificationService>();
        var now = DateTime.UtcNow;

        // 1. Expire overdue pending requests.
        var expired = await db.UnlockRequests
            .Where(r => r.Status == UnlockRequestStatus.Pending && r.ExpiresAtUtc <= now)
            .ToListAsync(ct);
        foreach (var request in expired)
        {
            request.Status = UnlockRequestStatus.Expired;
            request.ResolvedAtUtc = now;
        }
        if (expired.Count > 0)
        {
            await db.SaveChangesAsync(ct);
            foreach (var request in expired)
            {
                await notifier.NotifyAsync(
                    request.RequesterUid, "REQUEST_EXPIRED", UnlockRequestDto.From(request),
                    "Request expired", $"No one responded in time — {request.AppLabel} stays blocked.", ct);
            }
        }

        // 2. Finalize removals past cooldown.
        var toRemove = await db.Pairings
            .Where(p => p.Status == PairingStatus.Active && p.RemovalEffectiveAtUtc != null && p.RemovalEffectiveAtUtc <= now)
            .ToListAsync(ct);
        foreach (var pairing in toRemove)
        {
            pairing.Status = PairingStatus.Removed;
        }
        if (toRemove.Count > 0)
        {
            await db.SaveChangesAsync(ct);
            foreach (var pairing in toRemove)
            {
                foreach (var uid in new[] { pairing.UserAUid, pairing.UserBUid })
                {
                    await notifier.NotifyAsync(
                        uid, "PARTNER_REMOVED", new { pairingId = pairing.Id },
                        "Pairing ended", "An accountability pairing has ended.", ct);
                }
            }
        }

        // 2b. Finalize group-member removals past cooldown (owner leaving hands
        // the group to the longest-standing remaining member).
        var memberRemovals = await db.GroupMembers
            .Include(m => m.Group)
            .Where(m => m.Status == GroupMemberStatus.Active && m.RemovalEffectiveAtUtc != null && m.RemovalEffectiveAtUtc <= now)
            .ToListAsync(ct);
        if (memberRemovals.Count > 0)
        {
            var removingIds = memberRemovals.Select(m => m.Id).ToHashSet();
            foreach (var member in memberRemovals)
            {
                member.Status = GroupMemberStatus.Removed;
                if (member.Group!.OwnerUid == member.Uid)
                {
                    var heir = await db.GroupMembers
                        .Where(m => m.GroupId == member.GroupId && m.Status == GroupMemberStatus.Active &&
                                    !removingIds.Contains(m.Id))
                        .OrderBy(m => m.JoinedAtUtc)
                        .FirstOrDefaultAsync(ct);
                    if (heir is not null) member.Group.OwnerUid = heir.Uid;
                }
            }
            await db.SaveChangesAsync(ct);
            foreach (var member in memberRemovals)
            {
                var remaining = await db.GroupMembers
                    .Where(m => m.GroupId == member.GroupId && m.Status == GroupMemberStatus.Active)
                    .Select(m => m.Uid)
                    .ToListAsync(ct);
                foreach (var uid in remaining.Append(member.Uid))
                {
                    await notifier.NotifyAsync(
                        uid, "GROUP_MEMBER_LEFT",
                        new { groupId = member.GroupId, groupName = member.Group!.Name, uid = member.Uid },
                        member.Group.Name, "A membership in this group has ended.", ct);
                }
            }
        }

        // 3. Hourly: uninstall inference from silent heartbeats + log retention.
        if (now - _lastHeartbeatSweepUtc >= TimeSpan.FromHours(1))
        {
            _lastHeartbeatSweepUtc = now;
            await SweepSilentUsersAsync(db, notifier, now, ct);
            await PruneLogsAsync(db, now, ct);
        }
    }

    private async Task PruneLogsAsync(AppDbContext db, DateTime now, CancellationToken ct)
    {
        var cutoff = now - TimeSpan.FromDays(config.GetValue("Logging:RetentionDays", 30));
        try
        {
            await db.ActivityLogs.Where(l => l.TimestampUtc < cutoff).ExecuteDeleteAsync(ct);
            await db.ErrorLogs.Where(l => l.TimestampUtc < cutoff).ExecuteDeleteAsync(ct);
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "Log retention prune failed");
        }
    }

    private async Task SweepSilentUsersAsync(AppDbContext db, INotificationService notifier, DateTime now, CancellationToken ct)
    {
        var threshold = now - TimeSpan.FromHours(GetThresholdHours());

        // Who watches whom: pairing partners plus co-members of shared groups.
        var watchers = new Dictionary<string, HashSet<string>>();
        void AddEdge(string a, string b)
        {
            (watchers.TryGetValue(a, out var setA) ? setA : watchers[a] = []).Add(b);
            (watchers.TryGetValue(b, out var setB) ? setB : watchers[b] = []).Add(a);
        }

        var pairedUids = await db.Pairings
            .Where(p => p.Status == PairingStatus.Active)
            .Select(p => new { p.UserAUid, p.UserBUid })
            .ToListAsync(ct);
        foreach (var p in pairedUids)
            AddEdge(p.UserAUid, p.UserBUid);

        var groupLinks = await db.GroupMembers
            .Where(m => m.Status == GroupMemberStatus.Active)
            .Select(m => new { m.GroupId, m.Uid })
            .ToListAsync(ct);
        foreach (var group in groupLinks.GroupBy(m => m.GroupId))
        {
            var members = group.Select(m => m.Uid).Distinct().ToList();
            for (var i = 0; i < members.Count; i++)
                for (var j = i + 1; j < members.Count; j++)
                    AddEdge(members[i], members[j]);
        }

        foreach (var (uid, whoWatches) in watchers)
        {
            var user = await db.Users.FindAsync([uid], ct);
            if (user is null || user.UninstallNotifiedAtUtc != null) continue;

            var lastBeat = await db.Devices
                .Where(d => d.Uid == uid)
                .MaxAsync(d => (DateTime?)d.LastHeartbeatAtUtc, ct);
            // No devices registered at all → nothing to infer from yet.
            if (lastBeat is null || lastBeat > threshold) continue;

            user.UninstallNotifiedAtUtc = now;
            await db.SaveChangesAsync(ct);

            foreach (var watcherUid in whoWatches)
            {
                await notifier.NotifyAsync(
                    watcherUid, "TAMPER_ALERT",
                    new { uid, type = "APP_UNINSTALLED_SUSPECTED", lastSeenUtc = lastBeat },
                    "Protection alert",
                    $"{user.DisplayName ?? "Your partner"}'s BeOffline has gone silent — the app may have been uninstalled.", ct);
            }
        }
    }

    private int GetThresholdHours() => config.GetValue("Accountability:UninstallHeartbeatThresholdHours", 48);
}
