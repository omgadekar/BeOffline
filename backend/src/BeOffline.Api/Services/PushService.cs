using BeOffline.Api.Data;
using FirebaseAdmin.Messaging;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Services;

public interface IPushService
{
    Task SendToUserAsync(string uid, string title, string body, IReadOnlyDictionary<string, string> data, CancellationToken ct = default);
}

/// <summary>
/// FCM via the Firebase Admin SDK. Data payload always carries the event
/// envelope so the client can act even when the notification is swiped away.
/// </summary>
public sealed class FcmPushService(IServiceScopeFactory scopeFactory, ILogger<FcmPushService> logger) : IPushService
{
    public async Task SendToUserAsync(string uid, string title, string body, IReadOnlyDictionary<string, string> data, CancellationToken ct = default)
    {
        await using var scope = scopeFactory.CreateAsyncScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var tokens = await db.Devices
            .Where(d => d.Uid == uid)
            .Select(d => d.FcmToken)
            .Distinct()
            .ToListAsync(ct);
        if (tokens.Count == 0) return;

        // FirebaseAdmin marks Token obsolete in favor of FIDs, but registration
        // tokens are what the Android client registers and remain fully
        // supported by FCM — revisit if/when the client moves to FIDs.
#pragma warning disable CS0618
        var messages = tokens.Select(token => new Message
        {
            Token = token,
            Notification = new Notification { Title = title, Body = body },
            Data = data.ToDictionary(kv => kv.Key, kv => kv.Value),
            Android = new AndroidConfig { Priority = Priority.High }
        }).ToList();
#pragma warning restore CS0618

        try
        {
            var response = await FirebaseMessaging.DefaultInstance.SendEachAsync(messages, ct);
            if (response.FailureCount > 0)
            {
                logger.LogWarning("FCM: {Failed}/{Total} sends failed for uid {Uid}", response.FailureCount, tokens.Count, uid);
            }
        }
        catch (Exception ex)
        {
            // Push failure must never fail the API call that triggered it.
            logger.LogError(ex, "FCM send failed for uid {Uid}", uid);
        }
    }
}

/// <summary>Used when no Firebase service-account is configured (local dev, tests).</summary>
public sealed class NullPushService(ILogger<NullPushService> logger) : IPushService
{
    public Task SendToUserAsync(string uid, string title, string body, IReadOnlyDictionary<string, string> data, CancellationToken ct = default)
    {
        logger.LogInformation("Push (disabled) to {Uid}: {Title} — {Body}", uid, title, body);
        return Task.CompletedTask;
    }
}
