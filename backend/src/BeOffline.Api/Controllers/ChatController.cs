using BeOffline.Api.Auth;
using BeOffline.Api.Contracts;
using BeOffline.Api.Data;
using BeOffline.Api.Domain;
using BeOffline.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Controllers;

/// <summary>
/// Group chat (M4). Routes are group-scoped for now; ConversationKey already
/// carries a "group:"/"pair:" discriminator so 1:1 chat can be added without
/// touching the table.
/// </summary>
[ApiController]
[Authorize]
[Route("api/chat/groups/{groupId:guid}/messages")]
public sealed class ChatController(AppDbContext db, INotificationService notifier) : ControllerBase
{
    private static string KeyFor(Guid groupId) => $"group:{groupId}";

    [HttpPost]
    public async Task<ActionResult<ChatMessageDto>> Send(Guid groupId, SendChatMessageRequest request)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var body = request.Body.Trim();
        if (body.Length is < 1 or > 2000)
            return BadRequest(new { message = "Message must be 1-2000 characters." });

        var members = await ActiveMembersAsync(groupId);
        if (members.All(m => m.Uid != uid)) return NotFound();

        // Idempotent: the offline outbox may retry after a lost response.
        var existing = await db.ChatMessages.FirstOrDefaultAsync(m =>
            m.SenderUid == uid && m.ClientMessageId == request.ClientMessageId);
        if (existing is not null)
            return ChatMessageDto.From(existing, await DisplayNameAsync(uid));

        var message = new ChatMessage
        {
            Id = Guid.NewGuid(),
            ConversationKey = KeyFor(groupId),
            SenderUid = uid,
            Body = body,
            ClientMessageId = request.ClientMessageId,
            SentAtUtc = now
        };
        db.ChatMessages.Add(message);
        await db.SaveChangesAsync();

        var senderName = await DisplayNameAsync(uid) ?? "A member";
        var groupName = (await db.Groups.FindAsync(groupId))?.Name ?? "Group chat";
        var dto = ChatMessageDto.From(message, senderName);
        var preview = body.Length > 120 ? body[..120] + "…" : body;
        foreach (var member in members.Where(m => m.Uid != uid))
        {
            await notifier.NotifyAsync(
                member.Uid, "CHAT_MESSAGE", dto,
                groupName, $"{senderName}: {preview}");
        }

        return dto;
    }

    [HttpGet]
    public async Task<ActionResult<List<ChatMessageDto>>> List(
        Guid groupId, [FromQuery] DateTime? before, [FromQuery] int limit = 50)
    {
        var uid = User.Uid();
        var members = await ActiveMembersAsync(groupId);
        if (members.All(m => m.Uid != uid)) return NotFound();

        limit = Math.Clamp(limit, 1, 100);
        var key = KeyFor(groupId);
        var query = db.ChatMessages.Where(m => m.ConversationKey == key);
        if (before is { } cutoff)
            query = query.Where(m => m.SentAtUtc < cutoff);

        var messages = await query
            .OrderByDescending(m => m.SentAtUtc)
            .Take(limit)
            .ToListAsync();

        var senderUids = messages.Select(m => m.SenderUid).Distinct().ToList();
        var names = await db.Users
            .Where(u => senderUids.Contains(u.Uid))
            .ToDictionaryAsync(u => u.Uid, u => u.DisplayName);

        return messages.Select(m => ChatMessageDto.From(m, names.GetValueOrDefault(m.SenderUid))).ToList();
    }

    private Task<List<GroupMember>> ActiveMembersAsync(Guid groupId) =>
        db.GroupMembers
            .Where(m => m.GroupId == groupId && m.Status == GroupMemberStatus.Active)
            .ToListAsync();

    private async Task<string?> DisplayNameAsync(string uid) =>
        (await db.Users.FindAsync(uid))?.DisplayName;
}
