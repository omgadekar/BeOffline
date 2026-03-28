package com.beoffline.app.ui.screens

import android.Manifest
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.beoffline.app.background.BackgroundProtectionStatus
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusActive
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.StatusInactive
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary

private fun Drawable.toImageBitmapDash(): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
    val width = intrinsicWidth.takeIf { it > 0 } ?: 48
    val height = intrinsicHeight.takeIf { it > 0 } ?: 48
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}

@Composable
private fun TinyAppIcon(drawable: Drawable?, appName: String, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { drawable?.toImageBitmapDash() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = appName,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Brand700, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onCreateRule: () -> Unit,
    onEditRule: (Int) -> Unit,
    onRequestVpn: (List<String>) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSupportSheet by remember { mutableStateOf(false) }
    var showIssueDialog by remember { mutableStateOf(false) }
    var showIssueSubmittedDialog by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshBackgroundProtection()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBackgroundProtection()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Brand900,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRule,
                icon = { Icon(Icons.Default.Add, contentDescription = "New Rule") },
                text = { Text("New Rule") },
                modifier = Modifier.padding(bottom = 12.dp),
                containerColor = AccentPrimary,
                contentColor = Color.White
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DashboardHeader(
                    isRunning = uiState.isVpnRunning,
                    activeCount = uiState.activeRules.size,
                    onStopAll = viewModel::stopAll
                )
            }

            item {
                StatusCard(isVpnRunning = uiState.isVpnRunning, activeCount = uiState.activeRules.size)
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Rules",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    HelpButton(onClick = { showSupportSheet = true })
                }
            }

            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (uiState.rules.isEmpty()) {
                item { EmptyRulesPrompt() }
            }

            items(uiState.rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    appInfos = uiState.appInfosByRule[rule.id] ?: emptyList(),
                    onToggle = { active ->
                        if (active) {
                            viewModel.activateRule(rule, onRequestVpn)
                        } else {
                            viewModel.deactivateRule(rule)
                        }
                    },
                    onEdit = { onEditRule(rule.id) },
                    onDelete = { viewModel.deleteRule(rule) }
                )
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    when {
        uiState.backgroundProtection?.needsAttention == true -> {
            BackgroundProtectionDialog(
                status = uiState.backgroundProtection,
                onBatteryClick = viewModel::openBatteryOptimizationSettings,
                onNotificationClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.openNotificationSettings()
                    }
                }
            )
        }
        uiState.showWelcomeDialog -> {
            WelcomeDialog(onContinue = viewModel::dismissWelcomeDialog)
        }
    }

    if (showSupportSheet) {
        SupportSheet(
            blockedTrafficAlertsEnabled = uiState.blockedTrafficAlertsEnabled,
            onDismiss = { showSupportSheet = false },
            onBlockedTrafficAlertsChange = viewModel::setBlockedTrafficAlertsEnabled,
            onReportIssue = {
                showSupportSheet = false
                showIssueDialog = true
            }
        )
    }

    if (showIssueDialog) {
        ReportIssueDialog(
            onDismiss = { showIssueDialog = false },
            onSubmit = { title, details ->
                viewModel.submitIssueReport(title, details)
                showIssueDialog = false
                showIssueSubmittedDialog = true
            }
        )
    }

    if (showIssueSubmittedDialog) {
        IssueSubmittedDialog(onDismiss = { showIssueSubmittedDialog = false })
    }
}

@Composable
private fun DashboardHeader(
    isRunning: Boolean,
    activeCount: Int,
    onStopAll: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column {
            Text("BeOffline", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
            Text(
                text = if (isRunning) {
                    "$activeCount rule${if (activeCount != 1) "s" else ""} active"
                } else {
                    "All apps online"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isRunning) StatusActive else TextSecondary
            )
        }
        if (isRunning) {
            TextButton(
                onClick = onStopAll,
                colors = ButtonDefaults.textButtonColors(contentColor = StatusDanger)
            ) {
                Text("Stop All")
            }
        }
    }
}

@Composable
private fun HelpButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brand800)
            .border(1.dp, Brand600, CircleShape)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Text(
                text = "?",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun StatusCard(isVpnRunning: Boolean, activeCount: Int) {
    val gradientBrush = if (isVpnRunning) {
        Brush.horizontalGradient(colors = listOf(Color(0xFF1E2D5A), Color(0xFF0F2040)))
    } else {
        Brush.horizontalGradient(colors = listOf(Brand800, Brand800))
    }

    val dotColor by animateColorAsState(
        targetValue = if (isVpnRunning) StatusActive else StatusInactive,
        animationSpec = tween(600),
        label = "status_dot"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradientBrush)
            .border(
                width = 1.dp,
                color = if (isVpnRunning) AccentPrimary.copy(alpha = 0.4f) else Brand600,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box {
                if (isVpnRunning) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(StatusActive.copy(alpha = pulseAlpha))
                            .align(Alignment.Center)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .align(Alignment.Center)
                )
            }
            Column {
                Text(
                    text = if (isVpnRunning) "Selective Offline Active" else "All Apps Online",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = if (isVpnRunning) {
                        "$activeCount app${if (activeCount != 1) " groups" else " group"} silenced"
                    } else {
                        "Tap a rule or '+' to start"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: BlockRule,
    appInfos: List<AppInfo>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Brand800),
        border = if (rule.isActive) BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = buildRuleSummary(rule),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = rule.isActive,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentPrimary
                    )
                )
            }

            HorizontalDivider(color = Brand600, modifier = Modifier.padding(vertical = 10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (appInfos.isNotEmpty()) {
                    AppIconStrip(appInfos = appInfos, totalCount = rule.blockedPackages.size)
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Row {
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    }
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", style = MaterialTheme.typography.labelLarge, color = StatusDanger)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete '${rule.name}'?") },
            text = { Text("This will permanently remove the rule.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = StatusDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Brand800
        )
    }
}

@Composable
private fun AppIconStrip(appInfos: List<AppInfo>, totalCount: Int) {
    val visible = appInfos.take(3)
    val overflow = totalCount - visible.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        visible.forEach { info ->
            TinyAppIcon(
                drawable = info.icon,
                appName = info.appName,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentSecondary
                )
            }
        }
    }
}

@Composable
private fun EmptyRulesPrompt() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Rounded.PhoneDisabled,
            contentDescription = null,
            tint = Brand600,
            modifier = Modifier.size(56.dp)
        )
        Text("No rules yet", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        Text(
            text = "Tap '+' to silence your first app",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDisabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportSheet(
    blockedTrafficAlertsEnabled: Boolean,
    onDismiss: () -> Unit,
    onBlockedTrafficAlertsChange: (Boolean) -> Unit,
    onReportIssue: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Brand800
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Help & feedback",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "Tell us when something broke or didn’t feel right. Your report will include device and app context to help debug it faster.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Brand700),
                border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Blocked app alerts",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Get a gentle notification when BeOffline catches blocked apps trying to connect.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = blockedTrafficAlertsEnabled,
                        onCheckedChange = onBlockedTrafficAlertsChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentPrimary
                        )
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReportIssue),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Brand700),
                border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Report an issue",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Send a bug report straight from the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReportIssueDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    val canSubmit = title.isNotBlank() && details.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Report an issue",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Tell us what happened. The report will include app version, device info, and current rule state in Crashlytics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Short title") }
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 6,
                    label = { Text("What went wrong?") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(title.trim(), details.trim()) },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Send report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Brand800
    )
}

@Composable
private fun IssueSubmittedDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF17273A), Brand800, Color(0xFF101522))
                    )
                )
                .border(
                    width = 1.dp,
                    color = StatusActive.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(StatusActive.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = StatusActive,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Issue sent",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Thanks for reporting it. We'll review the issue and use the details from your report to investigate it faster.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WelcomeDialog(onContinue: () -> Unit) {
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = {}) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF20264B), Brand800, Color(0xFF11152A))
                    )
                )
                .border(
                    width = 1.dp,
                    color = AccentSecondary.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(30.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = 0.10f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AccentPrimary, AccentSecondary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneDisabled,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AccentPrimary.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Welcome to BeOffline",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentSecondary
                            )
                        }
                        Text(
                            text = "Go Truly Offline",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                    }
                }

                Text(
                    text = "Cut the internet for any app so you can focus without fake availability.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 352.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WelcomeFeatureCard(
                        title = "Take your space back",
                        body = "That one person whose message instantly kills your mood? Their texts can wait. Not forever - just until you're ready."
                    )
                    WelcomeFeatureCard(
                        title = "Silence that actually sticks",
                        body = "Tired of WhatsApp interrupting your focus? Block any app from the internet entirely - not just the notifications. Messages stop arriving, senders see a single gray tick, and you stay genuinely unreachable."
                    )
                    WelcomeFeatureCard(
                        title = "Use apps without reopening the floodgates",
                        body = "Your apps still work, just offline. Open WhatsApp to send a voice note, use Instagram to post - without your inbox flooding the moment you do."
                    )
                    WelcomeFeatureCard(
                        title = "Your rules, your timing",
                        body = "Any app. Any schedule. Your rules. Set a timer, pick a time slot, or block instantly. You decide who reaches you and when."
                    )
                }

                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Let's Go Offline", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WelcomeFeatureCard(title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(AccentSecondary)
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun BackgroundProtectionDialog(
    status: BackgroundProtectionStatus?,
    onBatteryClick: () -> Unit,
    onNotificationClick: () -> Unit
) {
    if (status == null) return

    Dialog(onDismissRequest = {}) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF20264B), Brand800, Color(0xFF11152A))
                    )
                )
                .border(
                    width = 1.dp,
                    color = AccentSecondary.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(30.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = 0.10f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AccentPrimary, AccentSecondary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AccentPrimary.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Required setup",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentSecondary
                            )
                        }
                        Text(
                            text = "Allow Background Access",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                    }
                }

                Text(
                    text = "A couple of permissions help BeOffline stay reliable when your phone tries to sleep apps in the background.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )

                PermissionRequirementCard(
                    title = "Battery optimization",
                    description = "Keeps BeOffline from being put to sleep while your rules are supposed to stay active.",
                    ok = status.batteryOptimizationIgnored
                )
                PermissionRequirementCard(
                    title = "Notifications",
                    description = "Lets BeOffline show important status updates while it's protecting your focus.",
                    ok = status.notificationsEnabled
                )

                if (!status.batteryOptimizationIgnored) {
                    Button(
                        onClick = onBatteryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Text("Allow Background Activity", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }

                if (!status.notificationsEnabled) {
                    Button(
                        onClick = onNotificationClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Text("Allow Notifications", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequirementCard(
    title: String,
    description: String,
    ok: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = if (ok) "Allowed" else "Required",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) StatusActive else StatusDanger
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun BackgroundProtectionCard(
    status: BackgroundProtectionStatus,
    onBatteryClick: () -> Unit,
    onExactAlarmClick: () -> Unit,
    onAppSettingsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Brand800),
        border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Keep BeOffline Running", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                text = "Some phones aggressively sleep background apps. These settings make BeOffline much more reliable.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            ProtectionStatusRow(
                title = "Battery optimization",
                ok = status.batteryOptimizationIgnored,
                okLabel = "Unrestricted",
                actionLabel = "Needs approval"
            )
            ProtectionStatusRow(
                title = "Exact alarms",
                ok = status.exactAlarmAllowed,
                okLabel = "Allowed",
                actionLabel = "Needs approval"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!status.batteryOptimizationIgnored) {
                    Button(
                        onClick = onBatteryClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Text("Allow Battery")
                    }
                }
                if (!status.exactAlarmAllowed) {
                    Button(
                        onClick = onExactAlarmClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand700)
                    ) {
                        Text("Allow Alarms")
                    }
                }
            }

            TextButton(
                onClick = onAppSettingsClick,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Open App Settings", color = AccentSecondary)
            }

            if (status.manufacturerInstructions.isNotEmpty()) {
                Text(
                    text = "${status.manufacturer} tips",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                status.manufacturerInstructions.forEach { step ->
                    Text(
                        text = "• $step",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionStatusRow(
    title: String,
    ok: Boolean,
    okLabel: String,
    actionLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(
            text = if (ok) okLabel else actionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) StatusActive else StatusDanger
        )
    }
}

private fun buildRuleSummary(rule: BlockRule): String {
    val appsLabel = "${rule.blockedPackages.size} app${if (rule.blockedPackages.size != 1) "s" else ""}"

    if (rule.ruleType != RuleType.TIMER) {
        return "$appsLabel • ${rule.ruleType.label()}"
    }

    return buildString {
        append("$appsLabel • ${rule.ruleType.label()}")
        rule.timerDurationMinutes?.let { append(" • ${formatTimerDurationLong(it)} total") }
    }
}

private fun formatTimerDurationLong(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 ->
            "$hours ${if (hours == 1) "hour" else "hours"} $remainingMinutes ${if (remainingMinutes == 1) "min" else "mins"}"
        hours > 0 ->
            "$hours ${if (hours == 1) "hour" else "hours"}"
        else ->
            "$minutes ${if (minutes == 1) "min" else "mins"}"
    }
}

private fun RuleType.label() = when (this) {
    RuleType.PERMANENT -> "Always on"
    RuleType.SCHEDULED -> "Scheduled"
    RuleType.TIMER -> "Timer"
}
