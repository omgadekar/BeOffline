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
[Route("api/groups")]
public sealed class GroupsController(AppDbContext db, INotificationService notifier, IConfiguration config)
    : ControllerBase
{
    // Same unambiguous alphabet as pairing invites: no 0/O/1/I/L.
    private const string CodeAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    [HttpPost]
    public async Task<ActionResult<GroupDto>> Create(CreateGroupRequest request)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var name = request.Name.Trim();
        if (name.Length is < 1 or > 64)
            return BadRequest(new { message = "Group name must be 1-64 characters." });

        var group = new ApproverGroup
        {
            Id = Guid.NewGuid(),
            Name = name,
            OwnerUid = uid,
            CreatedAtUtc = now
        };
        db.Groups.Add(group);
        db.GroupMembers.Add(new GroupMember
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Uid = uid,
            Status = GroupMemberStatus.Active,
            JoinedAtUtc = now,
            CanApproveAfterUtc = now
        });
        await db.SaveChangesAsync();

        return await ToDtoAsync(group);
    }

    [HttpPost("{id:guid}/invites")]
    public async Task<ActionResult<CreateInviteResponse>> CreateInvite(Guid id)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var isMember = await db.GroupMembers.AnyAsync(m =>
            m.GroupId == id && m.Uid == uid && m.Status == GroupMemberStatus.Active);
        if (!isMember) return NotFound();

        string code;
        do
        {
            code = RandomNumberGenerator.GetString(CodeAlphabet, 6);
        } while (await db.GroupInviteCodes.AnyAsync(c => c.Code == code));

        db.GroupInviteCodes.Add(new GroupInviteCode
        {
            Code = code,
            GroupId = id,
            IssuerUid = uid,
            CreatedAtUtc = now,
            ExpiresAtUtc = now.AddHours(24)
        });
        await db.SaveChangesAsync();

        return new CreateInviteResponse(code, now.AddHours(24));
    }

    [HttpPost("join")]
    public async Task<ActionResult<GroupDto>> Join(JoinGroupRequest request)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var invite = await db.GroupInviteCodes.FindAsync(request.Code.Trim().ToUpperInvariant());
        if (invite is null || invite.ExpiresAtUtc <= now || invite.ConsumedByUid != null)
            return NotFound(new { message = "Invalid or expired invite code." });

        var group = await db.Groups.FindAsync(invite.GroupId);
        if (group is null)
            return NotFound(new { message = "Invalid or expired invite code." });

        var members = await db.GroupMembers
            .Where(m => m.GroupId == group.Id)
            .ToListAsync();
        var activeMembers = members.Where(m => m.Status == GroupMemberStatus.Active).ToList();

        var existing = members.FirstOrDefault(m => m.Uid == uid);
        if (existing is { Status: GroupMemberStatus.Active })
            return Conflict(new { message = "You are already a member of this group." });

        var maxMembers = config.GetValue("Accountability:GroupMaxMembers", 10);
        if (activeMembers.Count >= maxMembers)
            return Conflict(new { message = $"This group is full ({maxMembers} members max)." });

        // Anti-puppet, same rule as pairings: only a genuinely FIRST accountability
        // relationship on both sides activates immediately. If the group already
        // has real membership, or either side has relationships elsewhere, the new
        // member can't approve until the cooldown passes.
        var joinerHasExisting =
            await db.Pairings.AnyAsync(p =>
                p.Status == PairingStatus.Active && (p.UserAUid == uid || p.UserBUid == uid)) ||
            await db.GroupMembers.AnyAsync(m =>
                m.Uid == uid && m.Status == GroupMemberStatus.Active && m.GroupId != group.Id);
        var groupUids = activeMembers.Select(m => m.Uid).ToList();
        var groupHasExisting =
            activeMembers.Count >= 2 ||
            await db.Pairings.AnyAsync(p =>
                p.Status == PairingStatus.Active && (groupUids.Contains(p.UserAUid) || groupUids.Contains(p.UserBUid))) ||
            await db.GroupMembers.AnyAsync(m =>
                groupUids.Contains(m.Uid) && m.Status == GroupMemberStatus.Active && m.GroupId != group.Id);
        var activationCooldownHours = config.GetValue("Accountability:ApproverActivationCooldownHours", 24);
        var canApproveAfter = joinerHasExisting || groupHasExisting ? now.AddHours(activationCooldownHours) : now;

        invite.ConsumedByUid = uid;
        if (existing is not null)
        {
            // Rejoining after removal reactivates the old row (the (GroupId, Uid)
            // unique index means there is only ever one row per person per group).
            existing.Status = GroupMemberStatus.Active;
            existing.JoinedAtUtc = now;
            existing.CanApproveAfterUtc = canApproveAfter;
            existing.RemovalRequestedByUid = null;
            existing.RemovalRequestedAtUtc = null;
            existing.RemovalEffectiveAtUtc = null;
        }
        else
        {
            db.GroupMembers.Add(new GroupMember
            {
                Id = Guid.NewGuid(),
                GroupId = group.Id,
                Uid = uid,
                Status = GroupMemberStatus.Active,
                JoinedAtUtc = now,
                CanApproveAfterUtc = canApproveAfter
            });
        }
        await db.SaveChangesAsync();

        var joinerName = (await db.Users.FindAsync(uid))?.DisplayName ?? "A new member";
        foreach (var member in activeMembers.Where(m => m.Uid != uid))
        {
            await notifier.NotifyAsync(
                member.Uid, "GROUP_MEMBER_JOINED",
                new { groupId = group.Id, groupName = group.Name, uid, displayName = joinerName },
                group.Name, $"{joinerName} joined the group.");
        }

        return await ToDtoAsync(group);
    }

    [HttpGet]
    public async Task<ActionResult<List<GroupDto>>> ListGroups()
    {
        var uid = User.Uid();
        var groupIds = await db.GroupMembers
            .Where(m => m.Uid == uid && m.Status == GroupMemberStatus.Active)
            .Select(m => m.GroupId)
            .ToListAsync();
        var groups = await db.Groups.Where(g => groupIds.Contains(g.Id)).ToListAsync();

        var result = new List<GroupDto>();
        foreach (var group in groups.OrderBy(g => g.CreatedAtUtc))
            result.Add(await ToDtoAsync(group));
        return result;
    }

    /// <summary>
    /// Leave the group (uid == caller) or, as owner, remove another member.
    /// Same mechanic as pairing removal: NOT instant — the membership keeps
    /// working through a cooldown and every member is told immediately.
    /// </summary>
    [HttpDelete("{id:guid}/members/{memberUid}")]
    public async Task<ActionResult<GroupDto>> RemoveMember(Guid id, string memberUid)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var group = await db.Groups.FindAsync(id);
        if (group is null) return NotFound();

        var members = await db.GroupMembers
            .Where(m => m.GroupId == id && m.Status == GroupMemberStatus.Active)
            .ToListAsync();
        var caller = members.FirstOrDefault(m => m.Uid == uid);
        var target = members.FirstOrDefault(m => m.Uid == memberUid);
        if (caller is null || target is null) return NotFound();
        if (memberUid != uid && group.OwnerUid != uid)
            return Forbid();
        if (target.RemovalRequestedAtUtc != null)
            return Conflict(new { message = "Removal is already pending for this member." });

        var cooldownHours = config.GetValue("Accountability:RemovalCooldownHours", 24);
        target.RemovalRequestedByUid = uid;
        target.RemovalRequestedAtUtc = now;
        target.RemovalEffectiveAtUtc = now.AddHours(cooldownHours);
        await db.SaveChangesAsync();

        var names = await DisplayNamesAsync([uid, memberUid]);
        var initiatorName = names.GetValueOrDefault(uid) ?? "A member";
        var targetName = names.GetValueOrDefault(memberUid) ?? "a member";
        var body = memberUid == uid
            ? $"{initiatorName} is leaving the group. Their membership stays active for {cooldownHours} more hours."
            : $"{initiatorName} is removing {targetName} from the group. The membership stays active for {cooldownHours} more hours.";
        foreach (var member in members.Where(m => m.Uid != uid))
        {
            await notifier.NotifyAsync(
                member.Uid, "GROUP_MEMBER_REMOVAL_STARTED",
                new
                {
                    groupId = group.Id,
                    groupName = group.Name,
                    uid = memberUid,
                    requestedByUid = uid,
                    effectiveAtUtc = target.RemovalEffectiveAtUtc
                },
                group.Name, body);
        }

        return await ToDtoAsync(group);
    }

    private async Task<GroupDto> ToDtoAsync(ApproverGroup group)
    {
        var members = await db.GroupMembers
            .Where(m => m.GroupId == group.Id && m.Status == GroupMemberStatus.Active)
            .OrderBy(m => m.JoinedAtUtc)
            .ToListAsync();
        var uids = members.Select(m => m.Uid).ToList();
        var names = await DisplayNamesAsync(uids);
        // Coarse presence (the plan's cut line): last device heartbeat only.
        var lastSeen = await db.Devices
            .Where(d => uids.Contains(d.Uid))
            .GroupBy(d => d.Uid)
            .Select(g => new { Uid = g.Key, Last = g.Max(d => d.LastHeartbeatAtUtc) })
            .ToDictionaryAsync(x => x.Uid, x => x.Last);

        return new GroupDto(
            group.Id, group.Name, group.OwnerUid, group.CreatedAtUtc,
            members.Select(m => GroupMemberDto.From(
                m, names.GetValueOrDefault(m.Uid),
                lastSeen.TryGetValue(m.Uid, out var seen) ? seen : null)).ToList());
    }

    private Task<Dictionary<string, string?>> DisplayNamesAsync(IReadOnlyCollection<string> uids) =>
        db.Users.Where(u => uids.Contains(u.Uid)).ToDictionaryAsync(u => u.Uid, u => u.DisplayName);
}
