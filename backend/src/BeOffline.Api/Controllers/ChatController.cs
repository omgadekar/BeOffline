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

    /// <summary>
    /// Fixed report reasons. A closed set, not free text: a free-text reason
    /// would be a second channel of user-written content to moderate.
    /// </summary>
    public static readonly IReadOnlySet<string> ReportReasons = new HashSet<string>
    {
        "spam", "harassment", "hate", "sexual", "violence", "selfharm", "other"
    };

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
            return ChatMessageDto.From(existing, Names.First(await DisplayNameAsync(uid), "A member"));

        // Only real co-members can be mentioned (drops junk / self-mentions).
        var memberUids = members.Select(m => m.Uid).ToHashSet();
        var mentioned = (request.MentionedUids ?? [])
            .Where(u => u != uid && memberUids.Contains(u))
            .Distinct()
            .ToList();

        var message = new ChatMessage
        {
            Id = Guid.NewGuid(),
            ConversationKey = KeyFor(groupId),
            SenderUid = uid,
            Body = body,
            MentionedUids = mentioned.Count > 0 ? string.Join(",", mentioned) : null,
            ClientMessageId = request.ClientMessageId,
            SentAtUtc = now
        };
        db.ChatMessages.Add(message);
        await db.SaveChangesAsync();

        var senderName = Names.First(await DisplayNameAsync(uid), "A member");
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

        // First name only, matching what the send path stamps on new messages.
        return messages
            .Select(m => ChatMessageDto.From(m, Names.First(names.GetValueOrDefault(m.SenderUid), "A member")))
            .ToList();
    }

    /// <summary>
    /// Report a message (Play's UGC policy requires an in-app reporting path).
    /// Writes one <see cref="ModerationLog"/> row to the activity log; the
    /// reported text stays in its ChatMessages row rather than being copied.
    ///
    /// Idempotent like the send path: a reporter who taps twice, or whose
    /// response was lost, gets the first report back with AlreadyReported set,
    /// not a second row.
    /// </summary>
    [HttpPost("{messageId:guid}/reports")]
    public async Task<ActionResult<ChatMessageReportDto>> Report(
        Guid groupId, Guid messageId, ReportChatMessageRequest request)
    {
        var uid = User.Uid();
        var now = DateTime.UtcNow;

        var reason = (request.Reason ?? string.Empty).Trim().ToLowerInvariant();
        if (!ReportReasons.Contains(reason))
            return BadRequest(new { message = $"Reason must be one of: {string.Join(", ", ReportReasons)}." });

        var members = await ActiveMembersAsync(groupId);
        if (members.All(m => m.Uid != uid)) return NotFound();

        var key = KeyFor(groupId);
        var message = await db.ChatMessages
            .FirstOrDefaultAsync(m => m.Id == messageId && m.ConversationKey == key);
        if (message is null) return NotFound();
        if (message.SenderUid == uid)
            return BadRequest(new { message = "You can't report your own message." });

        var path = ModerationLog.PathFor(groupId, messageId);
        var existing = await db.ActivityLogs
            .Where(l => l.Method == ModerationLog.Method && l.Uid == uid && l.Path.StartsWith(path))
            .OrderBy(l => l.Id)
            .FirstOrDefaultAsync();
        if (existing is not null)
            return new ChatMessageReportDto(
                messageId, message.SenderUid, reason, existing.TimestampUtc, AlreadyReported: true);

        db.ActivityLogs.Add(new ActivityLog
        {
            Uid = uid,
            Method = ModerationLog.Method,
            Path = $"{path}?reason={reason}&sender={message.SenderUid}",
            StatusCode = StatusCodes.Status200OK,
            DurationMs = 0,
            // The request's own middleware row already carries the caller's IP;
            // the moderation row does not need a second copy.
            IpAddress = null,
            TimestampUtc = now
        });
        await db.SaveChangesAsync();

        return new ChatMessageReportDto(
            messageId, message.SenderUid, reason, now, AlreadyReported: false);
    }

    private Task<List<GroupMember>> ActiveMembersAsync(Guid groupId) =>
        db.GroupMembers
            .Where(m => m.GroupId == groupId && m.Status == GroupMemberStatus.Active)
            .ToListAsync();

    private async Task<string?> DisplayNameAsync(string uid) =>
        (await db.Users.FindAsync(uid))?.DisplayName;
}
