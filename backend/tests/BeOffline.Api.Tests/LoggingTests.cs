using System.Net;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Tests;

public sealed class LoggingTests : IClassFixture<TestAppFactory>
{
    private readonly TestAppFactory _factory;

    public LoggingTests(TestAppFactory factory)
    {
        _factory = factory;
        _factory.EnsureDatabase();
    }

    [Fact]
    public async Task AuthenticatedRequest_isRecorded_inActivityLog()
    {
        var client = _factory.ClientFor("log-user-1");
        (await client.GetAsync("/api/pairing")).EnsureSuccessStatusCode();

        _factory.WithDb(db =>
        {
            var log = db.ActivityLogs.AsNoTracking()
                .Where(l => l.Path == "/api/pairing" && l.Uid == "log-user-1")
                .OrderByDescending(l => l.Id)
                .FirstOrDefault();
            Assert.NotNull(log);
            Assert.Equal("GET", log!.Method);
            Assert.Equal(200, log.StatusCode);
            Assert.True(log.DurationMs >= 0);
        });
    }

    [Fact]
    public async Task UnauthenticatedRequest_isRecorded_withNullUid_and401()
    {
        var client = _factory.CreateClient(); // no X-Test-Uid header → 401
        Assert.Equal(HttpStatusCode.Unauthorized, (await client.GetAsync("/api/pairing")).StatusCode);

        _factory.WithDb(db =>
        {
            var log = db.ActivityLogs.AsNoTracking()
                .Where(l => l.Path == "/api/pairing" && l.StatusCode == 401)
                .OrderByDescending(l => l.Id)
                .FirstOrDefault();
            Assert.NotNull(log);
            Assert.Null(log!.Uid);
        });
    }

    [Fact]
    public async Task HealthCheck_isNotLogged()
    {
        (await _factory.CreateClient().GetAsync("/health")).EnsureSuccessStatusCode();

        _factory.WithDb(db =>
            Assert.False(db.ActivityLogs.AsNoTracking().Any(l => l.Path == "/health")));
    }
}
