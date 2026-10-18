using BeOffline.Api.Auth;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;
using BeOffline.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Controllers;

/// <summary>
/// Account lifecycle. DELETE purges every row belonging to the caller — required
/// by Google Play's account-deletion policy for apps that offer sign-in.
/// </summary>
[ApiController]
[Authorize]
[Route("api/account")]
public sealed class AccountController(AppDbContext db, INotificationService notifier) : ControllerBase
{
    /// <summary>
    /// Deletes the caller's account and all associated server data. Idempotent:
    /// deleting an already-gone account returns 204. Partners and group
    /// co-members are told first, then everything is removed in one transaction.
    /// </summary>
    [HttpDelete]
    public async Task<IActionResult> DeleteAccount()
    {
        var uid = User.Uid();

        var user = await db.Users.FindAsync(uid);
        if (user is null) return NoContent(); // already deleted

        // ── Gather relationships up front (for notifications + cascade order) ──
        var pairings = await db.Pairings
            .Where(p => p.UserAUid == uid || p.UserBUid == uid)
            .ToListAsync();
        var partnerUids = pairings
            .Where(p => p.Status == PairingStatus.Active)
            .Select(p => p.PartnerOf(uid))
            .Distinct()
            .ToList();

        var myGroupIds = await db.GroupMembers
            .Where(m => m.Uid == uid && m.Status == GroupMemberStatus.Active)
            .Select(m => m.GroupId)
            .ToListAsync();
        var coMemberUids = await db.GroupMembers
            .Where(m => myGroupIds.Contains(m.GroupId) && m.Uid != uid && m.Status == GroupMemberStatus.Active)
            .Select(m => m.Uid)
            .Distinct()
            .ToListAsync();

        var displayName = Names.First(user.DisplayName);

        // ── Notify before we tear the data down ──────────────────────────────
        foreach (var partnerUid in partnerUids)
        {
            await notifier.NotifyAsync(
                partnerUid, "PARTNER_REMOVED", new { reason = "account_deleted" },
                "Pairing ended", $"{displayName} deleted their BeOffline account.");
        }
        foreach (var coMemberUid in coMemberUids)
        {
            await notifier.NotifyAsync(
                coMemberUid, "GROUP_MEMBER_LEFT", new { uid, reason = "account_deleted" },
                "Group update", $"{displayName} left the group (account deleted).");
        }

        // ── Purge, FK-safe, in one transaction ───────────────────────────────
        await using var tx = await db.Database.BeginTransactionAsync();

        // Owned groups: hand over to the longest-standing remaining member, or
        // mark the whole group for deletion if the caller was the only one left.
        var ownedGroups = await db.Groups.Where(g => g.OwnerUid == uid).ToListAsync();
        var groupsToDelete = new List<Guid>();
        foreach (var group in ownedGroups)
        {
            var heir = await db.GroupMembers
                .Where(m => m.GroupId == group.Id && m.Uid != uid && m.Status == GroupMemberStatus.Active)
                .OrderBy(m => m.JoinedAtUtc)
                .FirstOrDefaultAsync();
            if (heir is not null) group.OwnerUid = heir.Uid;
            else groupsToDelete.Add(group.Id);
        }
        if (ownedGroups.Count > 0) await db.SaveChangesAsync();

        // Unlock requests that must go: the caller's own, plus any tied to a
        // pairing or an about-to-be-deleted group (their FK would otherwise block
        // the pairing/group delete). Their approval rows go with them.
        var pairingIds = pairings.Select(p => p.Id).ToList();
        var requestIds = await db.UnlockRequests
            .Where(r => r.RequesterUid == uid
                        || (r.PairingId != null && pairingIds.Contains(r.PairingId.Value))
                        || (r.GroupId != null && groupsToDelete.Contains(r.GroupId.Value)))
            .Select(r => r.Id)
            .ToListAsync();

        await db.RequestApprovals
            .Where(a => a.ApproverUid == uid || requestIds.Contains(a.RequestId))
            .ExecuteDeleteAsync();
        await db.UnlockRequests.Where(r => requestIds.Contains(r.Id)).ExecuteDeleteAsync();

        // Empty owned groups: chat, invites, members (cascade), then the group.
        foreach (var groupId in groupsToDelete)
        {
            var key = $"group:{groupId}";
            await db.ChatMessages.Where(m => m.ConversationKey == key).ExecuteDeleteAsync();
            await db.GroupInviteCodes.Where(c => c.GroupId == groupId).ExecuteDeleteAsync();
            await db.GroupMembers.Where(m => m.GroupId == groupId).ExecuteDeleteAsync();
            await db.Groups.Where(g => g.Id == groupId).ExecuteDeleteAsync();
        }

        // The caller's own rows everywhere else.
        await db.GroupMembers.Where(m => m.Uid == uid).ExecuteDeleteAsync();
        await db.Pairings.Where(p => p.UserAUid == uid || p.UserBUid == uid).ExecuteDeleteAsync();
        await db.Devices.Where(d => d.Uid == uid).ExecuteDeleteAsync();
        await db.InviteCodes.Where(c => c.IssuerUid == uid).ExecuteDeleteAsync();
        await db.GroupInviteCodes.Where(c => c.IssuerUid == uid).ExecuteDeleteAsync();
        await db.Allowances.Where(a => a.Uid == uid).ExecuteDeleteAsync();
        await db.TamperEvents.Where(t => t.Uid == uid).ExecuteDeleteAsync();
        await db.SoloUnlocks.Where(u => u.Uid == uid).ExecuteDeleteAsync();
        await db.ChatMessages.Where(m => m.SenderUid == uid).ExecuteDeleteAsync();
        await db.Users.Where(u => u.Uid == uid).ExecuteDeleteAsync();

        await tx.CommitAsync();
        return NoContent();
    }
}
