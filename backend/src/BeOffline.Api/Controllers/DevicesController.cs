using BeOffline.Api.Auth;
using BeOffline.Api.Contracts;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/devices")]
public sealed class DevicesController(AppDbContext db) : ControllerBase
{
    [HttpPut]
    public async Task<IActionResult> Register(RegisterDeviceRequest request)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var device = await db.Devices.FirstOrDefaultAsync(d => d.Uid == uid && d.DeviceId == request.DeviceId);
        if (device is null)
        {
            db.Devices.Add(new Device
            {
                Id = Guid.NewGuid(),
                Uid = uid,
                DeviceId = request.DeviceId,
                Model = request.Model,
                FcmToken = request.FcmToken,
                LastHeartbeatAtUtc = now
            });
        }
        else
        {
            device.FcmToken = request.FcmToken;
            device.Model = request.Model ?? device.Model;
            device.LastHeartbeatAtUtc = now;
        }

        await ClearUninstallFlagAsync(uid);
        await db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>
    /// Liveness signal (client sends it periodically via WorkManager). Its
    /// ABSENCE is the point: the sweep infers uninstall from silence and tells
    /// the partner — the accountability answer to "you can't block uninstall".
    /// </summary>
    [HttpPost("heartbeat")]
    public async Task<IActionResult> Heartbeat(HeartbeatRequest request)
    {
        var uid = User.Uid();
        var device = await db.Devices.FirstOrDefaultAsync(d => d.Uid == uid && d.DeviceId == request.DeviceId);
        if (device is null) return NotFound(new { message = "Device not registered." });

        device.LastHeartbeatAtUtc = DateTime.UtcNow;
        await ClearUninstallFlagAsync(uid);
        await db.SaveChangesAsync();
        return NoContent();
    }

    private async Task ClearUninstallFlagAsync(string uid)
    {
        var user = await db.Users.FindAsync(uid);
        if (user?.UninstallNotifiedAtUtc != null)
        {
            // The app is alive again — allow a future silence to re-trigger the alert.
            user.UninstallNotifiedAtUtc = null;
        }
    }
}
