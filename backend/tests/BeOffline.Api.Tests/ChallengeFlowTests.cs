using System.Net;
using System.Net.Http.Json;
using BeOffline.Api.Contracts;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Tests;

public sealed class ChallengeFlowTests : IClassFixture<TestAppFactory>
{
    private readonly TestAppFactory _factory;

    public ChallengeFlowTests(TestAppFactory factory)
    {
        _factory = factory;
        _factory.EnsureDatabase();
    }

    private static RecordSoloUnlockRequest Unlock(
        string eventId, int level, string sessionKey = "timer-1", string kind = "Arithmetic") =>
        new(eventId, "rule-1", sessionKey, level, kind, "com.instagram.android", "Instagram", 5, DateTime.UtcNow);

    [Fact]
    public async Task UnseenSession_startsTheLadderAtZero()
    {
        var client = _factory.ClientFor("ch-fresh");

        var level = await (await client.GetAsync("/api/challenges/level?ruleKey=rule-9&sessionKey=never-seen"))
            .Content.ReadFromJsonAsync<ChallengeLevelDto>();

        Assert.NotNull(level);
        Assert.Equal(0, level!.Level);
    }

    [Fact]
    public async Task RecordedUnlocks_raiseTheLevel_forThatSessionOnly()
    {
        var client = _factory.ClientFor("ch-ladder");

        (await client.PostAsJsonAsync("/api/challenges/unlocks", Unlock("e1", 1))).EnsureSuccessStatusCode();
        var second = await (await client.PostAsJsonAsync("/api/challenges/unlocks", Unlock("e2", 2)))
            .Content.ReadFromJsonAsync<ChallengeLevelDto>();
        Assert.Equal(2, second!.Level);

        // A new focus session is a clean slate — that's the whole point of the key.
        var otherSession = await (await client
            .GetAsync("/api/challenges/level?ruleKey=rule-1&sessionKey=timer-2"))
            .Content.ReadFromJsonAsync<ChallengeLevelDto>();
        Assert.Equal(0, otherSession!.Level);
    }

    [Fact]
    public async Task ReplayedEvent_doesNotDoubleCount()
    {
        var client = _factory.ClientFor("ch-replay");

        (await client.PostAsJsonAsync("/api/challenges/unlocks", Unlock("dupe", 1))).EnsureSuccessStatusCode();
        // The offline outbox retries; counting the retry would silently ratchet
        // the ladder and make the next unlock harder than it earned.
        var replay = await (await client.PostAsJsonAsync("/api/challenges/unlocks", Unlock("dupe", 1)))
            .Content.ReadFromJsonAsync<ChallengeLevelDto>();

        Assert.Equal(1, replay!.Level);
        _factory.WithDb(db =>
            Assert.Equal(1, db.SoloUnlocks.AsNoTracking().Count(u => u.Uid == "ch-replay")));
    }

    [Fact]
    public async Task LadderIsScopedToTheAccount()
    {
        var mine = _factory.ClientFor("ch-mine");
        var theirs = _factory.ClientFor("ch-theirs");

        (await mine.PostAsJsonAsync("/api/challenges/unlocks", Unlock("shared-key", 3)))
            .EnsureSuccessStatusCode();

        // Same ruleKey and sessionKey, different account: rule keys are per-device
        // row ids, so they collide constantly and must never leak across users.
        var level = await (await theirs.GetAsync("/api/challenges/level?ruleKey=rule-1&sessionKey=timer-1"))
            .Content.ReadFromJsonAsync<ChallengeLevelDto>();
        Assert.Equal(0, level!.Level);
    }

    [Fact]
    public async Task UnknownKind_isRejected()
    {
        var client = _factory.ClientFor("ch-bad-kind");
        var response = await client.PostAsJsonAsync(
            "/api/challenges/unlocks", Unlock("bad", 1, kind: "Sudoku"));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task MissingKeys_areRejected()
    {
        var client = _factory.ClientFor("ch-no-keys");
        var response = await client.GetAsync("/api/challenges/level?ruleKey=&sessionKey=");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task Unauthenticated_isRejected()
    {
        var client = _factory.CreateClient();
        var response = await client.GetAsync("/api/challenges/level?ruleKey=rule-1&sessionKey=timer-1");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Partner_isToldAboutTheUnlock_byFirstNameOnly()
    {
        var solver = _factory.ClientFor("ch-solver");
        var watcher = _factory.ClientFor("ch-watcher");

        var invite = await (await solver.PostAsync("/api/pairing/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        (await watcher.PostAsJsonAsync("/api/pairing/invites/accept", new AcceptInviteRequest(invite!.Code)))
            .EnsureSuccessStatusCode();

        (await solver.PostAsJsonAsync("/api/challenges/unlocks", Unlock("watched", 1, kind: "Pattern")))
            .EnsureSuccessStatusCode();

        var sent = _factory.Push.For("ch-watcher").LastOrDefault(s => s.Type == "SOLO_UNLOCK");
        Assert.NotNull(sent);
        // The fake auth handler names everyone "User {uid}", so the first name is
        // "User" — the surname must not survive into the notification.
        Assert.StartsWith("User ", sent!.Body);
        Assert.DoesNotContain("ch-solver", sent.Body);
        Assert.Contains("pattern challenge", sent.Body);
        Assert.Contains("Instagram", sent.Body);
    }

    [Fact]
    public async Task DeletingTheAccount_removesTheLadder()
    {
        var client = _factory.ClientFor("ch-deleter");
        (await client.PostAsJsonAsync("/api/challenges/unlocks", Unlock("gone", 1))).EnsureSuccessStatusCode();

        (await client.DeleteAsync("/api/account")).EnsureSuccessStatusCode();

        _factory.WithDb(db =>
            Assert.Empty(db.SoloUnlocks.AsNoTracking().Where(u => u.Uid == "ch-deleter")));
    }
}
