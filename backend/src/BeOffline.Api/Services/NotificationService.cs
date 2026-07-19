using System.Text.Json;
using BeOffline.Api.Hubs;
using Microsoft.AspNetCore.SignalR;

namespace BeOffline.Api.Services;

/// <summary>
/// Fan-out to one user over both channels: SignalR (instant, foregrounded)
/// and FCM (wakes the app when backgrounded). Event types are the shared
/// vocabulary with the Android client:
///   UNLOCK_REQUEST, REQUEST_APPROVED, REQUEST_DENIED, REQUEST_EXPIRED,
///   INVITE_ACCEPTED, PARTNER_REMOVAL_STARTED, PARTNER_REMOVED, TAMPER_ALERT
/// </summary>
public interface INotificationService
{
    Task NotifyAsync(string uid, string type, object payload, string title, string body, CancellationToken ct = default);
}

public sealed class NotificationService(
    IHubContext<AccountabilityHub> hub,
    IPushService push,
    ILogger<NotificationService> logger) : INotificationService
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public async Task NotifyAsync(string uid, string type, object payload, string title, string body, CancellationToken ct = default)
    {
        var json = JsonSerializer.Serialize(payload, JsonOptions);

        try
        {
            await hub.Clients.Group(uid).SendAsync("event", type, json, ct);
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "SignalR delivery failed for {Uid}/{Type}", uid, type);
        }

        await push.SendToUserAsync(
            uid, title, body,
            new Dictionary<string, string> { ["type"] = type, ["payload"] = json },
            ct);
    }
}
