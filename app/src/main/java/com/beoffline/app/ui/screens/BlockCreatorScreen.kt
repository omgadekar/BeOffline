@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.beoffline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSurface
import com.beoffline.app.ui.theme.BoAppIcon
import com.beoffline.app.ui.theme.BoEyebrow
import com.beoffline.app.ui.theme.BoPrimaryButton
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.Brand500
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary

private val TIMER_PRESETS = listOf(15, 30, 60, 90, 120)
private val DAY_LABELS = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")

/**
 * Three questions, one screen: which apps, how strict, when.
 *
 * Nothing here asks for a name — the apps are the name — and nothing names an
 * engine. The accessibility switch is only mentioned if the answer to question
 * two actually needs it, and then in the sentence where it becomes relevant.
 */
@Composable
fun BlockCreatorScreen(
    ruleId: Int?,
    startAsLock: Boolean,
    focusPackage: String? = null,
    onNavigateToAppPicker: () -> Unit,
    onShowDisclosure: () -> Unit,
    onRequestVpn: (List<String>) -> Unit,
    onBack: () -> Unit,
    viewModel: BlockCreatorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(ruleId, startAsLock) { viewModel.load(ruleId, startAsLock, focusPackage) }

    var picking by remember { mutableStateOf<TimeField?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var showGate by remember { mutableStateOf(false) }


    if (showGate) {
        AlertDialog(
            onDismissRequest = { showGate = false },
            containerColor = Brand800,
            title = {
                Text(
                    text = if (state.isLock) "Two things, then it's on" else "Three things, then it's on",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                PermissionGate(
                    state = state,
                    onRefresh = viewModel::refreshPermissions,
                    onRequestBattery = viewModel::requestBattery,
                    onOpenNotificationSettings = viewModel::openNotificationSettings,
                    onRequestVpn = onRequestVpn,
                    onShowAccessibilityDisclosure = onShowDisclosure,
                    onReady = {
                        showGate = false
                        viewModel.save(onNeedVpnPermission = onRequestVpn, onSaved = onBack)
                    }
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGate = false }) {
                    Text("Not now", color = TextSecondary)
                }
            }
        )
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
                .padding(start = 10.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = if (state.isEditing) "Edit this block" else "Block an app",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            if (state.isEditing) {
                Text(
                    text = "Remove",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(BoShape.Control)
                        .clickable(role = Role.Button) { confirmingDelete = true }
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            Question(number = 1, title = "Which app?")
            AppChips(
                state = state,
                onAdd = onNavigateToAppPicker,
                onRemove = viewModel::deselectPackage
            )

            Spacer(Modifier.height(26.dp))

            Question(number = 2, title = "How strict?")
            StrictnessCard(
                title = "It opens, but nothing loads",
                body = "Feeds stay empty. Good for an app you still need for messages.",
                selected = state.strictness == Strictness.Offline,
                onClick = { viewModel.setStrictness(Strictness.Offline) }
            )
            Spacer(Modifier.height(8.dp))
            StrictnessCard(
                title = "It will not open at all",
                body = "Tapping it shows your block screen instead.",
                selected = state.strictness == Strictness.Locked,
                onClick = { viewModel.setStrictness(Strictness.Locked) }
            )

            if (state.missing.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(BoShape.Card)
                        .background(Brand700)
                        .clickable(role = Role.Button, onClick = onShowDisclosure)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (state.isLock) {
                            "This needs one switch in Android's settings. We'll ask when you save."
                        } else {
                            "This needs your permission to hold traffic. We'll ask when you save."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "What it sees",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentPrimary
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            Question(number = 3, title = "When?")
            Segmented(
                selected = state.whenKind,
                onSelect = viewModel::setWhenKind
            )
            Spacer(Modifier.height(12.dp))

            when (state.whenKind) {
                WhenKind.Always -> Note(
                    "On until you turn it off. The quietest option — nothing to " +
                        "forget, nothing to re-arm."
                )

                WhenKind.Hours -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeBox(
                            label = "From",
                            value = AppStateCopy.clock(state.startHour, state.startMinute),
                            onClick = { picking = TimeField.Start },
                            modifier = Modifier.weight(1f)
                        )
                        TimeBox(
                            label = "Until",
                            value = AppStateCopy.clock(state.endHour, state.endMinute),
                            onClick = { picking = TimeField.End },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    BoEyebrow("On these days")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        DAY_LABELS.forEach { (day, label) ->
                            DayToggle(
                                label = label,
                                selected = day in state.activeDays,
                                onClick = { viewModel.toggleDay(day) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (state.endHour * 60 + state.endMinute <
                        state.startHour * 60 + state.startMinute
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Note("This one runs overnight, into the next morning.")
                    }
                }

                WhenKind.Timer -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TIMER_PRESETS.forEach { minutes ->
                            Pill(
                                label = spokenTimer(minutes),
                                selected = state.timerMinutes == minutes,
                                onClick = { viewModel.setTimerMinutes(minutes) }
                            )
                        }
                    }
                }
            }

            if (state.movingFromOtherBlocks.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Note(
                    (if (state.movingFromOtherBlocks.size == 1) {
                        "${state.movingFromOtherBlocks.first()} is already in another block."
                    } else {
                        "${state.movingFromOtherBlocks.joinToString(", ")} are already in other blocks."
                    }) + " Saving moves it here, so each app has one set of rules and one row on your list."
                )
            }

            val error = state.scheduleError
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium, color = StatusDanger)
            }

            Spacer(Modifier.height(28.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brand800)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(10.dp))
            BoPrimaryButton(
                text = if (state.isEditing) "Save changes" else "Start blocking",
                enabled = state.isValid,
                onClick = {
                    // Nothing is written until the block can actually enforce.
                    if (state.canStart) {
                        viewModel.save(onNeedVpnPermission = onRequestVpn, onSaved = onBack)
                    } else {
                        viewModel.refreshPermissions()
                        showGate = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val field = picking
    if (field != null) {
        val initialHour = if (field == TimeField.Start) state.startHour else state.endHour
        val initialMinute = if (field == TimeField.Start) state.startMinute else state.endMinute
        val pickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { picking = null },
            containerColor = Brand800,
            title = {
                Text(
                    if (field == TimeField.Start) "Start blocking at" else "Stop blocking at",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    if (field == TimeField.Start) {
                        viewModel.setStart(pickerState.hour, pickerState.minute)
                    } else {
                        viewModel.setEnd(pickerState.hour, pickerState.minute)
                    }
                    picking = null
                }) { Text("Done", color = AccentPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = Brand800,
            title = { Text("Stop blocking these apps?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "They go back to normal straight away, and this block is gone. " +
                        "You can set it up again whenever you like.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Remove it", color = StatusDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("Keep it", color = TextSecondary)
                }
            }
        )
    }
}

private enum class TimeField { Start, End }

@Composable
private fun Question(number: Int, title: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = AccentPrimary
        )
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
    }
    Spacer(Modifier.height(11.dp))
}

@Composable
private fun AppChips(
    state: BlockCreatorUiState,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.selectedAppInfos.forEach { info ->
            Row(
                modifier = Modifier
                    .clip(BoShape.Pill)
                    .background(AccentSurface)
                    .border(1.dp, com.beoffline.app.ui.theme.AccentGlow, BoShape.Pill)
                    .padding(start = 6.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                BoAppIcon(drawable = info.icon, appName = info.appName, size = 22.dp)
                Text(
                    text = info.appName,
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentBright
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onRemove(info.packageName) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Remove ${info.appName}",
                        tint = AccentBright,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // One app per block, so this is "choose" or "change", never "add".
        Row(
            modifier = Modifier
                .clip(BoShape.Pill)
                .border(1.dp, Brand500, BoShape.Pill)
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.selectedAppInfos.isEmpty()) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = if (state.selectedAppInfos.isEmpty()) "Choose an app" else "Change",
                style = MaterialTheme.typography.labelLarge,
                color = AccentPrimary
            )
        }
    }
}

@Composable
private fun StrictnessCard(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BoShape.Card)
            .background(if (selected) AccentSurface else Brand800)
            .border(
                1.dp,
                if (selected) AccentPrimary else Brand600,
                BoShape.Card
            )
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
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PermissionNote(onExplain: () -> Unit, onAllow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BoShape.Card)
            .background(Brand700)
            .padding(16.dp)
    ) {
        Text(
            text = "One switch still to turn on",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "To stop an app opening, Android has to tell BeOffline which app " +
                "just came to the front. Until that is on, this block will not hold.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Turn it on",
                style = MaterialTheme.typography.labelLarge,
                color = AccentPrimary,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .border(1.dp, AccentPrimary, BoShape.Control)
                    .clickable(role = Role.Button, onClick = onAllow)
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            )
            Text(
                text = "What it sees",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .clickable(role = Role.Button, onClick = onExplain)
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun Segmented(selected: WhenKind, onSelect: (WhenKind) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BoShape.Card)
            .background(Brand700)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WhenKind.entries.forEach { kind ->
            val on = kind == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(BoShape.SmallCard)
                    .background(if (on) Brand800 else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(kind) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) TextPrimary else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TimeBox(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(BoShape.Card)
            .background(Brand800)
            .border(1.dp, Brand600, BoShape.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        BoEyebrow(label)
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DayToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(BoShape.SmallCard)
            .background(if (selected) AccentSurface else Brand800)
            .border(1.dp, if (selected) AccentPrimary else Brand600, BoShape.SmallCard)
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AccentBright else TextSecondary
        )
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(BoShape.Pill)
            .background(if (selected) AccentSurface else Brand800)
            .border(1.dp, if (selected) AccentPrimary else Brand600, BoShape.Pill)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AccentBright else TextSecondary
        )
    }
}

@Composable
private fun Note(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BoShape.Card)
            .background(Brand800)
            .border(1.dp, Brand600, BoShape.Card)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
    }
}
