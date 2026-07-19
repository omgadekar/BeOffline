using BeOffline.Api.Auth;
using BeOffline.Api.Data;
using BeOffline.Api.Hubs;
using BeOffline.Api.Services;
using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);
var isTesting = builder.Environment.IsEnvironment("Testing");

// ── Database ─────────────────────────────────────────────────────────────────
// Postgres in real environments; the test factory swaps in SQLite in-memory.
if (!isTesting)
{
    builder.Services.AddDbContext<AppDbContext>(o =>
        o.UseNpgsql(builder.Configuration.GetConnectionString("Postgres")));
}

// ── Firebase Auth (identity) ─────────────────────────────────────────────────
// Standard OIDC validation of Firebase ID tokens — no Firebase SDK needed here.
var projectId = builder.Configuration["Firebase:ProjectId"] ?? "beoffline-36d68";
var authority = $"https://securetoken.google.com/{projectId}";

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.Authority = authority;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuer = authority,
            ValidateAudience = true,
            ValidAudience = projectId,
            ValidateLifetime = true
        };
        // SignalR sends the token as a query parameter on the WebSocket upgrade.
        options.Events = new JwtBearerEvents
        {
            OnMessageReceived = context =>
            {
                var accessToken = context.Request.Query["access_token"];
                if (!string.IsNullOrEmpty(accessToken) &&
                    context.HttpContext.Request.Path.StartsWithSegments("/hubs"))
                {
                    context.Token = accessToken;
                }
                return Task.CompletedTask;
            }
        };
    });
builder.Services.AddAuthorization();

// ── Firebase Admin (FCM push) ────────────────────────────────────────────────
// Degrades to a logging no-op when no service account is configured, so local
// dev and tests run without any Firebase secrets.
var serviceAccountPath = builder.Configuration["Firebase:ServiceAccountJsonPath"];
var fcmAvailable = !isTesting && !string.IsNullOrWhiteSpace(serviceAccountPath) && File.Exists(serviceAccountPath);
if (fcmAvailable)
{
    // FromFile is flagged obsolete in favor of a CredentialFactory API; the
    // file here is an operator-provisioned service account on our own host,
    // so the flagged path-handling risk does not apply.
#pragma warning disable CS0618
    FirebaseApp.Create(new AppOptions { Credential = GoogleCredential.FromFile(serviceAccountPath) });
#pragma warning restore CS0618
    builder.Services.AddSingleton<IPushService, FcmPushService>();
}
else
{
    builder.Services.AddSingleton<IPushService, NullPushService>();
}

builder.Services.AddSignalR();
builder.Services.AddSingleton<INotificationService, NotificationService>();
builder.Services.AddHostedService<SweepService>();
builder.Services.AddControllers();

var app = builder.Build();

if (!isTesting && app.Configuration.GetValue("Database:AutoMigrate", true))
{
    using var scope = app.Services.CreateScope();
    scope.ServiceProvider.GetRequiredService<AppDbContext>().Database.Migrate();
}

app.UseAuthentication();
app.UseMiddleware<UserProvisioningMiddleware>();
app.UseAuthorization();

app.MapControllers();
app.MapHub<AccountabilityHub>("/hubs/accountability");
app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

app.Run();

/// <summary>Exposed for WebApplicationFactory in integration tests.</summary>
public partial class Program;
