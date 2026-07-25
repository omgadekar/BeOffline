using BeOffline.Api.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;

namespace BeOffline.Api.Hubs;

/// <summary>
/// Live delivery channel while the app is foregrounded (FCM covers background).
/// Server → client: single "event" method with (type, payloadJson) — the same
/// envelope FCM data messages use, so the client handles both paths uniformly.
/// </summary>
[Authorize]
public sealed class AccountabilityHub : Hub
{
    public override async Task OnConnectedAsync()
    {
        // One group per uid — NotificationService targets users, not connections.
        await Groups.AddToGroupAsync(Context.ConnectionId, Context.User!.Uid());
        await base.OnConnectedAsync();
    }
}
