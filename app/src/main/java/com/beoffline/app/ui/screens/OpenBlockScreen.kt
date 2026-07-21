package com.beoffline.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusActive
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * App Lock (open-block) feature home: engine status + rule list.
 * A separate feature area from the internet-block dashboard by design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenBlockScreen(
    onBack: () -> Unit,
    onCreateRule: () -> Unit,
    onEditRule: (Int) -> Unit,
    onShowDisclosure: () -> Unit,
    onOpenAccountability: () -> Unit,
    viewModel: OpenBlockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }

    // Ticks once a minute so disable-cooldown countdowns stay current on screen.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
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
                title = { Text("App Lock") },
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRule,
                icon = { Icon(Icons.Default.Add, contentDescription = "New App Lock") },
                text = { Text("New App Lock") },
                containerColor = AccentPrimary,
                contentColor = Color.White
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
            item {
                EngineStatusCard(
                    engineReady = uiState.engineReady,
                    serviceConnected = uiState.serviceConnected,
                    activeCount = uiState.activeRules.size,
                    onEnable = {
                        if (uiState.disclosureAccepted) {
                            viewModel.openAccessibilitySettings()
                        } else {
                            onShowDisclosure()
                        }
                    }
                )
            }

            item {
                TeaserAllowanceCard(
                    selectedMinutes = uiState.teaserAllowanceMinutes,
                    onSelect = viewModel::setTeaserAllowanceMinutes
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Brand800),
                    modifier = Modifier.clickable(onClick = onOpenAccountability)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accountability partner", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                text = "Pair with someone who approves your unlocks",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Your App Locks",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentPrimary)
                    }
                }
            } else if (uiState.rules.isEmpty()) {
                item { EmptyOpenBlockPrompt() }
            }

            items(uiState.rules, key = { it.id }) { rule ->
                OpenBlockRuleCard(
                    rule = rule,
                    appNames = uiState.appNamesByRule[rule.id] ?: emptyList(),
                    now = now,
                    onToggle = { active ->
                        if (active) viewModel.activateRule(rule) else viewModel.deactivateRule(rule)
                    },
                    onCancelDisable = { viewModel.cancelDisable(rule) },
                    onEdit = { onEditRule(rule.id) },
                    onDelete = { viewModel.deleteRule(rule) }
                )
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun EngineStatusCard(
    engineReady: Boolean,
    serviceConnected: Boolean,
    activeCount: Int,
    onEnable: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Brand800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = when {
                            engineReady && serviceConnected -> StatusActive
                            engineReady -> TextSecondary
                            else -> StatusDanger
                        },
                        shape = CircleShape
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        engineReady && serviceConnected ->
                            if (activeCount > 0) "Protecting • $activeCount active lock${if (activeCount != 1) "s" else ""}"
                            else "Ready — no locks active"
                        engineReady -> "Starting…"
                        else -> "Protection is OFF"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                if (!engineReady) {
                    Text(
                        text = "App Lock needs the accessibility service to detect when a blocked app opens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            if (!engineReady) {
                TextButton(onClick = onEnable) {
                    Text("Enable", color = AccentPrimary)
                }
            }
        }
    }
}

@Composable
private fun TeaserAllowanceCard(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Brand800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Unlock challenge reward",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = "How long an app stays usable after you solve the unlock challenge. Each unlock in a session gets harder.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 15).forEach { minutes ->
                    val selected = minutes == selectedMinutes
                    TextButton(
                        onClick = { onSelect(minutes) },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            containerColor = if (selected) AccentPrimary.copy(alpha = 0.25f) else Brand600.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = "${minutes}m",
                            color = if (selected) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyOpenBlockPrompt() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Brand800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No app locks yet",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = "An app lock stops selected apps from being opened at all — not just from going online.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
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
    val typeIcon = when (rule.ruleType) {
        RuleType.PERMANENT -> Icons.Default.Block
        RuleType.SCHEDULED -> Icons.Default.Schedule
        RuleType.TIMER -> Icons.Default.Timer
    }
    val appsLine = when {
        appNames.isEmpty() -> "${rule.blockedPackages.size} apps"
        appNames.size <= 3 -> appNames.joinToString(", ")
        else -> appNames.take(3).joinToString(", ") + " +${appNames.size - 3} more"
    }
    val pendingDisable = rule.disableEffectiveAt?.takeIf { it > now }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Brand800),
        modifier = Modifier.clickable(onClick = onEdit)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    typeIcon,
                    contentDescription = null,
                    tint = if (rule.isActive) AccentPrimary else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        text = appsLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete ${rule.name}",
                        tint = TextDisabled,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Switch(
                    checked = rule.isActive && pendingDisable == null,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentPrimary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Brand600
                    )
                )
            }

            // Disable-cooldown banner: the lock is on its way off but still
            // enforcing, and the partner has been told.
            if (pendingDisable != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusDanger.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Turning off in ${formatCooldownRemaining(pendingDisable, now)} — still active, partner notified",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusDanger,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancelDisable) {
                        Text("Keep on", color = AccentPrimary)
                    }
                }
            }
        }
    }
}
