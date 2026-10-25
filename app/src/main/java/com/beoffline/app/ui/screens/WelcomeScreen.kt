package com.beoffline.app.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentGlow
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSurface
import com.beoffline.app.ui.theme.BoAppIcon
import com.beoffline.app.ui.theme.BoPrimaryButton
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.Brand500
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary

private enum class WelcomeStep { Pick, When, Permission }

/**
 * First run.
 *
 * The app used to open on four permission prompts, before it had shown anyone a
 * single reason to care. This asks a question instead, builds the first block
 * out of the answer, and only then asks for the one permission that answer
 * actually needs — with the no-permission option still on the screen.
 */
@Composable
fun WelcomeScreen(
    onFinished: () -> Unit,
    onShowDisclosure: () -> Unit,
    onRequestVpn: (List<String>) -> Unit,
    viewModel: BlockCreatorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    var step by remember { mutableStateOf(WelcomeStep.Pick) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // The ongoing notification is how a person knows a block is running at all,
    // so ask for it once the first block exists rather than on a cold launch.
    // Going through the shared request records the ask, so the gate in the
    // block creator and the row in Settings can both tell a refusal here from
    // never having asked, and offer the settings switch instead of a dialog
    // Android will not draw.
    val notifications = rememberNotificationPermissionRequest(
        openSettings = { onFinished() },
        onResult = { onFinished() }
    )

    fun finish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.onTap()
        } else {
            onFinished()
        }
    }

    /**
     * Saves and leaves. Every exit from this flow goes through here, because
     * every exit has to hand the VPN consent callback to [BlockCreatorViewModel.save].
     * An earlier version passed an empty lambda, so finishing the flow with the
     * "nothing loads" option created a block and never asked for consent — the
     * app then sat there looking armed and blocking nothing.
     */
    fun saveAndFinish() {
        viewModel.save(onNeedVpnPermission = onRequestVpn, onSaved = ::finish)
    }

    LaunchedEffect(Unit) {
        viewModel.load(ruleId = null, startAsLock = true)
        viewModel.loadSuggestions()
    }

    // The accessibility switch is flipped outside the app, so the only way to
    // know it happened is to look again when we come back.
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
                .padding(start = 10.dp, end = 22.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step != WelcomeStep.Pick) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            step = if (step == WelcomeStep.Permission) WelcomeStep.When else WelcomeStep.Pick
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Skip for now",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .clickable(role = Role.Button, onClick = onFinished)
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            when (step) {
                WelcomeStep.Pick -> PickStep(
                    suggestions = suggestions,
                    selected = state.selectedPackages,
                    onPick = { info ->
                        viewModel.onPackagesSelected(listOf(info.packageName))
                        step = WelcomeStep.When
                    }
                )

                WelcomeStep.When -> WhenStep(
                    state = state,
                    onPickWhen = viewModel::setWhenKind,
                    onPickStrictness = viewModel::setStrictness
                )

                WelcomeStep.Permission -> {
                    PermissionStep(onShowDisclosure = onShowDisclosure, isLock = state.isLock)
                    Spacer(Modifier.height(22.dp))
                    PermissionGate(
                        state = state,
                        onRefresh = viewModel::refreshPermissions,
                        onRequestBattery = viewModel::requestBattery,
                        onOpenNotificationSettings = viewModel::openNotificationSettings,
                        onRequestVpn = onRequestVpn,
                        onShowAccessibilityDisclosure = onShowDisclosure,
                        onReady = ::saveAndFinish
                    )
                    // Someone who will not leave the app for the accessibility
                    // switch should not be stuck here: the simpler block needs
                    // nothing outside this screen.
                    if (state.isLock) {
                        Spacer(Modifier.height(18.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(BoShape.Control)
                                .clickable(role = Role.Button) {
                                    viewModel.setStrictness(Strictness.Offline)
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Keep it empty instead",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "It still opens, but nothing loads. Nothing to set up outside here.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        // The gate carries its own primary button, so the bottom bar belongs to
        // the "when" step alone.
        if (step == WelcomeStep.When) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                BoPrimaryButton(
                    text = if (state.canStart) "Start now" else "Next",
                    enabled = state.isValid,
                    onClick = {
                        if (state.canStart) {
                            saveAndFinish()
                        } else {
                            viewModel.refreshPermissions()
                            step = WelcomeStep.Permission
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PickStep(
    suggestions: List<com.beoffline.app.data.model.AppInfo>,
    selected: List<String>,
    onPick: (com.beoffline.app.data.model.AppInfo) -> Unit
) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = "What do you want\nto put down?",
        style = MaterialTheme.typography.displayLarge
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Pick one to start with. You can change it, or add more, at any time.",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary
    )
    Spacer(Modifier.height(26.dp))

    if (suggestions.isEmpty()) {
        Text(
            text = "Looking at what is installed…",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    suggestions.forEach { info ->
        val isSelected = info.packageName in selected
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(BoShape.Card)
                .background(if (isSelected) AccentSurface else Brand800)
                .border(1.dp, if (isSelected) AccentPrimary else Brand600, BoShape.Card)
                .clickable(role = Role.Button) { onPick(info) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BoAppIcon(drawable = info.icon, appName = info.appName, size = 40.dp)
            Text(
                text = info.appName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun WhenStep(
    state: BlockCreatorUiState,
    onPickWhen: (WhenKind) -> Unit,
    onPickStrictness: (Strictness) -> Unit
) {
    val appName = state.selectedAppInfos.firstOrNull()?.appName ?: "this app"

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BoAppIcon(
            drawable = state.selectedAppInfos.firstOrNull()?.icon,
            appName = appName,
            size = 28.dp
        )
        Text(text = appName, style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(20.dp))
    Text(text = "For how long?", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(18.dp))

    IntentCard(
        title = "Just for now",
        detail = "The next hour",
        selected = state.whenKind == WhenKind.Timer,
        onClick = { onPickWhen(WhenKind.Timer) }
    )
    IntentCard(
        title = "On a schedule",
        detail = "The same hours, the days you choose",
        selected = state.whenKind == WhenKind.Hours,
        onClick = { onPickWhen(WhenKind.Hours) }
    )
    IntentCard(
        title = "Until I say stop",
        detail = "No end date",
        selected = state.whenKind == WhenKind.Always,
        onClick = { onPickWhen(WhenKind.Always) }
    )

    Spacer(Modifier.height(26.dp))
    Text(text = "And if you reach for it anyway?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(14.dp))

    IntentCard(
        title = "Let it open, but keep it empty",
        detail = "Nothing to grant, works right away",
        selected = state.strictness == Strictness.Offline,
        onClick = { onPickStrictness(Strictness.Offline) }
    )
    IntentCard(
        title = "Do not let it open at all",
        detail = "Needs one permission - we explain it next",
        selected = state.strictness == Strictness.Locked,
        onClick = { onPickStrictness(Strictness.Locked) }
    )
}

@Composable
private fun PermissionStep(onShowDisclosure: () -> Unit, isLock: Boolean) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = "One permission,\nthen you are set",
        style = MaterialTheme.typography.headlineLarge
    )
    Spacer(Modifier.height(13.dp))
    Text(
        text = if (isLock) {
            "To stop an app opening, Android has to tell BeOffline which app just came " +
                "to the front. That one switch lives in Android's own settings; the rest " +
                "are right here."
        } else {
            "Nothing here leaves the app - each one is a system prompt over this screen."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary
    )
    Spacer(Modifier.height(24.dp))

    FactCard(
        icon = { tint -> Icon(Icons.Outlined.Shield, null, tint = tint, modifier = Modifier.size(17.dp)) },
        title = "What it sees",
        body = "The name of the app that just opened. That is the whole list.",
        accented = true
    )
    FactCard(
        icon = { tint -> Icon(Icons.Outlined.VisibilityOff, null, tint = tint, modifier = Modifier.size(17.dp)) },
        title = "What it never sees",
        body = "What is on your screen. What you type. Your messages. None of this " +
            "leaves your phone - not even to us.",
        accented = false
    )

    Spacer(Modifier.height(14.dp))
    Text(
        text = "Read the full disclosure",
        style = MaterialTheme.typography.labelLarge,
        color = AccentPrimary,
        modifier = Modifier
            .clip(BoShape.Control)
            .clickable(role = Role.Button, onClick = onShowDisclosure)
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun IntentCard(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(BoShape.Card)
            .background(if (selected) AccentSurface else Brand800)
            .border(1.dp, if (selected) AccentPrimary else Brand600, BoShape.Card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .border(
                        if (selected) 6.dp else 2.dp,
                        if (selected) AccentPrimary else Brand500,
                        CircleShape
                    )
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(text = detail, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FactCard(
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
    title: String,
    body: String,
    accented: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(BoShape.Card)
            .background(Brand800)
            .border(1.dp, if (accented) AccentGlow else Brand600, BoShape.Card)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(BoShape.SmallCard)
                .background(if (accented) AccentSurface else Brand700),
            contentAlignment = Alignment.Center
        ) {
            icon(if (accented) AccentBright else TextSecondary)
        }
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
