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
import androidx.compose.foundation.layout.width
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
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary

/**
 * Accountability partner area: sign-in, pairing via invite codes, partner
 * list (with removal-cooldown visibility), and incoming unlock requests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountabilityScreen(
    onBack: () -> Unit,
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

                if (uiState.recentRequests.isNotEmpty()) {
                    item {
                        Text("Recent requests", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    }
                    items(uiState.recentRequests, key = { "r-" + it.id }) { request ->
                        RecentRequestRow(request)
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

    SectionCard {
        Text("Your partner", style = MaterialTheme.typography.titleSmall, color = TextPrimary)

        if (uiState.partners.isEmpty()) {
            Text(
                "No partner yet. Share an invite code, or enter the one your partner sent you.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )
        }

        uiState.partners.forEach { partner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(partner.partnerName ?: partner.partnerUid, color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge)
                    val subtitle = when {
                        partner.removalPending -> "Removal pending — active until cooldown ends"
                        partner.canApproveAfterUtc > System.currentTimeMillis() -> "New pairing — can approve soon"
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

@Composable
private fun IncomingRequestCard(
    request: CachedUnlockRequest,
    enabled: Boolean,
    onApprove: (Int) -> Unit,
    onDeny: () -> Unit
) {
    SectionCard {
        Text(
            "${request.requesterName ?: "Your partner"} asks to open ${request.appLabel}",
            style = MaterialTheme.typography.titleSmall, color = TextPrimary
        )
        Text(
            "Approve for how long?",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 30, 60).forEach { minutes ->
                TextButton(
                    onClick = { onApprove(minutes) },
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = AccentPrimary.copy(alpha = 0.2f)
                    )
                ) { Text("${minutes}m", color = TextPrimary) }
            }
        }
        TextButton(onClick = onDeny, enabled = enabled) {
            Text("Deny", color = StatusDanger)
        }
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
