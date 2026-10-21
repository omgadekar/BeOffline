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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.ui.theme.AccentGlow
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.BoAppIcon
import com.beoffline.app.ui.theme.BoEyebrow
import com.beoffline.app.ui.theme.BoPrimaryButton
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.NumeralStyle
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.StatusDangerOutline
import com.beoffline.app.ui.theme.StatusDangerSurface
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import androidx.compose.ui.unit.sp

/**
 * Home: the list of apps you are staying away from.
 *
 * Every row says what the phone is doing in a sentence, so nothing on this
 * screen needs a legend, a status word or a tutorial. Tapping a row opens that
 * app's own settings; the single button underneath adds another.
 */
@Composable
fun AppsScreen(
    onOpenApp: (AppRowUi) -> Unit,
    onAddApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestVpn: (List<String>) -> Unit,
    /** Opens the accessibility disclosure, whose own button opens Android's settings. */
    onFixLock: () -> Unit = {},
    viewModel: AppsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // The accessibility switch lives in Android's settings, so it can be turned
    // off while this screen is in the background.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand900)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 22.dp, end = 12.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BeOffline",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 18.dp)) {
            Text(
                text = uiState.headline,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = uiState.subline,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }

        if (uiState.anyNotEnforcing) {
            NotEnforcingBanner(
                // Accessibility is the more severe of the two, and the one that
                // cannot be fixed from inside the app, so it leads.
                body = if (uiState.lockNotEnforcing) {
                    "BeOffline needs Android to tell it which app is open, and that " +
                        "switch is off. Until you turn it back on, blocked apps will open."
                } else {
                    "BeOffline needs your permission to hold these apps offline, and it " +
                        "does not have it. Until you allow it, they stay online."
                },
                // "Fix this" has to land ON the fix. It used to open the app's
                // own Settings screen, which had no accessibility row at all,
                // so the one failure the app promises to be loud about ended in
                // a dead end.
                onFix = {
                    if (uiState.lockNotEnforcing) onFixLock()
                    else onRequestVpn(uiState.offlinePackages)
                },
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            val blockingNow = uiState.rows.filter {
                it.state.status == AppStatus.BlockedNow || it.state.status == AppStatus.BorrowedTime
            }
            val later = uiState.rows.filter { it.state.status == AppStatus.Later }
            val paused = uiState.rows.filter { it.state.status == AppStatus.Paused }

            items(blockingNow, key = { it.packageName }) { row ->
                AppRow(row = row, onClick = { onOpenApp(row) })
            }

            if (later.isNotEmpty()) {
                item(key = "later-header") {
                    BoEyebrow("Later", modifier = Modifier.padding(top = 12.dp, bottom = 3.dp))
                }
                items(later, key = { it.packageName }) { row ->
                    AppRow(row = row, onClick = { onOpenApp(row) })
                }
            }

            if (paused.isNotEmpty()) {
                item(key = "paused-header") {
                    BoEyebrow("Paused", modifier = Modifier.padding(top = 12.dp, bottom = 3.dp))
                }
                items(paused, key = { it.packageName }) { row ->
                    AppRow(row = row, onClick = { onOpenApp(row) })
                }
            }

            if (uiState.isEmpty) {
                item(key = "empty") { FirstRunInvitation() }
            }
        }

        BoPrimaryButton(
            text = "Block an app",
            onClick = onAddApp,
            icon = Icons.Outlined.Add,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp)
        )
    }
}

/**
 * A lock that is switched on but cannot enforce. This is the one thing the app
 * is allowed to interrupt about, because the alternative is a person trusting
 * a block that is quietly doing nothing.
 */
@Composable
private fun NotEnforcingBanner(
    body: String,
    onFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BoShape.Card)
            .background(StatusDangerSurface)
            .border(1.dp, StatusDangerOutline, BoShape.Card)
            .clickable(role = Role.Button, onClick = onFix)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "These blocks are not holding",
            style = MaterialTheme.typography.titleSmall,
            color = StatusDanger
        )
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = StatusDanger)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Fix this",
            style = MaterialTheme.typography.labelLarge,
            color = StatusDanger
        )
    }
}

/**
 * One app. The icon says which, the sentence says what is happening, and the
 * countdown — when there is one — sits apart so the sentence never ticks.
 */
@Composable
private fun AppRow(row: AppRowUi, onClick: () -> Unit) {
    val status = row.state.status
    val surface = when (status) {
        AppStatus.BorrowedTime -> StatusDangerSurface
        AppStatus.BlockedNow -> Brand800
        else -> Brand900
    }
    val outline = when (status) {
        AppStatus.BorrowedTime -> StatusDangerOutline
        AppStatus.BlockedNow -> AccentGlow
        else -> Brand600
    }
    val sentenceColor = when (status) {
        AppStatus.BorrowedTime -> StatusDanger
        AppStatus.BlockedNow -> AccentPrimary
        else -> TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BoShape.Card)
            .background(surface)
            .border(1.dp, outline, BoShape.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        BoAppIcon(
            drawable = row.icon,
            appName = row.appName,
            size = 38.dp,
            dimmed = status == AppStatus.Paused
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.appName,
                style = MaterialTheme.typography.titleMedium,
                color = if (status == AppStatus.Paused) TextSecondary else TextPrimary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = row.state.sentence,
                style = MaterialTheme.typography.bodyMedium,
                color = sentenceColor
            )
        }

        val trailing = row.state.trailing
        if (trailing != null) {
            Text(
                text = trailing,
                style = NumeralStyle.copy(fontSize = 13.sp),
                color = sentenceColor
            )
        } else if (status == AppStatus.BlockedNow) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary)
            )
        }
    }
}

/**
 * The empty list is the one screen a brand-new person is guaranteed to see, so
 * it does the explaining the rest of the app no longer has to.
 */
@Composable
private fun FirstRunInvitation() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Brand700),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Nothing here yet",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Choose an app you keep reaching for. You decide whether it goes " +
                "quiet or stops opening altogether, and when.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(BoShape.Card)
                .background(Brand800)
                .border(1.dp, Brand600, BoShape.Card)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text("1", style = NumeralStyle.copy(fontSize = 13.sp), color = AccentPrimary)
            Column {
                Text(
                    "Nothing happens until you say so",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "BeOffline only ever touches the apps you pick. The rest of your " +
                        "phone carries on exactly as it does now.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
