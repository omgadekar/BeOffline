using System.Security.Claims;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;

namespace BeOffline.Api.Auth;

public static class AuthExtensions
{
    /// <summary>Firebase puts the UID in `sub` (mapped to NameIdentifier) and `user_id`.</summary>
    public static string Uid(this ClaimsPrincipal user) =>
        user.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? user.FindFirstValue("user_id")
            ?? throw new InvalidOperationException("Authenticated principal has no uid claim.");
}

/// <summary>
/// Auto-provisions an AppUser row on the first authenticated call (and keeps
/// LastSeenAtUtc fresh). There is no separate sign-up endpoint: possessing a
/// valid Firebase token IS the account.
/// </summary>
public sealed class UserProvisioningMiddleware(RequestDelegate next)
{
    public async Task InvokeAsync(HttpContext context, AppDbContext db)
    {
        if (context.User.Identity?.IsAuthenticated == true)
        {
            var uid = context.User.Uid();
            var now = DateTime.UtcNow;
            var user = await db.Users.FindAsync(uid);
            if (user is null)
            {
                db.Users.Add(new AppUser
                {
                    Uid = uid,
                    DisplayName = context.User.FindFirstValue("name"),
                    Email = context.User.FindFirstValue(ClaimTypes.Email)
                        ?? context.User.FindFirstValue("email"),
                    CreatedAtUtc = now,
                    LastSeenAtUtc = now
                });
                await db.SaveChangesAsync();
            }
            else if (now - user.LastSeenAtUtc > TimeSpan.FromMinutes(5))
            {
                user.LastSeenAtUtc = now;
                await db.SaveChangesAsync();
            }
        }

        await next(context);
    }
}
