using System.Net;
using System.Net.Http.Json;
using BeOffline.Api.Contracts;
using BeOffline.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Tests;

public sealed class GroupFlowTests : IClassFixture<TestAppFactory>
{
    private readonly TestAppFactory _factory;

    public GroupFlowTests(TestAppFactory factory)
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

    private async Task<GroupDto> JoinAsync(HttpClient inviter, HttpClient joiner, Guid groupId)
    {
        var invite = await (await inviter.PostAsync($"/api/groups/{groupId}/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        var joinResponse = await joiner.PostAsJsonAsync("/api/groups/join", new JoinGroupRequest(invite!.Code));
        joinResponse.EnsureSuccessStatusCode();
        return (await joinResponse.Content.ReadFromJsonAsync<GroupDto>())!;
    }

    /// <summary>Clears the anti-puppet cooldown so approval-path tests don't wait 24h.</summary>
    private void ActivateAllMembers(Guid groupId) => _factory.WithDb(db =>
    {
        foreach (var m in db.GroupMembers.Where(m => m.GroupId == groupId))
            m.CanApproveAfterUtc = DateTime.UtcNow.AddMinutes(-1);
    });

    [Fact]
    public async Task CreateAndJoin_firstRelationship_activatesImmediately_laterMembersGetCooldown()
    {
        var owner = _factory.ClientFor("g-owner1");
        var second = _factory.ClientFor("g-second1");
        var third = _factory.ClientFor("g-third1");

        var group = await CreateGroupAsync(owner, "Focus crew");
        Assert.Equal("g-owner1", group.OwnerUid);
        Assert.Single(group.Members);

        // First real relationship for both sides → immediate approval rights.
        var afterSecond = await JoinAsync(owner, second, group.Id);
        var secondMember = afterSecond.Members.Single(m => m.Uid == "g-second1");
        Assert.True(secondMember.CanApproveAfterUtc <= DateTime.UtcNow.AddSeconds(5));
        Assert.Contains(_factory.Push.For("g-owner1"), s => s.Type == "GROUP_MEMBER_JOINED");

        // The group now has real membership → the third member is cooldown-gated (anti-puppet).
        var afterThird = await JoinAsync(owner, third, group.Id);
        var thirdMember = afterThird.Members.Single(m => m.Uid == "g-third1");
        Assert.True(thirdMember.CanApproveAfterUtc > DateTime.UtcNow.AddHours(12),
            "a member added to an established group must not be able to approve immediately");

        // Both existing members heard about the join.
        Assert.Contains(_factory.Push.For("g-second1"), s => s.Type == "GROUP_MEMBER_JOINED");
    }

    [Fact]
    public async Task Join_whenGroupFull_isRejected()
    {
        var owner = _factory.ClientFor("g-full-owner");
        var group = await CreateGroupAsync(owner, "Full house");
        _factory.WithDb(db =>
        {
            for (var i = 0; i < 9; i++)
            {
                db.GroupMembers.Add(new GroupMember
                {
                    Id = Guid.NewGuid(),
                    GroupId = group.Id,
                    Uid = $"g-filler{i}",
                    Status = GroupMemberStatus.Active,
                    JoinedAtUtc = DateTime.UtcNow,
                    CanApproveAfterUtc = DateTime.UtcNow
                });
            }
        });

        var late = _factory.ClientFor("g-late1");
        var invite = await (await owner.PostAsync($"/api/groups/{group.Id}/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        var response = await late.PostAsJsonAsync("/api/groups/join", new JoinGroupRequest(invite!.Code));
        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
    }

    [Fact]
    public async Task GroupRequest_fansOut_firstResponderWins_othersSeeResolved()
    {
        var requester = _factory.ClientFor("q-req1");
        var approver = _factory.ClientFor("q-app1");
        var bystander = _factory.ClientFor("q-by1");

        var group = await CreateGroupAsync(requester, "Quorum");
        await JoinAsync(requester, approver, group.Id);
        await JoinAsync(requester, bystander, group.Id);
        ActivateAllMembers(group.Id);

        var created = await (await requester.PostAsJsonAsync("/api/requests",
                new CreateUnlockRequest("q-cr1", "com.instagram.android", "Instagram", null, group.Id)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();
        Assert.Equal(group.Id, created!.GroupId);
        Assert.Equal("Quorum", created.GroupName);

        // Fan-out: every other active member is asked.
        Assert.Contains(_factory.Push.For("q-app1"), s => s.Type == "UNLOCK_REQUEST");
        Assert.Contains(_factory.Push.For("q-by1"), s => s.Type == "UNLOCK_REQUEST");

        // First responder wins.
        var resolved = await (await approver.PostAsJsonAsync($"/api/requests/{created.Id}/respond",
                new RespondToRequest("APPROVE", 10)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();
        Assert.Equal("Approved", resolved!.Status);
        Assert.Equal("q-app1", resolved.ResolvedByUid);

        // Requester told, bystander sees "resolved by X".
        Assert.Contains(_factory.Push.For("q-req1"), s => s.Type == "REQUEST_APPROVED");
        Assert.Contains(_factory.Push.For("q-by1"), s => s.Type == "REQUEST_RESOLVED");

        // Second responder is too late.
        var late = await bystander.PostAsJsonAsync($"/api/requests/{created.Id}/respond",
            new RespondToRequest("DENY", null));
        Assert.Equal(HttpStatusCode.Conflict, late.StatusCode);

        // Allowance audit row is group-sourced.
        _factory.WithDb(db =>
            Assert.Equal("GROUP", db.Allowances.AsNoTracking().Single(a => a.RequestId == created.Id).Source));
    }

    [Fact]
    public async Task GroupRequest_withNoOtherMembers_isRejected()
    {
        var loner = _factory.ClientFor("q-loner1");
        var group = await CreateGroupAsync(loner, "Just me");

        var response = await loner.PostAsJsonAsync("/api/requests",
            new CreateUnlockRequest("q-loner-cr1", "com.instagram.android", "Instagram", null, group.Id));
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task NewMember_cannotApprove_duringAntiPuppetCooldown()
    {
        var requester = _factory.ClientFor("q-cd-req");
        var partner = _factory.ClientFor("q-cd-old");
        var puppet = _factory.ClientFor("q-cd-new");

        var group = await CreateGroupAsync(requester, "Cooldown");
        await JoinAsync(requester, partner, group.Id);
        await JoinAsync(requester, puppet, group.Id); // third member → cooldown-gated

        var created = await (await requester.PostAsJsonAsync("/api/requests",
                new CreateUnlockRequest("q-cd-cr1", "com.instagram.android", "Instagram", null, group.Id)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        var response = await puppet.PostAsJsonAsync($"/api/requests/{created!.Id}/respond",
            new RespondToRequest("APPROVE", 60));
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task LeaveGroup_cooldown_notifiesEveryone_thenSweepFinalizes_andTransfersOwnership()
    {
        var owner = _factory.ClientFor("l-owner1");
        var heir = _factory.ClientFor("l-heir1");
        var third = _factory.ClientFor("l-third1");

        var group = await CreateGroupAsync(owner, "Succession");
        await JoinAsync(owner, heir, group.Id);
        await JoinAsync(owner, third, group.Id);

        // Owner starts leaving: not instant, everyone else is told immediately.
        var leaveResponse = await owner.DeleteAsync($"/api/groups/{group.Id}/members/l-owner1");
        leaveResponse.EnsureSuccessStatusCode();
        var during = await leaveResponse.Content.ReadFromJsonAsync<GroupDto>();
        var leaving = during!.Members.Single(m => m.Uid == "l-owner1");
        Assert.True(leaving.RemovalPending);
        Assert.True(leaving.RemovalEffectiveAtUtc > DateTime.UtcNow.AddHours(12));
        Assert.Contains(_factory.Push.For("l-heir1"), s => s.Type == "GROUP_MEMBER_REMOVAL_STARTED");
        Assert.Contains(_factory.Push.For("l-third1"), s => s.Type == "GROUP_MEMBER_REMOVAL_STARTED");

        // Double-leave → conflict.
        Assert.Equal(HttpStatusCode.Conflict,
            (await owner.DeleteAsync($"/api/groups/{group.Id}/members/l-owner1")).StatusCode);

        // Non-owner cannot remove someone else.
        Assert.Equal(HttpStatusCode.Forbidden,
            (await heir.DeleteAsync($"/api/groups/{group.Id}/members/l-third1")).StatusCode);

        // Fast-forward past cooldown; sweep finalizes and hands the group over.
        _factory.WithDb(db =>
        {
            var m = db.GroupMembers.Single(x => x.GroupId == group.Id && x.Uid == "l-owner1");
            m.RemovalEffectiveAtUtc = DateTime.UtcNow.AddMinutes(-1);
        });
        await _factory.RunSweepOnceAsync();

        _factory.WithDb(db =>
        {
            Assert.Equal(GroupMemberStatus.Removed,
                db.GroupMembers.AsNoTracking().Single(x => x.GroupId == group.Id && x.Uid == "l-owner1").Status);
            // Longest-standing remaining member inherits the group.
            Assert.Equal("l-heir1", db.Groups.AsNoTracking().Single(g => g.Id == group.Id).OwnerUid);
        });
        Assert.Contains(_factory.Push.For("l-heir1"), s => s.Type == "GROUP_MEMBER_LEFT");
        Assert.Contains(_factory.Push.For("l-owner1"), s => s.Type == "GROUP_MEMBER_LEFT");
    }

    [Fact]
    public async Task Chat_send_isIdempotent_fansOut_pagesBackwards_andExcludesOutsiders()
    {
        var alice = _factory.ClientFor("c-alice1");
        var bob = _factory.ClientFor("c-bob1");
        var outsider = _factory.ClientFor("c-out1");

        var group = await CreateGroupAsync(alice, "Chatty");
        await JoinAsync(alice, bob, group.Id);

        var first = await (await alice.PostAsJsonAsync($"/api/chat/groups/{group.Id}/messages",
                new SendChatMessageRequest("c-m1", "Stay strong today!")))
            .Content.ReadFromJsonAsync<ChatMessageDto>();
        Assert.Equal($"group:{group.Id}", first!.ConversationKey);
        Assert.Contains(_factory.Push.For("c-bob1"), s => s.Type == "CHAT_MESSAGE");

        // Outbox retry with the same client id must not duplicate.
        var retried = await (await alice.PostAsJsonAsync($"/api/chat/groups/{group.Id}/messages",
                new SendChatMessageRequest("c-m1", "Stay strong today!")))
            .Content.ReadFromJsonAsync<ChatMessageDto>();
        Assert.Equal(first.Id, retried!.Id);
        _factory.WithDb(db =>
            Assert.Equal(1, db.ChatMessages.Count(m => m.ConversationKey == $"group:{group.Id}")));

        var second = await (await bob.PostAsJsonAsync($"/api/chat/groups/{group.Id}/messages",
                new SendChatMessageRequest("c-m2", "You've got this.")))
            .Content.ReadFromJsonAsync<ChatMessageDto>();

        // Newest-first list, and paging with ?before walks backwards.
        var page = await (await alice.GetAsync($"/api/chat/groups/{group.Id}/messages?limit=1"))
            .Content.ReadFromJsonAsync<List<ChatMessageDto>>();
        Assert.Single(page!);
        Assert.Equal(second!.Id, page![0].Id);
        var older = await (await alice.GetAsync(
                $"/api/chat/groups/{group.Id}/messages?limit=1&before={Uri.EscapeDataString(page[0].SentAtUtc.ToString("O"))}"))
            .Content.ReadFromJsonAsync<List<ChatMessageDto>>();
        Assert.Single(older!);
        Assert.Equal(first.Id, older![0].Id);

        // Non-members can neither read nor write.
        Assert.Equal(HttpStatusCode.NotFound,
            (await outsider.GetAsync($"/api/chat/groups/{group.Id}/messages")).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound,
            (await outsider.PostAsJsonAsync($"/api/chat/groups/{group.Id}/messages",
                new SendChatMessageRequest("c-mx", "hi"))).StatusCode);
    }
}
