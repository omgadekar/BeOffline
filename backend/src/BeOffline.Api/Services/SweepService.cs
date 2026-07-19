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

        // 3. Hourly: uninstall inference from silent heartbeats.
        if (now - _lastHeartbeatSweepUtc >= TimeSpan.FromHours(1))
        {
            _lastHeartbeatSweepUtc = now;
            await SweepSilentUsersAsync(db, notifier, now, ct);
        }
    }

    private async Task SweepSilentUsersAsync(AppDbContext db, INotificationService notifier, DateTime now, CancellationToken ct)
    {
        var threshold = now - TimeSpan.FromHours(GetThresholdHours());

        var pairedUids = await db.Pairings
            .Where(p => p.Status == PairingStatus.Active)
            .Select(p => new { p.UserAUid, p.UserBUid })
            .ToListAsync(ct);
        var uids = pairedUids.SelectMany(p => new[] { p.UserAUid, p.UserBUid }).Distinct().ToList();

        foreach (var uid in uids)
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

            var partners = pairedUids
                .Where(p => p.UserAUid == uid || p.UserBUid == uid)
                .Select(p => p.UserAUid == uid ? p.UserBUid : p.UserAUid)
                .Distinct();
            foreach (var partnerUid in partners)
            {
                await notifier.NotifyAsync(
                    partnerUid, "TAMPER_ALERT",
                    new { uid, type = "APP_UNINSTALLED_SUSPECTED", lastSeenUtc = lastBeat },
                    "Protection alert",
                    $"{user.DisplayName ?? "Your partner"}'s BeOffline has gone silent — the app may have been uninstalled.", ct);
            }
        }
    }

    private int GetThresholdHours() => config.GetValue("Accountability:UninstallHeartbeatThresholdHours", 48);
}
