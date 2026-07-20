package com.beoffline.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.accountability.GroupMemberDto
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import java.time.Instant

/**
 * Approver groups (M4): create/join via invite code, member list with coarse
 * last-seen presence, leave/remove with the visible cooldown, entry to chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    onOpenChat: (groupId: String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Brand900,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand900,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!uiState.signedIn) {
                item {
                    GroupSectionCard {
                        Text("Sign in first", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            "Groups need an account — sign in on the Accountability Partner screen, then come back here.",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary
                        )
                    }
                }
            } else {
                items(uiState.groups, key = { it.groupId }) { group ->
                    GroupCard(
                        group = group,
                        myUid = uiState.myUid,
                        inviteCode = uiState.inviteCode.takeIf { uiState.inviteGroupId == group.groupId },
                        busy = uiState.busy,
                        onInvite = { viewModel.createInvite(group.groupId) },
                        onChat = { onOpenChat(group.groupId) },
                        onRemoveMember = { uid -> viewModel.removeMember(group.groupId, uid) }
                    )
                }

                item { CreateGroupCard(busy = uiState.busy, onCreate = viewModel::createGroup) }
                item { JoinGroupCard(busy = uiState.busy, onJoin = viewModel::joinGroup) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun GroupSectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Brand800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun GroupCard(
    group: GroupUi,
    myUid: String?,
    inviteCode: String?,
    busy: Boolean,
    onInvite: () -> Unit,
    onChat: () -> Unit,
    onRemoveMember: (String) -> Unit
) {
    val isOwner = group.ownerUid == myUid

    GroupSectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "${group.members.size} member${if (group.members.size != 1) "s" else ""}" +
                        if (isOwner) " · you own this group" else "",
                    style = MaterialTheme.typography.bodySmall, color = TextDisabled
                )
            }
            Button(
                onClick = onChat,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = Color.White)
            ) { Text("Chat") }
        }

        group.members.forEach { member ->
            MemberRow(
                member = member,
                isMe = member.uid == myUid,
                canRemove = !busy && member.removalPending.not() && (member.uid == myUid || isOwner),
                onRemove = { onRemoveMember(member.uid) }
            )
        }

        inviteCode?.let { code ->
            Text("Share this code (valid 24h):", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(
                code,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold, letterSpacing = 6.sp
                ),
                color = AccentSecondary
            )
        }

        TextButton(onClick = onInvite, enabled = !busy) {
            Text("Create invite code", color = AccentPrimary)
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMemberDto,
    isMe: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                (member.displayName ?: member.uid) + if (isMe) " (you)" else "",
                style = MaterialTheme.typography.bodyLarge, color = TextPrimary
            )
            val subtitle = when {
                member.removalPending -> "Removal pending — active until cooldown ends"
                isInApprovalCooldown(member) -> "New member — can approve soon"
                else -> "Last seen ${lastSeenText(member.lastSeenAtUtc)}"
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (member.removalPending) StatusDanger else TextSecondary
            )
        }
        if (canRemove) {
            TextButton(onClick = onRemove) {
                Text(if (isMe) "Leave" else "Remove", color = TextDisabled)
            }
        }
    }
}

@Composable
private fun CreateGroupCard(busy: Boolean, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    GroupSectionCard {
        Text("Start a group", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(
            "Up to 10 people. Anyone in the group can approve your unlock requests — the first response counts.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(64) },
                placeholder = { Text("Group name", color = TextDisabled) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = Brand600,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Button(
                onClick = { onCreate(name); name = "" },
                enabled = name.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = Color.White)
            ) { Text("Create") }
        }
    }
}

@Composable
private fun JoinGroupCard(busy: Boolean, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    GroupSectionCard {
        Text("Join a group", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(6) },
                placeholder = { Text("Enter code", color = TextDisabled) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = Brand600,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Button(
                onClick = { onJoin(code); code = "" },
                enabled = code.length == 6 && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = Color.White)
            ) { Text("Join") }
        }
    }
}

private fun isInApprovalCooldown(member: GroupMemberDto): Boolean = try {
    Instant.parse(member.canApproveAfterUtc).toEpochMilli() > System.currentTimeMillis()
} catch (_: Exception) {
    false
}

private fun lastSeenText(lastSeenAtUtc: String?): String {
    val millis = try {
        lastSeenAtUtc?.let { Instant.parse(it).toEpochMilli() }
    } catch (_: Exception) {
        null
    } ?: return "never"
    val ageMinutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        ageMinutes < 60 -> "recently"
        ageMinutes < 24 * 60 -> "${ageMinutes / 60}h ago"
        else -> "${ageMinutes / (24 * 60)}d ago"
    }
}
