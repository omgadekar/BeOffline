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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.BoCard
import com.beoffline.app.ui.theme.BoEmptyState
import com.beoffline.app.ui.theme.BoFadedDivider
import com.beoffline.app.ui.theme.BoHeaderAction
import com.beoffline.app.ui.theme.BoScreenHeader
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.BoToggle
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * App Lock — locks that stop an app opening at all, rather than just cutting
 * its internet. Its own tab now: it was the app's second engine hiding behind
 * a card on the dashboard.
 */
@Composable
fun OpenBlockScreen(
    onCreateRule: () -> Unit,
    onEditRule: (Int) -> Unit,
    onShowDisclosure: () -> Unit,
    onOpenAccountability: () -> Unit,
    viewModel: OpenBlockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Ticks once a minute so disable-cooldown countdowns stay current on screen.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brand900)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            BoScreenHeader(
                title = "App Lock",
                subtitle = when {
                    !uiState.engineReady -> "Protection is off"
                    uiState.activeRules.isEmpty() -> "Ready — no locks active"
                    else -> "Protecting · ${uiState.activeRules.size} active lock" +
                        if (uiState.activeRules.size == 1) "" else "s"
                }
            ) {
                BoHeaderAction("New lock", onClick = onCreateRule, icon = Icons.Outlined.Add)
            }
            BoFadedDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                item(key = "engine") {
                    EngineStatusCard(
                        engineReady = uiState.engineReady,
                        serviceConnected = uiState.serviceConnected,
                        onEnable = {
                            if (uiState.disclosureAccepted) viewModel.openAccessibilitySettings()
                            else onShowDisclosure()
                        }
                    )
                }

                item(key = "accountability") {
                    BoCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenAccountability) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(13.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Accountability",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    "Let someone you trust approve your unlocks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentPrimary)
                        }
                    }
                } else if (uiState.rules.isEmpty()) {
                    item(key = "empty") {
                        BoEmptyState(
                            icon = Icons.Outlined.Lock,
                            title = "No app locks yet",
                            body = "A lock stops an app opening at all — not just going online."
                        )
                    }
                }

                items(uiState.rules, key = { it.id }) { rule ->
                    OpenBlockRuleCard(
                        rule = rule,
                        appNames = uiState.appNamesByRule[rule.id].orEmpty(),
                        now = now,
                        onToggle = { active ->
                            if (active) viewModel.activateRule(rule) else viewModel.deactivateRule(rule)
                        },
                        onCancelDisable = { viewModel.cancelDisable(rule) },
                        onEdit = { onEditRule(rule.id) },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
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
private fun EngineStatusCard(
    engineReady: Boolean,
    serviceConnected: Boolean,
    onEnable: () -> Unit
) {
    BoCard(modifier = Modifier.fillMaxWidth(), accented = !engineReady) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = if (engineReady) AccentPrimary else StatusDanger,
                modifier = Modifier.padding(top = 1.dp).size(19.dp)
            )
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        engineReady && serviceConnected -> "Accessibility service running"
                        engineReady -> "Accessibility service starting…"
                        else -> "Protection is off"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = if (engineReady) {
                        "Needed to notice the moment a locked app opens. Nothing leaves the device."
                    } else {
                        "App Lock can't block anything until you allow the accessibility service."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (!engineReady) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Enable",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentBright,
                    modifier = Modifier
                        .clip(BoShape.Control)
                        .border(1.dp, AccentPrimary, BoShape.Control)
                        .clickable(role = Role.Button, onClick = onEnable)
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun OpenBlockRuleCard(
    rule: OpenBlockRule,
    appNames: List<String>,
    now: Long,
    onToggle: (Boolean) -> Unit,
    onCancelDisable: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val appsLine = when {
        appNames.isEmpty() -> "${rule.blockedPackages.size} app" +
            if (rule.blockedPackages.size == 1) "" else "s"
        appNames.size <= 3 -> appNames.joinToString(", ")
        else -> appNames.take(3).joinToString(", ") + " +${appNames.size - 3} more"
    }
    val pendingDisable = rule.disableEffectiveAt?.takeIf { it > now }

    BoCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = rule.ruleType.lockIcon(),
                contentDescription = null,
                tint = if (rule.isActive) AccentPrimary else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    text = "$appsLine · ${rule.scheduleLine()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete ${rule.name}",
                tint = TextDisabled,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .clickable(role = Role.Button, onClick = onDelete)
                    .padding(6.dp)
                    .size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            BoToggle(checked = rule.isActive && pendingDisable == null, onCheckedChange = onToggle)
        }

        // Disable-cooldown banner: the lock is on its way off but still
        // enforcing, and the partner has been told.
        if (pendingDisable != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BoShape.Control)
                    .background(AccentPrimary.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Turning off in ${formatCooldownRemaining(pendingDisable, now)} — still enforcing, your partner was told.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentSecondary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Keep on",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentBright,
                    modifier = Modifier
                        .clip(BoShape.Control)
                        .clickable(role = Role.Button, onClick = onCancelDisable)
                        .padding(4.dp)
                )
            }
        }
    }
}

private fun RuleType.lockIcon(): ImageVector = when (this) {
    RuleType.PERMANENT -> Icons.Outlined.Block
    RuleType.SCHEDULED -> Icons.Outlined.Schedule
    RuleType.TIMER -> Icons.Outlined.HourglassEmpty
}

private fun OpenBlockRule.scheduleLine(): String = when (ruleType) {
    RuleType.PERMANENT -> "always on"
    RuleType.TIMER -> "${timerDurationMinutes ?: 0} min timer"
    RuleType.SCHEDULED -> {
        val start = clockLabel(startHour, startMinute)
        val end = clockLabel(endHour, endMinute)
        if (start == null || end == null) "scheduled" else "$start–$end"
    }
}

private fun clockLabel(hour: Int?, minute: Int?): String? {
    if (hour == null || minute == null) return null
    val suffix = if (hour < 12) "AM" else "PM"
    val display = if (hour % 12 == 0) 12 else hour % 12
    return "$display:${minute.toString().padStart(2, '0')} $suffix"
}
