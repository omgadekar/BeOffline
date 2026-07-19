using System.Net;
using System.Net.Http.Json;
using BeOffline.Api.Contracts;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Tests;

public sealed class RequestFlowTests : IClassFixture<TestAppFactory>
{
    private readonly TestAppFactory _factory;

    public RequestFlowTests(TestAppFactory factory)
    {
        _factory = factory;
        _factory.EnsureDatabase();
    }

    private async Task<(HttpClient requester, HttpClient approver, Guid pairingId)> PairAsync(string requesterUid, string approverUid)
    {
        var requester = _factory.ClientFor(requesterUid);
        var approver = _factory.ClientFor(approverUid);
        var invite = await (await requester.PostAsync("/api/pairing/invites", null))
            .Content.ReadFromJsonAsync<CreateInviteResponse>();
        var pairing = await (await approver.PostAsJsonAsync("/api/pairing/invites/accept", new AcceptInviteRequest(invite!.Code)))
            .Content.ReadFromJsonAsync<PairingDto>();
        return (requester, approver, pairing!.Id);
    }

    private static CreateUnlockRequest NewRequest(Guid pairingId, string? clientId = null) =>
        new(clientId ?? Guid.NewGuid().ToString(), "com.instagram.android", "Instagram", pairingId);

    [Fact]
    public async Task Create_isPending_expiresInFuture_andPartnerIsNotified()
    {
        var (requester, _, pairingId) = await PairAsync("req1", "app1");

        var dto = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        Assert.Equal("Pending", dto!.Status);
        Assert.True(dto.ExpiresAtUtc > DateTime.UtcNow.AddMinutes(10));
        Assert.Contains(_factory.Push.For("app1"), s => s.Type == "UNLOCK_REQUEST");
    }

    [Fact]
    public async Task Create_isIdempotentOnClientRequestId()
    {
        var (requester, _, pairingId) = await PairAsync("req2", "app2");
        var clientId = "outbox-retry-1";

        var first = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId, clientId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();
        var second = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId, clientId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        Assert.Equal(first!.Id, second!.Id);
    }

    [Fact]
    public async Task Approve_setsGrantedUntil_createsAllowance_notifiesRequester()
    {
        var (requester, approver, pairingId) = await PairAsync("req3", "app3");
        var dto = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        var approved = await (await approver.PostAsJsonAsync($"/api/requests/{dto!.Id}/respond",
                new RespondToRequest("APPROVE", 10)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        Assert.Equal("Approved", approved!.Status);
        Assert.Equal(10, approved.GrantedDurationMinutes);
        Assert.True(approved.GrantedUntilUtc > DateTime.UtcNow.AddMinutes(8));

        _factory.WithDb(db =>
            Assert.Single(db.Allowances.AsNoTracking().Where(a => a.RequestId == dto.Id && a.Uid == "req3")));
        Assert.Contains(_factory.Push.For("req3"), s => s.Type == "REQUEST_APPROVED");
    }

    [Fact]
    public async Task Deny_notifiesRequester()
    {
        var (requester, approver, pairingId) = await PairAsync("req4", "app4");
        var dto = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        var denied = await (await approver.PostAsJsonAsync($"/api/requests/{dto!.Id}/respond",
                new RespondToRequest("DENY", null)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        Assert.Equal("Denied", denied!.Status);
        Assert.Contains(_factory.Push.For("req4"), s => s.Type == "REQUEST_DENIED");
    }

    [Fact]
    public async Task RequesterCannotRespondToOwnRequest()
    {
        var (requester, _, pairingId) = await PairAsync("req5", "app5");
        var dto = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        var response = await requester.PostAsJsonAsync($"/api/requests/{dto!.Id}/respond",
            new RespondToRequest("APPROVE", 10));
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task ApproveWithoutDuration_isRejected()
    {
        var (requester, approver, pairingId) = await PairAsync("req6", "app6");
        var dto = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        var response = await approver.PostAsJsonAsync($"/api/requests/{dto!.Id}/respond",
            new RespondToRequest("APPROVE", null));
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task RespondingAfterExpiry_conflicts_andRequestIsExpired()
    {
        var (requester, approver, pairingId) = await PairAsync("req7", "app7");
        var dto = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        _factory.WithDb(db =>
        {
            var r = db.UnlockRequests.Single(x => x.Id == dto!.Id);
            r.ExpiresAtUtc = DateTime.UtcNow.AddMinutes(-1);
        });

        var response = await approver.PostAsJsonAsync($"/api/requests/{dto!.Id}/respond",
            new RespondToRequest("APPROVE", 10));
        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);

        var outgoing = await (await requester.GetAsync("/api/requests?role=outgoing"))
            .Content.ReadFromJsonAsync<List<UnlockRequestDto>>();
        Assert.Equal("Expired", outgoing!.Single(r => r.Id == dto!.Id).Status);
    }

    [Fact]
    public async Task PairingInApprovalCooldown_cannotApprove()
    {
        var (requester, approver, pairingId) = await PairAsync("req8", "app8");
        var dto = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        _factory.WithDb(db =>
        {
            var p = db.Pairings.Single(x => x.Id == pairingId);
            p.CanApproveAfterUtc = DateTime.UtcNow.AddHours(23);
        });

        var response = await approver.PostAsJsonAsync($"/api/requests/{dto!.Id}/respond",
            new RespondToRequest("APPROVE", 10));
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task SweepExpiresOverduePending_andNotifiesRequester()
    {
        var (requester, _, pairingId) = await PairAsync("req9", "app9");
        var dto = await (await requester.PostAsJsonAsync("/api/requests", NewRequest(pairingId)))
            .Content.ReadFromJsonAsync<UnlockRequestDto>();

        _factory.WithDb(db =>
        {
            var r = db.UnlockRequests.Single(x => x.Id == dto!.Id);
            r.ExpiresAtUtc = DateTime.UtcNow.AddMinutes(-1);
        });
        await _factory.RunSweepOnceAsync();

        Assert.Contains(_factory.Push.For("req9"), s => s.Type == "REQUEST_EXPIRED");
    }

    [Fact]
    public async Task TamperReport_fansOutToPartner_andIsIdempotent()
    {
        var (requester, _, _) = await PairAsync("req10", "app10");

        var report = new ReportTamperRequest("evt-1", "ACCESSIBILITY_DISABLED", null, DateTime.UtcNow);
        (await requester.PostAsJsonAsync("/api/tamper", report)).EnsureSuccessStatusCode();
        (await requester.PostAsJsonAsync("/api/tamper", report)).EnsureSuccessStatusCode();

        var alerts = _factory.Push.For("app10").Where(s => s.Type == "TAMPER_ALERT").ToList();
        Assert.Single(alerts);
    }
}
