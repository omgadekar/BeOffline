package com.beoffline.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    Scaffold(
        containerColor = Brand900,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRule,
                icon = { Icon(Icons.Default.Add, contentDescription = "New Rule") },
                text = { Text("New Rule") },
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
                DashboardHeader(uiState.isVpnRunning, uiState.activeRules.size, onStopAll = viewModel::stopAll)
            }

            item {
                StatusCard(isVpnRunning = uiState.isVpnRunning, activeCount = uiState.activeRules.size)
            }

            item {
                Text(
                    text = "Your Rules",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
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
}

@Composable
private fun DashboardHeader(isRunning: Boolean, activeCount: Int, onStopAll: () -> Unit) {
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
            TextButton(onClick = onStopAll, colors = ButtonDefaults.textButtonColors(contentColor = StatusDanger)) {
                Text("Stop All")
            }
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
