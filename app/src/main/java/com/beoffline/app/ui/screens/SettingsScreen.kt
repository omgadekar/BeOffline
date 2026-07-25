package com.beoffline.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.beoffline.app.openblock.ChallengeKind
import com.beoffline.app.ui.challenge.ChallengePreviewOverlay
import com.beoffline.app.util.firstName
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.BoCard
import com.beoffline.app.ui.theme.BoChip
import com.beoffline.app.ui.theme.BoEyebrow
import com.beoffline.app.ui.theme.BoFadedDivider
import com.beoffline.app.ui.theme.BoHairline
import com.beoffline.app.ui.theme.BoScreenHeader
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.BoToggle
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.ui.theme.TextTertiary

/**
 * Settings — everything that used to live behind the dashboard's help sheet and
 * setup dialogs, on one honest page: what the app needs to keep working, what
 * an unlock costs you, and how to leave.
 */
@Composable
fun SettingsScreen(
    onRequestVpn: (List<String>) -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    lockViewModel: OpenBlockViewModel = hiltViewModel(),
    accountViewModel: AccountabilityViewModel = hiltViewModel()
) {
    val dashboard by dashboardViewModel.uiState.collectAsState()
    val lock by lockViewModel.uiState.collectAsState()
    val account by accountViewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    var showIssueDialog by remember { mutableStateOf(false) }
    var showHowItWorks by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showChallengePreview by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { dashboardViewModel.refreshBackgroundProtection() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dashboardViewModel.refreshBackgroundProtection()
                lockViewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val status = dashboard.backgroundProtection
    val permissions = listOf(
        PermissionRow(
            title = "Battery optimisation",
            sub = "Stops Android sleeping the rule engine",
            granted = status?.batteryOptimizationIgnored ?: false,
            onGrant = dashboardViewModel::openBatteryOptimizationSettings
        ),
        PermissionRow(
            title = "Notifications",
            sub = "Ongoing status while rules enforce",
            granted = status?.notificationsEnabled ?: false,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    dashboardViewModel.openNotificationSettings()
                }
            }
        ),
        PermissionRow(
            title = "VPN access",
            sub = "The local tunnel that blocks traffic",
            granted = dashboard.isVpnPermissionGranted,
            onGrant = { onRequestVpn(emptyList()) }
        ),
        PermissionRow(
            title = "Hide blocked-app notices",
            sub = "Optional — dismisses their alerts too",
            granted = status?.notificationAccessEnabled ?: false,
            onGrant = dashboardViewModel::openNotificationAccessSettings
        )
    )
    val outstanding = permissions.count { !it.granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand900)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        BoScreenHeader(
            title = "Settings",
            subtitle = when (outstanding) {
                0 -> "All four permissions allowed"
                1 -> "One permission still needed"
                else -> "$outstanding permissions still needed"
            }
        )
        BoFadedDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item(key = "reliability") {
                BoCard(modifier = Modifier.fillMaxWidth()) {
                    BoEyebrow("Reliability")
                    Text(
                        text = "Android sleeps background apps differently on every phone. These four keep rules enforcing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    permissions.forEach { permission ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    permission.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    permission.sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            if (permission.granted) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "Allowed",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AccentSecondary
                                    )
                                }
                            } else {
                                Text(
                                    text = "Allow",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AccentBright,
                                    modifier = Modifier
                                        .clip(BoShape.Control)
                                        .border(1.dp, AccentPrimary, BoShape.Control)
                                        .clickable(role = Role.Button, onClick = permission.onGrant)
                                        .padding(horizontal = 11.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "alerts") {
                BoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Blocked-app alerts",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                "A quiet notification when a blocked app tries to connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        BoToggle(
                            checked = dashboard.blockedTrafficAlertsEnabled,
                            onCheckedChange = dashboardViewModel::setBlockedTrafficAlertsEnabled
                        )
                    }
                }
            }

            item(key = "reward") {
                BoCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock reward", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "How long a locked app stays usable after you pass its challenge. Each unlock in a session gets harder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Spacer(Modifier.height(13.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(1, 5, 10, 15).forEach { minutes ->
                            BoChip(
                                label = "$minutes min",
                                selected = lock.teaserAllowanceMinutes == minutes,
                                onClick = { lockViewModel.setTeaserAllowanceMinutes(minutes) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item(key = "challenge-kinds") {
                BoCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Challenge kinds", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "What a locked app can ask of you. Pick more than one and the app rotates between them — you can't rehearse your way out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Spacer(Modifier.height(13.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        ChallengeKind.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                row.forEach { kind ->
                                    val enabled = kind in lock.enabledChallengeKinds
                                    BoChip(
                                        label = kind.displayName,
                                        selected = enabled,
                                        onClick = { lockViewModel.toggleChallengeKind(kind) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item(key = "preview") {
                BoCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showChallengePreview = true }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Preview an unlock challenge",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                "See what a locked app asks of you",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            item(key = "support") {
                BoCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                    SettingsRow(
                        icon = Icons.Outlined.HelpOutline,
                        label = "How BeOffline works",
                        onClick = { showHowItWorks = true }
                    )
                    BoHairline()
                    SettingsRow(
                        icon = Icons.Outlined.BugReport,
                        label = "Report an issue",
                        onClick = { showIssueDialog = true }
                    )
                }
            }

            if (account.signedIn) {
                item(key = "account") {
                    BoCard(modifier = Modifier.fillMaxWidth()) {
                        BoEyebrow("Account")
                        Text(
                            text = account.userName?.firstName() ?: "Signed in",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text(
                                text = "Sign out",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary,
                                modifier = Modifier
                                    .clip(BoShape.Control)
                                    .clickable(role = Role.Button) { accountViewModel.signOut() }
                                    .padding(vertical = 4.dp)
                            )
                            Text(
                                text = "Delete account",
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusDanger,
                                modifier = Modifier
                                    .clip(BoShape.Control)
                                    .clickable(role = Role.Button) { showDeleteConfirm = true }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showIssueDialog) {
        ReportIssueSheet(
            onDismiss = { showIssueDialog = false },
            onSubmit = { title, details ->
                dashboardViewModel.submitIssueReport(title, details)
                showIssueDialog = false
            }
        )
    }

    if (showHowItWorks) {
        HowItWorksDialog(onDismiss = { showHowItWorks = false })
    }

    if (showDeleteConfirm) {
        AlertDialog(
            containerColor = Brand800,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete your account?", color = TextPrimary) },
            text = {
                Text(
                    "Your pairings, groups, requests and messages you sent are removed from the server. " +
                        "This can't be undone, and your partner is told the pairing ended.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    accountViewModel.deleteAccount()
                }) { Text("Delete", color = StatusDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Keep it", color = TextSecondary)
                }
            }
        )
    }

    if (showChallengePreview) {
        ChallengePreviewOverlay(
            kinds = lock.enabledChallengeKinds,
            rewardMinutes = lock.teaserAllowanceMinutes,
            onDismiss = { showChallengePreview = false }
        )
    }

    // Surface anything either half of the screen wants to say, once.
    (account.message ?: lock.message)?.let { message ->
        AlertDialog(
            containerColor = Brand800,
            onDismissRequest = {
                accountViewModel.dismissMessage()
                lockViewModel.dismissMessage()
            },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    accountViewModel.dismissMessage()
                    lockViewModel.dismissMessage()
                }) { Text("OK", color = AccentBright) }
            }
        )
    }

    // Keep the Google-sign-in availability check honest on this screen too.
    DisposableEffect(Unit) {
        accountViewModel.checkConfiguration(context)
        onDispose { }
    }
}

private data class PermissionRow(
    val title: String,
    val sub: String,
    val granted: Boolean,
    val onGrant: () -> Unit
)

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(13.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun HowItWorksDialog(onDismiss: () -> Unit) {
    AlertDialog(
        containerColor = Brand800,
        onDismissRequest = onDismiss,
        title = { Text("How BeOffline works", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Rules cut selected apps off from the internet using a local VPN. Nothing leaves your phone — the tunnel goes nowhere, which is what makes it a block.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    "App Lock goes further and stops the app opening at all. It needs the accessibility permission to notice which app came to the foreground.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    "You can always turn BeOffline off. If a partner is holding you accountable, they're told when you do, and it takes a cooldown. That's the whole design.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = AccentBright) } }
    )
}

@Composable
private fun ReportIssueSheet(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        containerColor = Brand800,
        onDismissRequest = onDismiss,
        title = { Text("Report an issue", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("What went wrong?", color = TextTertiary) },
                    singleLine = true,
                    colors = boFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    placeholder = { Text("Anything else that helps", color = TextTertiary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    minLines = 3,
                    colors = boFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Sends the report with your rule counts and VPN state — no app names, no traffic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSubmit(title.trim(), details.trim()) }
            ) { Text("Send", color = if (title.isNotBlank()) AccentBright else TextTertiary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
internal fun boFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = Brand900,
    unfocusedContainerColor = Brand900,
    focusedBorderColor = AccentPrimary,
    unfocusedBorderColor = Brand600,
    cursorColor = AccentPrimary
)
