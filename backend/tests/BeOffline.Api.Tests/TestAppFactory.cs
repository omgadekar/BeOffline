using System.Security.Claims;
using System.Text.Encodings.Web;
using BeOffline.Api.Data;
using BeOffline.Api.Services;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace BeOffline.Api.Tests;

/// <summary>
/// Boots the real app in the Testing environment with:
///  - SQLite in-memory instead of Postgres (connection held open for lifetime)
///  - a header-based fake auth scheme (X-Test-Uid)
///  - a recording push service instead of FCM
/// </summary>
public sealed class TestAppFactory : WebApplicationFactory<Program>
{
    private readonly SqliteConnection _connection = new("DataSource=:memory:");
    public RecordingPushService Push { get; } = new();

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Testing");
        _connection.Open();

        builder.ConfigureTestServices(services =>
        {
            services.AddDbContext<AppDbContext>(o => o.UseSqlite(_connection));

            services.AddAuthentication(TestAuthHandler.SchemeName)
                .AddScheme<AuthenticationSchemeOptions, TestAuthHandler>(TestAuthHandler.SchemeName, _ => { });
            services.PostConfigure<AuthenticationOptions>(o =>
            {
                o.DefaultAuthenticateScheme = TestAuthHandler.SchemeName;
                o.DefaultChallengeScheme = TestAuthHandler.SchemeName;
            });

            services.AddSingleton<IPushService>(Push);
        });
    }

    /// <summary>Client authenticated as the given fake uid (auto-provisions on first call).</summary>
    public HttpClient ClientFor(string uid)
    {
        var client = CreateClient();
        client.DefaultRequestHeaders.Add(TestAuthHandler.UidHeader, uid);
        return client;
    }

    public void EnsureDatabase()
    {
        using var scope = Services.CreateScope();
        scope.ServiceProvider.GetRequiredService<AppDbContext>().Database.EnsureCreated();
    }

    public void WithDb(Action<AppDbContext> action)
    {
        using var scope = Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        action(db);
        db.SaveChanges();
    }

    /// <summary>Runs one sweep iteration synchronously (the hosted loop ticks every 60s).</summary>
    public async Task RunSweepOnceAsync()
    {
        var sweep = new SweepService(
            Services.GetRequiredService<IServiceScopeFactory>(),
            Services.GetRequiredService<Microsoft.Extensions.Configuration.IConfiguration>(),
            Services.GetRequiredService<ILogger<SweepService>>());
        await sweep.RunOnceAsync(CancellationToken.None);
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);
        _connection.Dispose();
    }
}

public sealed class TestAuthHandler(
    IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder)
    : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    public const string SchemeName = "Test";
    public const string UidHeader = "X-Test-Uid";

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        if (!Request.Headers.TryGetValue(UidHeader, out var uid) || string.IsNullOrWhiteSpace(uid))
            return Task.FromResult(AuthenticateResult.NoResult());

        var claims = new[]
        {
            new Claim(ClaimTypes.NameIdentifier, uid!),
            new Claim("name", $"User {uid}")
        };
        var identity = new ClaimsIdentity(claims, SchemeName);
        var ticket = new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName);
        return Task.FromResult(AuthenticateResult.Success(ticket));
    }
}

public sealed class RecordingPushService : IPushService
{
    public sealed record Sent(string Uid, string Type, string Title, string Body);

    private readonly List<Sent> _sent = [];
    public IReadOnlyList<Sent> All { get { lock (_sent) return _sent.ToList(); } }

    public Task SendToUserAsync(string uid, string title, string body, IReadOnlyDictionary<string, string> data, CancellationToken ct = default)
    {
        lock (_sent) _sent.Add(new Sent(uid, data.GetValueOrDefault("type", "?"), title, body));
        return Task.CompletedTask;
    }

    public List<Sent> For(string uid) { lock (_sent) return _sent.Where(s => s.Uid == uid).ToList(); }
}
