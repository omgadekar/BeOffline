using System.Diagnostics;
using System.Security.Claims;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;

namespace BeOffline.Api.Logging;

/// <summary>
/// Activity log: one row per handled request (method, path, status, duration,
/// uid). Runs OUTERMOST and logs in a finally, so it records the outcome even
/// when a downstream exception is turned into a 500 by the error middleware.
///
/// Writes on a fresh DI scope so it never touches the request's own DbContext,
/// and swallows its own failures — logging must never break a real request.
/// Metadata only: no bodies, headers, or tokens are ever persisted.
/// </summary>
public sealed class RequestLoggingMiddleware(
    RequestDelegate next,
    IServiceScopeFactory scopeFactory,
    IConfiguration config,
    ILogger<RequestLoggingMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context)
    {
        var sw = Stopwatch.StartNew();
        try
        {
            await next(context);
        }
        finally
        {
            sw.Stop();
            if (ShouldLog(context))
            {
                await SafeWriteAsync(context, sw.ElapsedMilliseconds);
            }
        }
    }

    private bool ShouldLog(HttpContext context)
    {
        // Skip health checks and the SignalR hub (long-lived WebSocket, not a call).
        var path = context.Request.Path.Value ?? string.Empty;
        if (!config.GetValue("Logging:ActivityLogEnabled", true)) return false;
        return path != "/health" && !path.StartsWith("/hubs", StringComparison.OrdinalIgnoreCase);
    }

    private async Task SafeWriteAsync(HttpContext context, long durationMs)
    {
        try
        {
            await using var scope = scopeFactory.CreateAsyncScope();
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            db.ActivityLogs.Add(new ActivityLog
            {
                Uid = context.User.FindFirstValue(ClaimTypes.NameIdentifier)
                      ?? context.User.FindFirstValue("user_id"),
                Method = context.Request.Method,
                Path = Truncate(context.Request.Path.Value ?? string.Empty, 512),
                StatusCode = context.Response.StatusCode,
                DurationMs = durationMs,
                IpAddress = context.Connection.RemoteIpAddress?.ToString(),
                TimestampUtc = DateTime.UtcNow
            });
            await db.SaveChangesAsync();
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "Activity log write failed for {Method} {Path}",
                context.Request.Method, context.Request.Path);
        }
    }

    internal static string Truncate(string value, int max) =>
        value.Length <= max ? value : value[..max];
}
