using System.Net;
using System.Net.Http.Json;
using BeOffline.Api.Contracts;
using BeOffline.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Tests;

public sealed class PairingFlowTests : IClassFixture<TestAppFactory>
{
    private readonly TestAppFactory _factory;

    public PairingFlowTests(TestAppFactory factory)
    {
        _factory = factory;
        _factory.EnsureDatabase();
    }

    [Fact]
    public async Task InviteAndAccept_createsPairing_visibleToBothSides_firstPairingApprovesImmediately()
    {
        var alice = _factory.ClientFor("alice1");
        var bob = _factory.ClientFor("bob1");

        var invite = await (await alice.PostAsync("/api/pairing/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        Assert.NotNull(invite);
        Assert.Equal(6, invite!.Code.Length);

        var acceptResponse = await bob.PostAsJsonAsync("/api/pairing/invites/accept", new AcceptInviteRequest(invite.Code));
        acceptResponse.EnsureSuccessStatusCode();
        var pairing = await acceptResponse.Content.ReadFromJsonAsync<PairingDto>();
        Assert.NotNull(pairing);
        Assert.Equal("alice1", pairing!.PartnerUid);
        // First-ever pairing for both parties: approval works immediately.
        Assert.True(pairing.CanApproveAfterUtc <= DateTime.UtcNow.AddSeconds(5));

        var alicePairings = await (await alice.GetAsync("/api/pairing")).Content.ReadFromJsonAsync<List<PairingDto>>();
        Assert.Contains(alicePairings!, p => p.PartnerUid == "bob1");

        // Issuer was notified.
        Assert.Contains(_factory.Push.For("alice1"), s => s.Type == "INVITE_ACCEPTED");
    }

    [Fact]
    public async Task AcceptingOwnInvite_isRejected()
    {
        var carol = _factory.ClientFor("carol1");
        var invite = await (await carol.PostAsync("/api/pairing/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();

        var response = await carol.PostAsJsonAsync("/api/pairing/invites/accept", new AcceptInviteRequest(invite!.Code));
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task SecondPairing_getsAntiPuppetApprovalCooldown()
    {
        var dave = _factory.ClientFor("dave1");
        var erin = _factory.ClientFor("erin1");
        var frank = _factory.ClientFor("frank1");

        // First pairing: dave + erin — immediate.
        var invite1 = await (await dave.PostAsync("/api/pairing/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        (await erin.PostAsJsonAsync("/api/pairing/invites/accept", new AcceptInviteRequest(invite1!.Code)))
            .EnsureSuccessStatusCode();

        // Second pairing: dave + frank — dave already has one → cooldown applies.
        var invite2 = await (await dave.PostAsync("/api/pairing/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        var pairing2 = await (await frank.PostAsJsonAsync("/api/pairing/invites/accept", new AcceptInviteRequest(invite2!.Code)))
            .Content.ReadFromJsonAsync<PairingDto>();

        Assert.True(pairing2!.CanApproveAfterUtc > DateTime.UtcNow.AddHours(12),
            "a pairing added alongside an existing one must not be able to approve immediately");
    }

    [Fact]
    public async Task Removal_startsCooldown_notifiesPartner_pairingStaysActive_thenSweepFinalizes()
    {
        var gina = _factory.ClientFor("gina1");
        var hank = _factory.ClientFor("hank1");

        var invite = await (await gina.PostAsync("/api/pairing/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        var pairing = await (await hank.PostAsJsonAsync("/api/pairing/invites/accept", new AcceptInviteRequest(invite!.Code)))
            .Content.ReadFromJsonAsync<PairingDto>();

        // Gina asks to remove Hank.
        var removeResponse = await gina.DeleteAsync($"/api/pairing/{pairing!.Id}");
        removeResponse.EnsureSuccessStatusCode();
        var removed = await removeResponse.Content.ReadFromJsonAsync<PairingDto>();

        Assert.True(removed!.RemovalPending);
        Assert.Equal("gina1", removed.RemovalRequestedByUid);
        Assert.True(removed.RemovalEffectiveAtUtc > DateTime.UtcNow.AddHours(12));

        // Partner (hank) was told immediately — the visible-and-costly mechanic.
        Assert.Contains(_factory.Push.For("hank1"), s => s.Type == "PARTNER_REMOVAL_STARTED");

        // Pairing still functions during cooldown.
        var hankView = await (await hank.GetAsync("/api/pairing")).Content.ReadFromJsonAsync<List<PairingDto>>();
        Assert.Contains(hankView!, p => p.Id == pairing.Id && p.Status == "Active");

        // Second removal request → conflict.
        Assert.Equal(HttpStatusCode.Conflict, (await gina.DeleteAsync($"/api/pairing/{pairing.Id}")).StatusCode);

        // Fast-forward past the cooldown and sweep → finalized + both notified.
        _factory.WithDb(db =>
        {
            var p = db.Pairings.Single(x => x.Id == pairing.Id);
            p.RemovalEffectiveAtUtc = DateTime.UtcNow.AddMinutes(-1);
        });
        await _factory.RunSweepOnceAsync();

        _factory.WithDb(db =>
            Assert.Equal(PairingStatus.Removed, db.Pairings.AsNoTracking().Single(x => x.Id == pairing.Id).Status));
        Assert.Contains(_factory.Push.For("hank1"), s => s.Type == "PARTNER_REMOVED");
        Assert.Contains(_factory.Push.For("gina1"), s => s.Type == "PARTNER_REMOVED");
    }
}
