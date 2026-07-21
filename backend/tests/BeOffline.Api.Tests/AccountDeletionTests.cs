using System.Net;
using System.Net.Http.Json;
using BeOffline.Api.Contracts;
using BeOffline.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Tests;

public sealed class AccountDeletionTests : IClassFixture<TestAppFactory>
{
    private readonly TestAppFactory _factory;

    public AccountDeletionTests(TestAppFactory factory)
    {
        _factory = factory;
        _factory.EnsureDatabase();
    }

    [Fact]
    public async Task DeleteAccount_purgesUsersData_notifiesPartner_andIsIdempotent()
    {
        var alice = _factory.ClientFor("del-alice");
        var bob = _factory.ClientFor("del-bob");

        // Pair, register a device, and send a request so Alice has data spread around.
        var invite = await (await alice.PostAsync("/api/pairing/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        (await bob.PostAsJsonAsync("/api/pairing/invites/accept", new AcceptInviteRequest(invite!.Code)))
            .EnsureSuccessStatusCode();
        (await alice.PutAsJsonAsync("/api/devices", new RegisterDeviceRequest("dev-1", "token-1", "Pixel")))
            .EnsureSuccessStatusCode();
        (await alice.PostAsJsonAsync("/api/requests",
            new CreateUnlockRequest("del-cr1", "com.instagram.android", "Instagram", null)))
            .EnsureSuccessStatusCode();

        // Delete Alice's account.
        var resp = await alice.DeleteAsync("/api/account");
        Assert.Equal(HttpStatusCode.NoContent, resp.StatusCode);

        _factory.WithDb(db =>
        {
            Assert.Null(db.Users.AsNoTracking().FirstOrDefault(u => u.Uid == "del-alice"));
            Assert.False(db.Devices.AsNoTracking().Any(d => d.Uid == "del-alice"));
            Assert.False(db.UnlockRequests.AsNoTracking().Any(r => r.RequesterUid == "del-alice"));
            Assert.False(db.Pairings.AsNoTracking()
                .Any(p => p.UserAUid == "del-alice" || p.UserBUid == "del-alice"));
        });

        // Partner was told.
        Assert.Contains(_factory.Push.For("del-bob"),
            s => s.Type == "PARTNER_REMOVED");

        // Idempotent: a second delete (user re-provisioned by the auth middleware,
        // then removed again) still succeeds.
        Assert.Equal(HttpStatusCode.NoContent, (await alice.DeleteAsync("/api/account")).StatusCode);
    }

    [Fact]
    public async Task DeleteAccount_ownedGroup_transfersOwnership_toRemainingMember()
    {
        var owner = _factory.ClientFor("del-owner");
        var member = _factory.ClientFor("del-member");

        var group = await (await owner.PostAsJsonAsync("/api/groups", new CreateGroupRequest("Crew")))
            .Content.ReadFromJsonAsync<GroupDto>();
        var invite = await (await owner.PostAsync($"/api/groups/{group!.Id}/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        (await member.PostAsJsonAsync("/api/groups/join", new JoinGroupRequest(invite!.Code)))
            .EnsureSuccessStatusCode();

        (await owner.DeleteAsync("/api/account")).EnsureSuccessStatusCode();

        _factory.WithDb(db =>
        {
            var g = db.Groups.AsNoTracking().FirstOrDefault(x => x.Id == group.Id);
            Assert.NotNull(g);                       // group survives
            Assert.Equal("del-member", g!.OwnerUid); // ownership handed over
            Assert.False(db.GroupMembers.AsNoTracking()
                .Any(m => m.GroupId == group.Id && m.Uid == "del-owner"));
        });
    }

    [Fact]
    public async Task PrivacyAndDeletionPages_areServed()
    {
        var client = _factory.CreateClient();
        var privacy = await client.GetAsync("/privacy");
        privacy.EnsureSuccessStatusCode();
        Assert.Contains("Privacy Policy", await privacy.Content.ReadAsStringAsync());

        var deletion = await client.GetAsync("/account-deletion");
        deletion.EnsureSuccessStatusCode();
        Assert.Contains("Delete your BeOffline account", await deletion.Content.ReadAsStringAsync());
    }
}
