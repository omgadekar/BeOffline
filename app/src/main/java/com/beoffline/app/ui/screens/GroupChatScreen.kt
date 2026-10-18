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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
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
import com.beoffline.app.data.model.ChatMessageCache
import com.beoffline.app.ui.screens.GroupChatViewModel.ChatMember
import com.beoffline.app.ui.screens.GroupChatViewModel.ReportReason
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.OnAccent
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.util.firstName
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
    val groupName by viewModel.groupName.collectAsState()
    val memberCount by viewModel.memberCount.collectAsState()
    val members by viewModel.members.collectAsState()
    val mentionNames by viewModel.mentionNames.collectAsState()
    val chatNames by viewModel.chatNames.collectAsState()
    val isOwner by viewModel.isOwner.collectAsState()
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

    val items = remember(messages) { buildChatItems(messages, myUid) }

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
                    Column {
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
                                // Your own words, and a message still on its way
                                // out, have no server id to report yet.
                                onLongPress = if (item.mine || item.message.pending) {
                                    null
                                } else {
                                    { actionsFor = item.message }
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
            body = "You stop seeing this conversation, and the people here stop being able to " +
                "reach you through it. Everyone is told, and it takes effect after a cooldown.",
            confirmLabel = "Leave",
            onConfirm = {
                showLeaveConfirm = false
                viewModel.leaveGroup()
            },
            onDismiss = { showLeaveConfirm = false }
        )
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
}

private fun keyFor(item: ChatItem): String = when (item) {
    is ChatItem.Day -> "day-${item.label}"
    is ChatItem.Msg -> item.message.id
}

/** Flattens the message list into day dividers + grouped bubbles (messages are ascending). */
private fun buildChatItems(messages: List<ChatMessageCache>, myUid: String?): List<ChatItem> {
    val items = mutableListOf<ChatItem>()
    var lastDay: Long? = null
    var lastSender: String? = null
    for (message in messages) {
        val day = dayStamp(message.sentAtUtc)
        val newDay = day != lastDay
        if (newDay) {
            items.add(ChatItem.Day(dayLabel(message.sentAtUtc)))
            lastSender = null
        }
        val mine = message.senderUid == myUid
        val showHeader = newDay || message.senderUid != lastSender
        items.add(ChatItem.Msg(message, mine, showHeader))
        lastDay = day
        lastSender = message.senderUid
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
    /** Null when there is nothing to offer for this message. */
    onLongPress: (() -> Unit)? = null
) {
    val message = item.message
    // First name only. A member who has since left the group isn't in the map,
    // so fall back to shortening whatever name rode along with the message.
    val senderName = chatNames[message.senderUid] ?: message.senderName.firstName(fallback = "Member")
    if (item.mine) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Bubble(
                message = message, mine = true, senderName = senderName,
                mentionNames = mentionNames, mentionsMe = false,
                onLongPress = onLongPress
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
                onLongPress = onLongPress
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    message: ChatMessageCache,
    mine: Boolean,
    senderName: String,
    showName: Boolean = false,
    mentionNames: List<String>,
    mentionsMe: Boolean,
    onLongPress: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (mine) 16.dp else 5.dp,
        bottomEnd = if (mine) 5.dp else 16.dp
    )
    var bubble = Modifier
        .widthIn(max = 300.dp)
        .background(color = if (mine) AccentPrimary else Brand800, shape = shape)
    if (mentionsMe) bubble = bubble.border(1.5.dp, AccentSecondary, shape)
    if (onLongPress != null) {
        bubble = bubble.combinedClickable(
            onClick = {},
            onLongClick = onLongPress,
            onLongClickLabel = "Message options"
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
        val mentionColor = if (mine) AccentBright else AccentPrimary
        Text(
            annotateMentions(message.body, mentionNames, mentionColor),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
        Text(
            if (message.pending) "sending…" else timeText(message.sentAtUtc),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.align(Alignment.End)
        )
    }
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
