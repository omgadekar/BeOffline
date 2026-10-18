using System.Net;
using System.Net.Http.Json;
using BeOffline.Api.Contracts;
using BeOffline.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Tests;

/// <summary>
/// Play's UGC policy needs an in-app way to report a message and to get out of
/// a group. The reporting path writes a ModerationLog row to the activity log;
/// the "block" half is the existing owner-remove / self-leave endpoint, covered
/// in <see cref="GroupFlowTests"/> and re-checked here from a chat member's
/// point of view.
/// </summary>
public sealed class ChatModerationTests : IClassFixture<TestAppFactory>
{
    private readonly TestAppFactory _factory;

    public ChatModerationTests(TestAppFactory factory)
    {
        _factory = factory;
        _factory.EnsureDatabase();
    }

    private async Task<GroupDto> CreateGroupAsync(HttpClient owner, string name)
    {
        var response = await owner.PostAsJsonAsync("/api/groups", new CreateGroupRequest(name));
        response.EnsureSuccessStatusCode();
        return (await response.Content.ReadFromJsonAsync<GroupDto>())!;
    }

    private async Task JoinAsync(HttpClient inviter, HttpClient joiner, Guid groupId)
    {
        var invite = await (await inviter.PostAsync($"/api/groups/{groupId}/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        (await joiner.PostAsJsonAsync("/api/groups/join", new JoinGroupRequest(invite!.Code)))
            .EnsureSuccessStatusCode();
    }

    private async Task<ChatMessageDto> SendAsync(HttpClient client, Guid groupId, string clientId, string body)
    {
        var response = await client.PostAsJsonAsync($"/api/chat/groups/{groupId}/messages",
            new SendChatMessageRequest(clientId, body));
        response.EnsureSuccessStatusCode();
        return (await response.Content.ReadFromJsonAsync<ChatMessageDto>())!;
    }

    [Fact]
    public async Task Report_writesOneModerationRow_andIsIdempotent()
    {
        var alice = _factory.ClientFor("rep-alice");
        var bob = _factory.ClientFor("rep-bob");
        var group = await CreateGroupAsync(alice, "Reportable");
        await JoinAsync(alice, bob, group.Id);

        var message = await SendAsync(alice, group.Id, "rep-m1", "something unpleasant");

        var first = await bob.PostAsJsonAsync(
            $"/api/chat/groups/{group.Id}/messages/{message.Id}/reports",
            new ReportChatMessageRequest("harassment"));
        first.EnsureSuccessStatusCode();
        var dto = (await first.Content.ReadFromJsonAsync<ChatMessageReportDto>())!;
        Assert.Equal(message.Id, dto.MessageId);
        Assert.Equal("rep-alice", dto.SenderUid);
        Assert.False(dto.AlreadyReported);

        // The row carries who, what and why — without copying the reported text.
        var path = ModerationLog.PathFor(group.Id, message.Id);
        _factory.WithDb(db =>
        {
            var row = db.ActivityLogs.AsNoTracking()
                .Single(l => l.Method == ModerationLog.Method && l.Uid == "rep-bob" && l.Path.StartsWith(path));
            Assert.Contains("reason=harassment", row.Path);
            Assert.Contains("sender=rep-alice", row.Path);
            Assert.DoesNotContain("unpleasant", row.Path);
        });

        // Tapping report twice (or a retried request) must not pile up rows.
        var again = await bob.PostAsJsonAsync(
            $"/api/chat/groups/{group.Id}/messages/{message.Id}/reports",
            new ReportChatMessageRequest("spam"));
        again.EnsureSuccessStatusCode();
        Assert.True((await again.Content.ReadFromJsonAsync<ChatMessageReportDto>())!.AlreadyReported);

        _factory.WithDb(db => Assert.Equal(1, db.ActivityLogs.AsNoTracking()
            .Count(l => l.Method == ModerationLog.Method && l.Path.StartsWith(path))));
    }

    [Fact]
    public async Task Report_rejectsUnknownReason_ownMessage_outsiders_andOtherGroupsMessages()
    {
        var alice = _factory.ClientFor("rep2-alice");
        var bob = _factory.ClientFor("rep2-bob");
        var outsider = _factory.ClientFor("rep2-out");

        var group = await CreateGroupAsync(alice, "Rejections");
        await JoinAsync(alice, bob, group.Id);
        var message = await SendAsync(alice, group.Id, "rep2-m1", "hello");

        Assert.Equal(HttpStatusCode.BadRequest, (await bob.PostAsJsonAsync(
            $"/api/chat/groups/{group.Id}/messages/{message.Id}/reports",
            new ReportChatMessageRequest("because-i-say-so"))).StatusCode);

        // Reporting yourself is a mis-tap, not a report.
        Assert.Equal(HttpStatusCode.BadRequest, (await alice.PostAsJsonAsync(
            $"/api/chat/groups/{group.Id}/messages/{message.Id}/reports",
            new ReportChatMessageRequest("spam"))).StatusCode);

        Assert.Equal(HttpStatusCode.NotFound, (await outsider.PostAsJsonAsync(
            $"/api/chat/groups/{group.Id}/messages/{message.Id}/reports",
            new ReportChatMessageRequest("spam"))).StatusCode);

        // A real message id, but reported through a group it does not belong to.
        var otherGroup = await CreateGroupAsync(bob, "Elsewhere");
        Assert.Equal(HttpStatusCode.NotFound, (await bob.PostAsJsonAsync(
            $"/api/chat/groups/{otherGroup.Id}/messages/{message.Id}/reports",
            new ReportChatMessageRequest("spam"))).StatusCode);

        _factory.WithDb(db => Assert.False(db.ActivityLogs.AsNoTracking()
            .Any(l => l.Method == ModerationLog.Method
                      && l.Path.StartsWith(ModerationLog.PathFor(group.Id, message.Id)))));
    }

    [Fact]
    public async Task Reports_surviveTheLogRetentionSweep_thatPrunesOrdinaryRequestRows()
    {
        var alice = _factory.ClientFor("rep3-alice");
        var bob = _factory.ClientFor("rep3-bob");
        var group = await CreateGroupAsync(alice, "Retention");
        await JoinAsync(alice, bob, group.Id);
        var message = await SendAsync(alice, group.Id, "rep3-m1", "old trouble");

        (await bob.PostAsJsonAsync(
            $"/api/chat/groups/{group.Id}/messages/{message.Id}/reports",
            new ReportChatMessageRequest("hate"))).EnsureSuccessStatusCode();

        // Age everything past the 30-day window the sweep prunes against.
        var longAgo = DateTime.UtcNow.AddDays(-120);
        _factory.WithDb(db =>
        {
            foreach (var row in db.ActivityLogs) row.TimestampUtc = longAgo;
        });

        await _factory.RunSweepOnceAsync();

        var path = ModerationLog.PathFor(group.Id, message.Id);
        _factory.WithDb(db =>
        {
            Assert.True(db.ActivityLogs.AsNoTracking()
                .Any(l => l.Method == ModerationLog.Method && l.Path.StartsWith(path)));
            Assert.False(db.ActivityLogs.AsNoTracking()
                .Any(l => l.Method != ModerationLog.Method && l.TimestampUtc <= longAgo));
        });
    }

    [Fact]
    public async Task OwnerCanRemoveAReportedMember_andAnyMemberCanLeaveFromTheChat()
    {
        var owner = _factory.ClientFor("mod-owner");
        var troll = _factory.ClientFor("mod-troll");
        var bystander = _factory.ClientFor("mod-bystander");

        var group = await CreateGroupAsync(owner, "Moderated");
        await JoinAsync(owner, troll, group.Id);
        await JoinAsync(owner, bystander, group.Id);

        // A plain member cannot remove anyone else...
        Assert.Equal(HttpStatusCode.Forbidden,
            (await bystander.DeleteAsync($"/api/groups/{group.Id}/members/mod-troll")).StatusCode);

        // ...but can always leave, which is the chat's own escape hatch.
        (await bystander.DeleteAsync($"/api/groups/{group.Id}/members/mod-bystander"))
            .EnsureSuccessStatusCode();

        // The owner can remove the reported member.
        (await owner.DeleteAsync($"/api/groups/{group.Id}/members/mod-troll"))
            .EnsureSuccessStatusCode();

        _factory.WithDb(db =>
        {
            var removed = db.GroupMembers.AsNoTracking()
                .Single(m => m.GroupId == group.Id && m.Uid == "mod-troll");
            Assert.Equal("mod-owner", removed.RemovalRequestedByUid);
            Assert.NotNull(removed.RemovalEffectiveAtUtc);
        });
        Assert.Contains(_factory.Push.For("mod-troll"), s => s.Type == "GROUP_MEMBER_REMOVAL_STARTED");
    }
}
