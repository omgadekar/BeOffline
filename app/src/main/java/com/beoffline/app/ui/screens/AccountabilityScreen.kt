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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.CachedUnlockRequest
import com.beoffline.app.data.model.Partner
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Accountability partner area: sign-in, pairing via invite codes, partner
 * list (with removal-cooldown visibility), and incoming unlock requests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountabilityScreen(
    onBack: () -> Unit,
    onOpenGroups: () -> Unit = {},
    viewModel: AccountabilityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.checkConfiguration(context) }
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
                title = { Text("Accountability Partner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand900,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    if (uiState.signedIn) {
                        TextButton(onClick = viewModel::signOut) {
                            Text("Sign out", color = TextSecondary)
                        }
                    }
                }
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
                    SectionCard {
                        Text("Sign in to pair up", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            "An account keeps your pairing alive even if you reinstall the app.",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary
                        )
                        if (!uiState.googleConfigured) {
                            Text(
                                "Google sign-in isn't configured in this build yet (enable the Google provider in Firebase console and refresh google-services.json).",
                                style = MaterialTheme.typography.bodySmall, color = StatusDanger
                            )
                        }
                        Button(
                            onClick = { viewModel.signIn(context) },
                            enabled = uiState.googleConfigured && !uiState.busy,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = Color.White)
                        ) { Text("Continue with Google") }
                    }
                }
            } else {
                item {
                    Text(
                        "Signed in as ${uiState.userName ?: "you"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDisabled
                    )
                }

                // Incoming requests first — they're time-critical.
                if (uiState.incomingPending.isNotEmpty()) {
                    item {
                        Text("Waiting on you", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    }
                    items(uiState.incomingPending, key = { it.id }) { request ->
                        IncomingRequestCard(
                            request = request,
                            enabled = !uiState.busy,
                            onApprove = { minutes -> viewModel.respond(request, true, minutes) },
                            onDeny = { viewModel.respond(request, false, null) }
                        )
                    }
                }

                item { PartnerSection(uiState, onCreateInvite = viewModel::createInvite, onAccept = viewModel::acceptInvite, onRemove = viewModel::removePartner) }

                item {
                    SectionCard {
                        Text("Groups", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            "Ask several people at once — the first response counts. Groups also get a chat.",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary
                        )
                        Button(
                            onClick = onOpenGroups,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = Color.White)
                        ) { Text("My groups") }
                    }
                }

                if (uiState.recentRequests.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Recent requests",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::clearRecent, enabled = !uiState.busy) {
                                    Text("Clear", color = TextDisabled)
                                }
                            }
                            // Bounded, independently scrollable — the history never
                            // pushes the rest of the screen off into eternity.
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                uiState.recentRequests.forEach { RecentRequestRow(it) }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
private fun PartnerSection(
    uiState: AccountabilityUiState,
    onCreateInvite: () -> Unit,
    onAccept: (String) -> Unit,
    onRemove: (Partner) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }

    // Ticks once a minute so the removal-cooldown countdown stays current
    // without the user needing to leave and reopen the screen.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    val hasPartner = uiState.partners.isNotEmpty()

    SectionCard {
        Text("Your partner", style = MaterialTheme.typography.titleSmall, color = TextPrimary)

        uiState.partners.forEach { partner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(partner.partnerName ?: partner.partnerUid, color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge)
                    val subtitle = when {
                        partner.removalPending -> partner.removalEffectiveAtUtc?.let {
                            "Removal in ${formatCooldownRemaining(it, now)}"
                        } ?: "Removal pending"
                        partner.canApproveAfterUtc > now -> "New pairing — can approve soon"
                        else -> "Active"
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = if (partner.removalPending) StatusDanger else TextSecondary)
                }
                if (!partner.removalPending) {
                    TextButton(onClick = { onRemove(partner) }) {
                        Text("Remove", color = TextDisabled)
                    }
                }
            }
        }

        // One 1:1 partner at a time — the invite/pair controls only appear when
        // you have none. To pair with someone else, remove this partner first;
        // to involve several people at once, use a Group instead.
        if (hasPartner) {
            Text(
                "You can have one accountability partner at a time. Remove them to pair with someone else, or use a Group for several people.",
                style = MaterialTheme.typography.bodySmall, color = TextDisabled
            )
        } else {
            Text(
                "No partner yet. Share an invite code, or enter the one your partner sent you.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )

            uiState.inviteCode?.let { code ->
                Text("Share this code (valid 24h):", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    code,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold, letterSpacing = 6.sp
                    ),
                    color = AccentSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCreateInvite, enabled = !uiState.busy) {
                    Text("Create invite code", color = AccentPrimary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase().take(6) },
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
                    onClick = { onAccept(codeInput); codeInput = "" },
                    enabled = codeInput.length == 6 && !uiState.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = Color.White)
                ) { Text("Pair") }
            }
        }
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

    SectionCard {
        Text(
            "${request.requesterName ?: "Your partner"} asks to open ${request.appLabel}",
            style = MaterialTheme.typography.titleSmall, color = TextPrimary
        )
        request.groupName?.let { group ->
            Text(
                "Via $group — first response counts.",
                style = MaterialTheme.typography.bodySmall, color = TextDisabled
            )
        }
        Text(
            "Grant time",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 30, 60).forEach { minutes ->
                DurationPill(
                    label = "${minutes}m",
                    selected = minutes == selectedMinutes,
                    enabled = enabled,
                    onClick = { selectedMinutes = minutes }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onApprove(selectedMinutes) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary, contentColor = Color.White
                )
            ) { Text("Approve ${selectedMinutes}m") }
            OutlinedButton(
                onClick = onDeny,
                enabled = enabled,
                border = BorderStroke(1.dp, StatusDanger),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
            ) { Text("Deny") }
        }
    }
}

@Composable
private fun DurationPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) AccentPrimary else Brand700,
        contentColor = if (selected) Color.White else TextSecondary
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun RecentRequestRow(request: CachedUnlockRequest) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${if (request.direction == "OUTGOING") "You" else request.requesterName ?: "Partner"} → ${request.appLabel}",
                style = MaterialTheme.typography.bodyMedium, color = TextPrimary
            )
            val detail = listOfNotNull(
                request.groupName?.let { "via $it" },
                request.resolvedByName?.let { "resolved by $it" }
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = TextDisabled)
            }
        }
        Text(
            request.status,
            style = MaterialTheme.typography.labelMedium,
            color = when (request.status) {
                "Approved" -> AccentSecondary
                "Denied", "Expired" -> StatusDanger
                else -> TextSecondary
            }
        )
    }
}
