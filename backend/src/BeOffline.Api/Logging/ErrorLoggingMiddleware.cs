using System.Security.Claims;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;

namespace BeOffline.Api.Logging;

/// <summary>
/// Catches any unhandled exception, persists an ErrorLog row, and returns a
/// generic 500 (never leaking a stack trace to the caller). Sits INSIDE the
/// activity middleware so that request still gets logged with status 500.
/// </summary>
public sealed class ErrorLoggingMiddleware(
    RequestDelegate next,
    IServiceScopeFactory scopeFactory,
    ILogger<ErrorLoggingMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await next(context);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Unhandled exception for {Method} {Path}",
                context.Request.Method, context.Request.Path);
            await SafeWriteAsync(context, ex);

            // If the response already started we can't rewrite it — rethrow so
            // the host tears the connection down; the error is already logged.
            if (context.Response.HasStarted) throw;

            context.Response.Clear();
            context.Response.StatusCode = StatusCodes.Status500InternalServerError;
            await context.Response.WriteAsJsonAsync(new { message = "An unexpected error occurred." });
        }
    }

    private async Task SafeWriteAsync(HttpContext context, Exception ex)
    {
        try
        {
            await using var scope = scopeFactory.CreateAsyncScope();
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            db.ErrorLogs.Add(new ErrorLog
            {
                Uid = context.User.FindFirstValue(ClaimTypes.NameIdentifier)
                      ?? context.User.FindFirstValue("user_id"),
                Method = context.Request.Method,
                Path = RequestLoggingMiddleware.Truncate(context.Request.Path.Value ?? string.Empty, 512),
                Message = RequestLoggingMiddleware.Truncate(ex.Message, 2000),
                ExceptionType = ex.GetType().FullName,
                StackTrace = ex.StackTrace,
                TimestampUtc = DateTime.UtcNow
            });
            await db.SaveChangesAsync();
        }
        catch (Exception logEx)
        {
            logger.LogWarning(logEx, "Error log write failed");
        }
    }
}
