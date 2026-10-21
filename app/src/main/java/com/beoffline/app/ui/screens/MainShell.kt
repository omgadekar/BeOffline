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
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.TextTertiary

/**
 * The two places the app can be.
 *
 * It used to be five — Home, Rules, App Lock, Social, Settings — which asked
 * every newcomer to work out the difference between "Rules" and "App Lock"
 * before they could block anything. Both are now one list of apps, strictness
 * is a per-app choice inside it, and Settings moved to the header where a
 * thing you touch twice a year belongs.
 */
enum class ShellTab(val label: String, val icon: ImageVector, val iconOn: ImageVector) {
    Apps("Your apps", Icons.Outlined.Lock, Icons.Filled.Lock),
    People("People", Icons.Outlined.Groups, Icons.Filled.Groups)
}

@Composable
fun MainShell(
    selectedTab: ShellTab,
    onSelectTab: (ShellTab) -> Unit,
    onRequestVpn: (List<String>) -> Unit,
    onOpenApp: (AppRowUi) -> Unit,
    onAddApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowDisclosure: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Brand900)) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                ShellTab.Apps -> AppsScreen(
                    onOpenApp = onOpenApp,
                    onAddApp = onAddApp,
                    onOpenSettings = onOpenSettings,
                    onRequestVpn = onRequestVpn,
                    onFixLock = onShowDisclosure
                )
                ShellTab.People -> AccountabilityScreen(onOpenChat = onOpenChat)
            }
        }
        BoBottomNav(selected = selectedTab, onSelect = onSelectTab)
    }
}

@Composable
private fun BoBottomNav(selected: ShellTab, onSelect: (ShellTab) -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Brand600)
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
