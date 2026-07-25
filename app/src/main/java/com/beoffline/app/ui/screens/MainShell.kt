package com.beoffline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.TextTertiary

/**
 * The five places the app can be. App Lock and Social used to be buried inside
 * the dashboard; giving them a tab each is the one structural change the
 * redesign makes.
 */
enum class ShellTab(val label: String, val icon: ImageVector, val iconOn: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Rules("Rules", Icons.Outlined.Tune, Icons.Filled.Tune),
    AppLock("App Lock", Icons.Outlined.Lock, Icons.Filled.Lock),
    Social("Social", Icons.Outlined.Groups, Icons.Filled.Groups),
    Settings("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

@Composable
fun MainShell(
    selectedTab: ShellTab,
    onSelectTab: (ShellTab) -> Unit,
    onRequestVpn: (List<String>) -> Unit,
    onCreateRule: () -> Unit,
    onEditRule: (Int) -> Unit,
    onCreateLock: () -> Unit,
    onEditLock: (Int) -> Unit,
    onShowDisclosure: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Brand900)) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                ShellTab.Home -> HomeScreen(
                    onRequestVpn = onRequestVpn,
                    onSeeAllRules = { onSelectTab(ShellTab.Rules) },
                    onOpenAppLock = { onSelectTab(ShellTab.AppLock) },
                    onCreateRule = onCreateRule
                )
                ShellTab.Rules -> RulesScreen(
                    onCreateRule = onCreateRule,
                    onEditRule = onEditRule,
                    onRequestVpn = onRequestVpn
                )
                ShellTab.AppLock -> OpenBlockScreen(
                    onCreateRule = onCreateLock,
                    onEditRule = onEditLock,
                    onShowDisclosure = onShowDisclosure,
                    onOpenAccountability = { onSelectTab(ShellTab.Social) }
                )
                ShellTab.Social -> AccountabilityScreen(onOpenChat = onOpenChat)
                ShellTab.Settings -> SettingsScreen(onRequestVpn = onRequestVpn)
            }
        }
        BoBottomNav(selected = selectedTab, onSelect = onSelectTab)
    }

    // First launch: the permissions that decide whether a rule actually holds.
    // Hosted here rather than on Home so it appears whichever tab you land on
    // (a notification deep-link can open the app straight into Social).
    StartupPermissionsDialog(onRequestVpn = onRequestVpn)
}

@Composable
private fun BoBottomNav(selected: ShellTab, onSelect: (ShellTab) -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.07f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brand900)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            ShellTab.entries.forEach { tab ->
                val on = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // The selected tab is marked by a short accent rule above it —
                    // no pill, no fill, nothing that reads as a button.
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (on) AccentPrimary else Color.Transparent)
                    )
                    Icon(
                        imageVector = if (on) tab.iconOn else tab.icon,
                        contentDescription = tab.label,
                        tint = if (on) AccentBright else TextTertiary,
                        modifier = Modifier.size(21.dp)
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) AccentBright else TextTertiary
                    )
                }
            }
        }
    }
}
