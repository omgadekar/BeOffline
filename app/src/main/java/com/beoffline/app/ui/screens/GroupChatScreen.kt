package com.beoffline.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.accountability.GroupMemberDto
import com.beoffline.app.data.model.CachedUnlockRequest
import com.beoffline.app.data.model.ChatMessageCache
import com.beoffline.app.ui.screens.GroupChatViewModel.ChatMember
import com.beoffline.app.ui.screens.GroupChatViewModel.ReportReason
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentGlow
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.AccentSurface
import com.beoffline.app.ui.theme.BoChip
import com.beoffline.app.ui.theme.BoPrimaryButton
import com.beoffline.app.ui.theme.BoSecondaryButton
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.OnAccent
import com.beoffline.app.ui.theme.OnAccentDim
import com.beoffline.app.ui.theme.OnAccentMention
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.util.firstName
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Group chat (M4): live via SignalR/FCM, offline sends queue through the outbox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    onBack: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val groupName by viewModel.groupName.collectAsState()
    val memberCount by viewModel.memberCount.collectAsState()
    val members by viewModel.members.collectAsState()
    val mentionNames by viewModel.mentionNames.collectAsState()
    val chatNames by viewModel.chatNames.collectAsState()
    val isOwner by viewModel.isOwner.collectAsState()
    val info by viewModel.info.collectAsState()
    val removalAt by viewModel.removalAt.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val left by viewModel.left.collectAsState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val myUid = viewModel.myUid

    // Long-pressed message, and the two dialogs the chat's own menu can open.
    var actionsFor by remember { mutableStateOf<ChatMessageCache?>(null) }
    var reportFor by remember { mutableStateOf<ChatMessageCache?>(null) }
    var removeFor by remember { mutableStateOf<ChatMessageCache?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    // Leaving closes the conversation: there is nothing left to look at.
    LaunchedEffect(left) {
        if (left) onBack()
    }

    // Mute notifications for this conversation while it's on screen.
    DisposableEffect(Unit) {
        viewModel.setOnScreen(true)
        onDispose { viewModel.setOnScreen(false) }
    }

    // Ticks so a message that never got out stops claiming to be "sending…",
    // and so a request's countdown to expiry stays honest, without either
    // needing a new emission from the database.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }

    val items = remember(messages, requests) { buildChatItems(messages, requests, myUid) }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    // Active @-mention query (null when the cursor isn't in a mention token).
    val query = mentionQuery(input)
    val suggestions = if (query != null) {
        members.filter { it.name.contains(query, ignoreCase = true) }.take(5)
    } else {
        emptyList()
    }

    Scaffold(
        containerColor = Brand900,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable { showInfo = true }) {
                        Text(groupName, style = MaterialTheme.typography.titleMedium)
                        if (memberCount > 0) {
                            Text(
                                "$memberCount member${if (memberCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Group info", color = TextPrimary) },
                                onClick = {
                                    menuOpen = false
                                    showInfo = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Leave this group", color = TextPrimary) },
                                onClick = {
                                    menuOpen = false
                                    showLeaveConfirm = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand900,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Your own leave is instant, so this can only be somebody else's
            // decision about you. It used to happen with nothing on screen to
            // say so: the group simply stayed, and then one day did not.
            removalAt?.let { at -> RemovalBanner(at = at, now = now) }

            if (items.isEmpty()) {
                EmptyChat(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(items.size, key = { keyFor(items[it]) }) { index ->
                        when (val item = items[index]) {
                            is ChatItem.Day -> DayDivider(item.label)
                            is ChatItem.Msg -> MessageRow(
                                item = item,
                                mentionNames = mentionNames,
                                mentionsMe = myUid != null &&
                                    item.message.mentionedUids?.split(",")?.contains(myUid) == true,
                                chatNames = chatNames,
                                now = now,
                                // Your own words, and a message still on its way
                                // out, have no server id to report yet.
                                onLongPress = if (item.mine || item.message.pending) {
                                    null
                                } else {
                                    { actionsFor = item.message }
                                },
                                onRetry = { viewModel.retry(item.message) }
                            )
                            is ChatItem.Request -> RequestRow(
                                item = item,
                                now = now,
                                enabled = !busy,
                                onRespond = { approve, minutes ->
                                    viewModel.respond(item.request, approve, minutes)
                                }
                            )
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                MentionSuggestions(
                    members = suggestions,
                    onPick = { member -> input = applyMention(input, member.name) }
                )
            }

            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    viewModel.send(input.text)
                    input = TextFieldValue("")
                }
            )
        }
    }

    actionsFor?.let { message ->
        val name = chatNames[message.senderUid] ?: message.senderName.firstName(fallback = "this member")
        MessageActionsDialog(
            senderName = name,
            canRemove = isOwner,
            onReport = {
                reportFor = message
                actionsFor = null
            },
            onRemove = {
                removeFor = message
                actionsFor = null
            },
            onDismiss = { actionsFor = null }
        )
    }

    reportFor?.let { message ->
        ReportReasonDialog(
            busy = busy,
            onPick = { reason ->
                viewModel.report(message.id, reason)
                reportFor = null
            },
            onDismiss = { reportFor = null }
        )
    }

    removeFor?.let { message ->
        val name = chatNames[message.senderUid] ?: message.senderName.firstName(fallback = "this member")
        ConfirmDialog(
            title = "Remove $name?",
            body = "They stop being part of this group after a cooldown, and everyone here " +
                "is told. You can invite them back later.",
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.removeMember(message.senderUid)
                removeFor = null
            },
            onDismiss = { removeFor = null }
        )
    }

    if (showLeaveConfirm) {
        ConfirmDialog(
            title = "Leave $groupName?",
            body = "Straight away: you stop seeing this conversation, and the people here stop " +
                "being able to reach you through it. Everyone still in it is told. You can be " +
                "invited back later.",
            confirmLabel = "Leave",
            onConfirm = {
                showLeaveConfirm = false
                viewModel.leaveGroup()
            },
            onDismiss = { showLeaveConfirm = false }
        )
    }

    if (showInfo) {
        info?.let { group ->
            GroupInfoDialog(
                info = group,
                myUid = myUid,
                chatNames = chatNames,
                onDismiss = { showInfo = false }
            )
        }
    }
}

// ── Group info ──────────────────────────────────────────────────

/**
 * Who is in here, who owns it, and since when.
 *
 * Owner rather than creator: ownership moves to the longest-standing member
 * when an owner leaves, and the account that originally made the group is not
 * recorded once that has happened. Saying "created by" would be a guess that
 * is wrong exactly when it matters.
 */
@Composable
private fun GroupInfoDialog(
    info: GroupChatViewModel.GroupInfo,
    myUid: String?,
    chatNames: Map<String, String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = Brand800,
        onDismissRequest = onDismiss,
        title = { Text(info.name, color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${info.members.size} member${if (info.members.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                info.members.forEach { member ->
                    val name = chatNames[member.uid]
                        ?: member.displayName.firstName(fallback = "Member")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name = name, seed = member.uid)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name + if (member.uid == myUid) "  (you)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = memberDetail(member, isOwner = member.uid == info.ownerUid),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (member.removalPending) StatusDanger else TextSecondary,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = AccentBright) }
        }
    )
}

/** "Owner · in since 20 Jul", or what is happening to them instead. */
private fun memberDetail(member: GroupMemberDto, isOwner: Boolean): String {
    if (member.removalPending) return "On the way out of this group"
    val joined = runCatching {
        java.time.Instant.parse(member.joinedAtUtc).toEpochMilli()
    }.getOrNull()
    return listOfNotNull(
        if (isOwner) "Owner" else null,
        joined?.let { "in since " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it)) }
    ).joinToString(" · ").ifEmpty { "Member" }
}

/** Someone else has started removing you, and it has a clock on it. */
@Composable
private fun RemovalBanner(at: Long, now: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(Brand800, BoShape.Card)
            .border(1.dp, Brand600, BoShape.Card)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "You are being removed from this group",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = if (at > now) {
                    "It takes effect in ${formatCooldownRemaining(at, now)}. Until then nothing " +
                        "changes \u2014 or leave now from the menu."
                } else {
                    "It takes effect shortly."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ── Moderation dialogs ──────────────────────────────────────────────
// Play's user-generated-content policy: a chat that carries other people's
// words has to offer a way to report one and a way to get away from whoever
// wrote it. Long-press a message for the first; the header menu for the second.

@Composable
private fun MessageActionsDialog(
    senderName: String,
    canRemove: Boolean,
    onReport: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = Brand800,
        onDismissRequest = onDismiss,
        title = { Text("Message from $senderName", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DialogAction("Report this message", onClick = onReport)
                if (canRemove) DialogAction("Remove $senderName from the group", onClick = onRemove)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun ReportReasonDialog(
    busy: Boolean,
    onPick: (ReportReason) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = Brand800,
        onDismissRequest = onDismiss,
        title = { Text("What is wrong with it?", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "The message and who sent it come to us. We read every report.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                ReportReason.entries.forEach { reason ->
                    DialogAction(reason.label, enabled = !busy) { onPick(reason) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = Brand800,
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) },
        confirmButton = {
            // No destructive red anywhere in this app: leaving is possible,
            // never inviting.
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = AccentBright) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun DialogAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (enabled) TextPrimary else TextDisabled,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

// ── Message list model ─────────────────────────────────────────────────────────

private sealed interface ChatItem {
    data class Day(val label: String) : ChatItem

    /** [showHeader] = first message of a run from this sender (or after a day break). */
    data class Msg(val message: ChatMessageCache, val mine: Boolean, val showHeader: Boolean) : ChatItem

    /** An unlock request raised in this group. [mine] = you are the one asking. */
    data class Request(val request: CachedUnlockRequest, val mine: Boolean) : ChatItem
}

private fun keyFor(item: ChatItem): String = when (item) {
    is ChatItem.Day -> "day-${item.label}"
    is ChatItem.Msg -> item.message.id
    is ChatItem.Request -> "req-${item.request.id}"
}

/**
 * Flattens messages and requests into one ascending timeline with day
 * dividers.
 *
 * A request is part of the conversation, not an overlay on it: it lands where
 * it was asked, between the messages either side of it, the way a payment
 * request does in a messaging app. It also breaks a run of bubbles, so the
 * next message from the same person gets its name back.
 */
private fun buildChatItems(
    messages: List<ChatMessageCache>,
    requests: List<CachedUnlockRequest>,
    myUid: String?
): List<ChatItem> {
    val timeline = (
        messages.map { it.sentAtUtc to (it as Any) } +
            requests.map { it.requestedAtUtc to (it as Any) }
        ).sortedBy { it.first }

    val items = mutableListOf<ChatItem>()
    var lastDay: Long? = null
    var lastSender: String? = null
    for ((at, entry) in timeline) {
        val day = dayStamp(at)
        val newDay = day != lastDay
        if (newDay) {
            items.add(ChatItem.Day(dayLabel(at)))
            lastSender = null
        }
        when (entry) {
            is ChatMessageCache -> {
                val mine = entry.senderUid == myUid
                items.add(ChatItem.Msg(entry, mine, newDay || entry.senderUid != lastSender))
                lastSender = entry.senderUid
            }
            is CachedUnlockRequest -> {
                items.add(ChatItem.Request(entry, entry.direction == "OUTGOING"))
                lastSender = null
            }
        }
        lastDay = day
    }
    return items
}

// ── Pieces ─────────────────────────────────────────────────────────────────────

@Composable
private fun DayDivider(label: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier
                .background(Brand800, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MessageRow(
    item: ChatItem.Msg,
    mentionNames: List<String>,
    mentionsMe: Boolean,
    chatNames: Map<String, String>,
    now: Long,
    /** Null when there is nothing to offer for this message. */
    onLongPress: (() -> Unit)? = null,
    onRetry: () -> Unit = {}
) {
    val message = item.message
    // First name only. A member who has since left the group isn't in the map,
    // so fall back to shortening whatever name rode along with the message.
    val senderName = chatNames[message.senderUid] ?: message.senderName.firstName(fallback = "Member")
    if (item.mine) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Bubble(
                message = message, mine = true, senderName = senderName,
                mentionNames = mentionNames, mentionsMe = false, now = now,
                onLongPress = onLongPress, onRetry = onRetry
            )
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (item.showHeader) {
                Avatar(name = senderName, seed = message.senderUid)
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(8.dp))
            Bubble(
                message = message, mine = false, showName = item.showHeader,
                senderName = senderName, mentionNames = mentionNames, mentionsMe = mentionsMe,
                now = now, onLongPress = onLongPress, onRetry = onRetry
            )
        }
    }
}

/**
 * Your own bubble is a filled clay surface, so everything inside it has to be
 * written for clay. It used to carry the same near-black body text and grey
 * timestamp as a received bubble, which on this fill is close to invisible.
 * The mention colour went the wrong way too: [AccentBright] is a dark clay,
 * which on a clay fill is barely a highlight at all.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    message: ChatMessageCache,
    mine: Boolean,
    senderName: String,
    showName: Boolean = false,
    mentionNames: List<String>,
    mentionsMe: Boolean,
    now: Long,
    onLongPress: (() -> Unit)? = null,
    onRetry: () -> Unit = {}
) {
    // A send that has been in progress for a minute is not in progress.
    val stuck = message.pending && now - message.sentAtUtc > STUCK_AFTER_MS
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (mine) 16.dp else 5.dp,
        bottomEnd = if (mine) 5.dp else 16.dp
    )
    var bubble = Modifier
        .widthIn(max = 300.dp)
        .background(color = if (mine) AccentPrimary else Brand800, shape = shape)
    if (mentionsMe) bubble = bubble.border(1.5.dp, AccentSecondary, shape)
    if (onLongPress != null || stuck) {
        bubble = bubble.combinedClickable(
            onClick = { if (stuck) onRetry() },
            onClickLabel = if (stuck) "Send again" else null,
            onLongClick = onLongPress,
            onLongClickLabel = if (onLongPress != null) "Message options" else null
        )
    }

    Column(
        modifier = bubble.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (!mine && showName) {
            Text(
                senderName,
                style = MaterialTheme.typography.labelMedium,
                color = avatarColor(message.senderUid),
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            annotateMentions(
                message.body,
                mentionNames,
                if (mine) OnAccentMention else AccentPrimary
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (mine) OnAccent else TextPrimary
        )
        Text(
            text = when {
                stuck -> "Not sent · tap to try again"
                message.pending -> "sending…"
                else -> timeText(message.sentAtUtc)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (mine) OnAccentDim else TextSecondary,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

/** How long a send may sit on "sending" before it is called what it is. */
private const val STUCK_AFTER_MS = 60_000L

// ── Unlock requests, in the conversation ─────────────────────────────
// A group request is addressed to whoever is around rather than to one named
// person, and the chat is where "whoever is around" already is. So it is shown
// here, answerable in place, the way a payment request sits in a thread — and
// it is deliberately absent from the People tab, which carries 1:1 requests
// only. The same request offered in two places invites two people to answer it.

@Composable
private fun RequestRow(
    item: ChatItem.Request,
    now: Long,
    enabled: Boolean,
    onRespond: (approve: Boolean, minutes: Int?) -> Unit
) {
    val request = item.request
    if (item.mine) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            RequestCard(request, now, mine = true, enabled = enabled, onRespond = onRespond)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Seeded by name, not by request id: the same person keeps the
            // same colour across everything they ask for.
            Avatar(
                name = request.requesterName.firstName(fallback = "Member"),
                seed = request.requesterName ?: request.id
            )
            Spacer(Modifier.width(8.dp))
            RequestCard(request, now, mine = false, enabled = enabled, onRespond = onRespond)
        }
    }
}

@Composable
private fun RequestCard(
    request: CachedUnlockRequest,
    now: Long,
    mine: Boolean,
    enabled: Boolean,
    onRespond: (approve: Boolean, minutes: Int?) -> Unit
) {
    // "Pending" in the cache can mean "pending until it quietly expired a
    // while ago" — the server resolves that lazily, and until it does the
    // honest thing to show is that the window has closed.
    val lapsed = request.expiresAtUtc?.let { it <= now } == true
    val open = request.status in setOf("Pending", "Queued") && !lapsed
    val actionable = open && !mine && request.status == "Pending"
    var minutes by remember(request.id) { mutableIntStateOf(15) }

    Column(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .background(if (open) AccentSurface else Brand800, BoShape.Card)
            .border(1.dp, if (open) AccentGlow else Brand600, BoShape.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.LockOpen,
                contentDescription = null,
                tint = if (open) AccentSecondary else TextDisabled,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (open) "UNLOCK REQUEST" else "UNLOCK REQUEST · CLOSED",
                style = MaterialTheme.typography.labelSmall,
                color = if (open) AccentSecondary else TextDisabled
            )
        }

        Text(
            text = if (mine) {
                "You asked to open ${request.appLabel}"
            } else {
                "${request.requesterName.firstName()} asks to open ${request.appLabel}"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.padding(top = 6.dp)
        )

        RequestStatusLine(request, lapsed, open, mine, now)

        if (actionable) {
            Text(
                "Grant time",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 12.dp)
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 15, 30, 60).forEach { option ->
                    BoChip(
                        label = "${option}m",
                        selected = option == minutes,
                        onClick = { if (enabled) minutes = option },
                        shape = BoShape.Pill
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoPrimaryButton(
                    text = "Approve ${minutes}m",
                    onClick = { onRespond(true, minutes) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                BoSecondaryButton(
                    text = "Deny",
                    onClick = { onRespond(false, null) },
                    enabled = enabled
                )
            }
        }

        Text(
            timeText(request.requestedAtUtc),
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled,
            modifier = Modifier.align(Alignment.End).padding(top = 6.dp)
        )
    }
}

/** The one line under the ask: what happened to it, or how long is left. */
@Composable
private fun RequestStatusLine(
    request: CachedUnlockRequest,
    lapsed: Boolean,
    open: Boolean,
    mine: Boolean,
    now: Long
) {
    val resolver = request.resolvedByName?.firstName(fallback = "a member")
    val text = when {
        request.status == "Queued" && open -> "Waiting to reach the group…"
        request.status == "Approved" -> listOfNotNull(
            "Approved",
            request.grantedUntilUtc
                ?.takeIf { it > now }
                ?.let { "open until ${timeText(it)}" },
            resolver?.let { "by $it" }
        ).joinToString(" · ")
        request.status == "Denied" -> listOfNotNull("Denied", resolver?.let { "by $it" }).joinToString(" · ")
        request.status == "Expired" || lapsed -> "Nobody answered in time"
        request.status == "Cancelled" -> "Withdrawn"
        mine -> "Waiting on the group"
        else -> remainingText(request.expiresAtUtc, now)
            ?.let { "Whoever answers first decides · $it left" }
            ?: "Whoever answers first decides"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            request.status == "Approved" -> AccentSecondary
            request.status == "Expired" || lapsed -> StatusDanger
            else -> TextSecondary
        },
        modifier = Modifier.padding(top = 3.dp)
    )
}

/** Coarse time left, e.g. "12m". Null once there is none. */
private fun remainingText(expiresAtUtc: Long?, now: Long): String? {
    val left = (expiresAtUtc ?: return null) - now
    if (left <= 0) return null
    val minutes = left / 60_000
    return if (minutes >= 1) "${minutes}m" else "under a minute"
}

@Composable
private fun MentionSuggestions(members: List<ChatMember>, onPick: (ChatMember) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .background(Brand800)
            .padding(vertical = 4.dp)
    ) {
        members.forEach { member ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(member) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(name = member.name, seed = member.uid)
                Spacer(Modifier.width(10.dp))
                Text(member.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun Avatar(name: String?, seed: String) {
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(avatarColor(seed), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChatInputBar(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.copy(text = it.text.take(2000))) },
            placeholder = { Text("Message  ·  @ to mention", color = TextDisabled) },
            modifier = Modifier.weight(1f),
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = Brand600,
                focusedContainerColor = Brand800,
                unfocusedContainerColor = Brand800,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
        val enabled = value.text.isNotBlank()
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(if (enabled) AccentPrimary else Brand800, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onSend, enabled = enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled) OnAccent else TextDisabled
                )
            }
        }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            tint = TextDisabled,
            modifier = Modifier.size(40.dp)
        )
        Text("No messages yet", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(
            "Say hello, cheer someone on, or check in on the group's goals.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
    }
}

// ── Mention helpers ──────────────────────────────────────────────────────────

/** The @-token being typed at the cursor, or null. Empty string means just "@". */
private fun mentionQuery(tfv: TextFieldValue): String? {
    val cursor = tfv.selection.end
    if (cursor <= 0 || cursor > tfv.text.length) return null
    val upToCursor = tfv.text.substring(0, cursor)
    val at = upToCursor.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && !upToCursor[at - 1].isWhitespace()) return null // must start a token
    val q = upToCursor.substring(at + 1)
    if (q.any { it.isWhitespace() }) return null // a space ends the mention
    return q
}

/** Replaces the in-progress @token at the cursor with "@Name ". */
private fun applyMention(tfv: TextFieldValue, name: String): TextFieldValue {
    val cursor = tfv.selection.end
    val at = tfv.text.substring(0, cursor).lastIndexOf('@')
    if (at < 0) return tfv
    val insert = "@$name "
    val newText = tfv.text.substring(0, at) + insert + tfv.text.substring(cursor)
    return TextFieldValue(newText, TextRange(at + insert.length))
}

/** Colors every "@<memberName>" occurrence. Longer names first so full names win over prefixes. */
private fun annotateMentions(body: String, names: List<String>, color: Color): AnnotatedString =
    buildAnnotatedString {
        append(body)
        for (name in names.sortedByDescending { it.length }) {
            val token = "@$name"
            var i = body.indexOf(token)
            while (i >= 0) {
                addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), i, i + token.length)
                i = body.indexOf(token, i + token.length)
            }
        }
    }

// ── Time helpers ─────────────────────────────────────────────────────────────

private fun timeText(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

/** Day bucket (year * 1000 + day-of-year) for grouping. */
private fun dayStamp(millis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(millis: Long): String {
    val today = dayStamp(System.currentTimeMillis())
    val stamp = dayStamp(millis)
    return when (stamp) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
    }
}

/** Stable per-sender accent so each person reads consistently across the thread. */
private val AVATAR_COLORS = listOf(
    Color(0xFF4DB6AC), Color(0xFF9575CD), Color(0xFFF06292),
    Color(0xFF64B5F6), Color(0xFFFFB74D), Color(0xFF4DD0E1)
)

private fun avatarColor(seed: String): Color =
    AVATAR_COLORS[(seed.hashCode() and Int.MAX_VALUE) % AVATAR_COLORS.size]
