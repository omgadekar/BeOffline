using System.Security.Cryptography;
using BeOffline.Api.Auth;
using BeOffline.Api.Contracts;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;
using BeOffline.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/pairing")]
public sealed class PairingController(AppDbContext db, INotificationService notifier, IConfiguration config)
    : ControllerBase
{
    // Unambiguous alphabet: no 0/O/1/I/L.
    private const string CodeAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    [HttpPost("invites")]
    public async Task<ActionResult<CreateInviteResponse>> CreateInvite()
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        string code;
        do
        {
            code = RandomNumberGenerator.GetString(CodeAlphabet, 6);
        } while (await db.InviteCodes.AnyAsync(c => c.Code == code));

        db.InviteCodes.Add(new InviteCode
        {
            Code = code,
            IssuerUid = uid,
            CreatedAtUtc = now,
            ExpiresAtUtc = now.AddHours(24)
        });
        await db.SaveChangesAsync();

        return new CreateInviteResponse(code, now.AddHours(24));
    }

    [HttpPost("invites/accept")]
    public async Task<ActionResult<PairingDto>> AcceptInvite(AcceptInviteRequest request)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var invite = await db.InviteCodes.FindAsync(request.Code.Trim().ToUpperInvariant());
        if (invite is null || invite.ExpiresAtUtc <= now || invite.ConsumedByUid != null)
            return NotFound(new { message = "Invalid or expired invite code." });
        if (invite.IssuerUid == uid)
            return BadRequest(new { message = "You cannot pair with yourself." });

        // One 1:1 partner at a time, per side. A pairing in its removal cooldown
        // still counts as Active, so you can't line up a replacement until the
        // current partner is fully gone (this is the anti-puppet guard). To
        // involve several people at once, use a Group instead.
        var accepterHasPartner = await db.Pairings.AnyAsync(p =>
            p.Status == PairingStatus.Active && (p.UserAUid == uid || p.UserBUid == uid));
        if (accepterHasPartner)
            return Conflict(new { message = "You already have an accountability partner. Remove them first, or use a Group for several people." });

        var issuerHasPartner = await db.Pairings.AnyAsync(p =>
            p.Status == PairingStatus.Active && (p.UserAUid == invite.IssuerUid || p.UserBUid == invite.IssuerUid));
        if (issuerHasPartner)
            return Conflict(new { message = "This person already has an accountability partner." });

        invite.ConsumedByUid = uid;
        var pairing = new Pairing
        {
            Id = Guid.NewGuid(),
            UserAUid = invite.IssuerUid,
            UserBUid = uid,
            Status = PairingStatus.Active,
            CreatedAtUtc = now,
            // Neither side had a partner (enforced above), so this is a genuine
            // first pairing for both → approval works immediately.
            CanApproveAfterUtc = now
        };
        db.Pairings.Add(pairing);
        await db.SaveChangesAsync();

        var accepterName = Names.First((await db.Users.FindAsync(uid))?.DisplayName, "Your invitee");
        await notifier.NotifyAsync(
            invite.IssuerUid, "INVITE_ACCEPTED", new { pairingId = pairing.Id },
            "Pairing complete", $"{accepterName} accepted your invite — you are now accountability partners.");

        var issuerName = (await db.Users.FindAsync(invite.IssuerUid))?.DisplayName;
        return PairingDto.From(pairing, uid, issuerName);
    }

    [HttpGet]
    public async Task<ActionResult<List<PairingDto>>> ListPairings()
    {
        var uid = User.Uid();
        var pairings = await db.Pairings
            .Where(p => p.Status == PairingStatus.Active && (p.UserAUid == uid || p.UserBUid == uid))
            .ToListAsync();

        var partnerUids = pairings.Select(p => p.PartnerOf(uid)).Distinct().ToList();
        var names = await db.Users
            .Where(u => partnerUids.Contains(u.Uid))
            .ToDictionaryAsync(u => u.Uid, u => u.DisplayName);

        return pairings
            .Select(p => PairingDto.From(p, uid, names.GetValueOrDefault(p.PartnerOf(uid))))
            .ToList();
    }

    /// <summary>
    /// Starts removal — deliberately NOT instant (the accountability plan's
    /// "removal hole"): the pairing keeps working through a cooldown and the
    /// partner is told immediately, making the act visible and socially costly.
    /// </summary>
    [HttpDelete("{id:guid}")]
    public async Task<ActionResult<PairingDto>> RequestRemoval(Guid id)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var pairing = await db.Pairings.FindAsync(id);
        if (pairing is null || pairing.Status != PairingStatus.Active || !pairing.Involves(uid))
            return NotFound();
        if (pairing.RemovalRequestedAtUtc != null)
            return Conflict(new { message = "Removal is already pending for this pairing." });

        var cooldownHours = config.GetValue("Accountability:RemovalCooldownHours", 24);
        pairing.RemovalRequestedByUid = uid;
        pairing.RemovalRequestedAtUtc = now;
        pairing.RemovalEffectiveAtUtc = now.AddHours(cooldownHours);
        await db.SaveChangesAsync();

        var removerName = Names.First((await db.Users.FindAsync(uid))?.DisplayName);
        await notifier.NotifyAsync(
            pairing.PartnerOf(uid), "PARTNER_REMOVAL_STARTED",
            new { pairingId = pairing.Id, effectiveAtUtc = pairing.RemovalEffectiveAtUtc },
            "Partner removal started",
            $"{removerName} is removing you as their accountability partner. The pairing stays active for {cooldownHours} more hours.");

        var partnerName = (await db.Users.FindAsync(pairing.PartnerOf(uid)))?.DisplayName;
        return PairingDto.From(pairing, uid, partnerName);
    }
}
