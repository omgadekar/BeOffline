package com.beoffline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.CachedUnlockRequest
import com.beoffline.app.data.model.Partner
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.AccentSurfaceDim
import com.beoffline.app.ui.theme.BoAvatar
import com.beoffline.app.ui.theme.BoCard
import com.beoffline.app.ui.theme.BoChip
import com.beoffline.app.ui.theme.BoEyebrow
import com.beoffline.app.ui.theme.BoFadedDivider
import com.beoffline.app.ui.theme.BoHairline
import com.beoffline.app.ui.theme.BoPrimaryButton
import com.beoffline.app.ui.theme.BoSecondaryButton
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.ui.theme.TextTertiary
import com.beoffline.app.util.firstName
import kotlinx.coroutines.delay

/**
 * Social — the accountability tab: who's holding you to it, what they're being
 * asked right now, and the groups you're in.
 *
 * People are named by first name throughout. The full display name is still in
 * the cache and still shown in group member management, where telling two
 * people apart is the whole job; here, it's a person you know.
 */
@Composable
fun AccountabilityScreen(
    onOpenChat: (String) -> Unit,
    viewModel: AccountabilityViewModel = hiltViewModel(),
    groupsViewModel: GroupsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val groupsState by groupsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showGroupManager by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.checkConfiguration(context) }

    if (showGroupManager) {
        GroupsScreen(
            onBack = { showGroupManager = false },
            onOpenChat = { groupId ->
                showGroupManager = false
                onOpenChat(groupId)
            },
            viewModel = groupsViewModel
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Brand900)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            BoScreenHeaderWithName(
                signedIn = uiState.signedIn,
                userName = uiState.userName
            )
            BoFadedDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                if (!uiState.signedIn) {
                    item(key = "signin") {
                        BoCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Sign in to pair up",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                "An account keeps your pairing alive even if you reinstall the app. Nothing about your rules or your traffic goes with it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (!uiState.googleConfigured) {
                                Text(
                                    "Google sign-in isn't configured in this build yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusDanger,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            BoPrimaryButton(
                                text = "Continue with Google",
                                onClick = { viewModel.signIn(context) },
                                enabled = uiState.googleConfigured && !uiState.busy,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    return@LazyColumn
                }

                // Incoming requests first — they're time-critical.
                items(uiState.incomingPending, key = { it.id }) { request ->
                    IncomingRequestCard(
                        request = request,
                        enabled = !uiState.busy,
                        onApprove = { minutes -> viewModel.respond(request, true, minutes) },
                        onDeny = { viewModel.respond(request, false, null) }
                    )
                }

                item(key = "partner") {
                    PartnerCard(
                        uiState = uiState,
                        onCreateInvite = viewModel::createInvite,
                        onAccept = viewModel::acceptInvite,
                        onRemove = viewModel::removePartner
                    )
                }

                item(key = "groups") {
                    GroupsCard(
                        groups = groupsState.groups,
                        onOpenChat = onOpenChat,
                        onManage = { showGroupManager = true }
                    )
                }

                if (uiState.recentRequests.isNotEmpty()) {
                    item(key = "recent") {
                        RecentRequestsSection(
                            requests = uiState.recentRequests,
                            enabled = !uiState.busy,
                            onClear = viewModel::clearRecent
                        )
                    }
                }
            }
        }

        uiState.message?.let { message ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = Brand800,
                contentColor = TextPrimary,
                action = {
                    Text(
                        text = "OK",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentBright,
                        modifier = Modifier
                            .clickable(role = Role.Button) { viewModel.dismissMessage() }
                            .padding(8.dp)
                    )
                }
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun BoScreenHeaderWithName(signedIn: Boolean, userName: String?) {
    Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 14.dp)) {
        Text("Accountability", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text(
            text = if (signedIn) "Signed in as ${userName.firstName(fallback = "you")}" else "Not signed in",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun IncomingRequestCard(
    request: CachedUnlockRequest,
    enabled: Boolean,
    onApprove: (Int) -> Unit,
    onDeny: () -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(15) }

    BoCard(modifier = Modifier.fillMaxWidth(), accented = true) {
        BoEyebrow("Waiting on you", color = AccentSecondary)
        Text(
            text = "${request.requesterName.firstName()} asks to open ${request.appLabel}.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.padding(top = 10.dp)
        )
        request.groupName?.let { group ->
            Text(
                "Via $group — first response counts.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            "Grant time",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 14.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(5, 15, 30, 60).forEach { minutes ->
                BoChip(
                    label = "${minutes}m",
                    selected = minutes == selectedMinutes,
                    onClick = { if (enabled) selectedMinutes = minutes },
                    shape = BoShape.Pill
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            BoPrimaryButton(
                text = "Approve ${selectedMinutes}m",
                onClick = { onApprove(selectedMinutes) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            BoSecondaryButton(text = "Deny", onClick = onDeny, enabled = enabled)
        }
    }
}

@Composable
private fun PartnerCard(
    uiState: AccountabilityUiState,
    onCreateInvite: () -> Unit,
    onAccept: (String) -> Unit,
    onRemove: (Partner) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }

    // Ticks once a minute so the removal-cooldown countdown stays current
    // without the user needing to leave and reopen the screen.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    BoCard(modifier = Modifier.fillMaxWidth()) {
        BoEyebrow("Your partner")

        uiState.partners.forEach { partner ->
            val name = partner.partnerName.firstName(fallback = "Your partner")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                BoAvatar(name = name, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    val subtitle = when {
                        partner.removalPending -> partner.removalEffectiveAtUtc?.let {
                            "Removal in ${formatCooldownRemaining(it, now)}"
                        } ?: "Removal pending"
                        partner.canApproveAfterUtc > now -> "New pairing — can approve soon"
                        else -> "Active"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (partner.removalPending) StatusDanger else TextTertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (!partner.removalPending) {
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextDisabled,
                        modifier = Modifier
                            .clip(BoShape.Control)
                            .clickable(role = Role.Button) { onRemove(partner) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // One 1:1 partner at a time — the invite/pair controls only appear when
        // you have none. To pair with someone else, remove this partner first;
        // to involve several people at once, use a Group instead.
        if (uiState.partners.isNotEmpty()) {
            Text(
                text = "One partner at a time. Remove them to pair with someone else, or use a group for several people.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDisabled,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            Text(
                text = "No partner yet. Share an invite code, or enter the one they sent you.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(top = 8.dp)
            )

            uiState.inviteCode?.let { code ->
                Spacer(Modifier.height(14.dp))
                Text(
                    "Share this code (valid 24h)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Text(
                    text = code,
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 6.sp),
                    color = AccentSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Create invite code",
                style = MaterialTheme.typography.labelLarge,
                color = AccentBright,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .border(1.dp, AccentPrimary, BoShape.Control)
                    .clickable(enabled = !uiState.busy, role = Role.Button) { onCreateInvite() }
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            )

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase().take(6) },
                    placeholder = { Text("Enter code", color = TextDisabled) },
                    singleLine = true,
                    shape = BoShape.Control,
                    colors = boFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                BoPrimaryButton(
                    text = "Pair",
                    onClick = { onAccept(codeInput); codeInput = "" },
                    enabled = codeInput.length == 6 && !uiState.busy
                )
            }
        }
    }
}

@Composable
private fun GroupsCard(
    groups: List<GroupUi>,
    onOpenChat: (String) -> Unit,
    onManage: () -> Unit
) {
    BoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoEyebrow("Groups", modifier = Modifier.weight(1f))
            Text(
                text = "Manage",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .clickable(role = Role.Button, onClick = onManage)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (groups.isEmpty()) {
            Text(
                text = "Ask several people at once — the first response counts. Groups get a chat too.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        groups.forEachIndexed { index, group ->
            if (index > 0) BoHairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpenChat(group.groupId) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentSurfaceDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Groups,
                        contentDescription = null,
                        tint = AccentSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text(
                        text = "${group.members.size} member" + if (group.members.size == 1) "" else "s",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextDisabled,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentRequestsSection(
    requests: List<CachedUnlockRequest>,
    enabled: Boolean,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoEyebrow("Recent requests", modifier = Modifier.weight(1f))
            Text(
                text = "Clear",
                style = MaterialTheme.typography.labelLarge,
                color = TextDisabled,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClear)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        // Bounded and independently scrollable — the history never pushes the
        // rest of the screen off into eternity.
        Column(
            modifier = Modifier
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            requests.forEach { request -> RecentRequestRow(request) }
        }
    }
}

@Composable
private fun RecentRequestRow(request: CachedUnlockRequest) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            val who = if (request.direction == "OUTGOING") "You" else request.requesterName.firstName()
            Text(
                text = "$who → ${request.appLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            val detail = listOfNotNull(
                request.groupName?.let { "via $it" },
                request.resolvedByName?.let { "resolved by ${it.firstName(fallback = it)}" }
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDisabled,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = request.status,
            style = MaterialTheme.typography.labelMedium,
            color = when (request.status) {
                "Approved" -> AccentSecondary
                "Denied", "Expired" -> TextTertiary
                else -> TextSecondary
            }
        )
    }
}
