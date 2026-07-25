package com.beoffline.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.ui.theme.BoAppIcon
import com.beoffline.app.ui.theme.BoCard
import com.beoffline.app.ui.theme.BoEmptyState
import com.beoffline.app.ui.theme.BoFadedDivider
import com.beoffline.app.ui.theme.BoHairline
import com.beoffline.app.ui.theme.BoHeaderAction
import com.beoffline.app.ui.theme.BoScreenHeader
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.BoToggle
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.ui.theme.TextTertiary

/**
 * Rules — the full list, one card each. Home shows the first three; this is
 * where a rule is edited, deleted, or read in full.
 */
@Composable
fun RulesScreen(
    onCreateRule: () -> Unit,
    onEditRule: (Int) -> Unit,
    onRequestVpn: (List<String>) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand900)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        BoScreenHeader(
            title = "Rules",
            subtitle = if (uiState.rules.isEmpty()) {
                "Nothing set up yet"
            } else {
                "${uiState.activeRules.size} of ${uiState.rules.size} enforcing"
            }
        ) {
            BoHeaderAction("New rule", onClick = onCreateRule, icon = Icons.Outlined.Add)
        }
        BoFadedDivider()

        if (uiState.rules.isEmpty()) {
            BoEmptyState(
                icon = Icons.Outlined.WifiOff,
                title = "No rules yet",
                body = "Make one and an app stops reaching the internet."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                items(uiState.rules, key = { it.id }) { rule ->
                    RuleDetailCard(
                        rule = rule,
                        appInfos = uiState.appInfosByRule[rule.id].orEmpty(),
                        onToggle = { active ->
                            if (active) viewModel.activateRule(rule, onRequestVpn)
                            else viewModel.deactivateRule(rule)
                        },
                        onEdit = { onEditRule(rule.id) },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleDetailCard(
    rule: BlockRule,
    appInfos: List<AppInfo>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    BoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = rule.ruleType.homeIcon(),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.padding(top = 1.dp).size(18.dp)
            )
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    text = rule.homeMeta(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            BoToggle(checked = rule.isActive, onCheckedChange = onToggle)
        }

        Spacer(Modifier.height(13.dp))
        BoHairline()
        Spacer(Modifier.height(13.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Real launcher icons, straight from the PackageManager.
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                appInfos.take(5).forEach { app ->
                    BoAppIcon(
                        drawable = app.icon,
                        appName = app.appName,
                        size = 26.dp,
                        shape = BoShape.Control
                    )
                }
                val overflow = appInfos.size - 5
                if (overflow > 0) {
                    Text(
                        text = "+$overflow",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
            Text(
                text = "Edit",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Delete",
                style = MaterialTheme.typography.labelLarge,
                color = TextDisabled,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .clickable(role = Role.Button, onClick = onDelete)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}
