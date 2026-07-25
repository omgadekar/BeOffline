package com.beoffline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.BoCard
import com.beoffline.app.ui.theme.BoEyebrow
import com.beoffline.app.ui.theme.BoPrimaryButton
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextMuted
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.ui.theme.TextTertiary

/**
 * Creator/editor for App Lock (open-block) rules. Mirrors the internet-block
 * RuleCreatorScreen idiom but is deliberately its own screen: the two features
 * stay independent, and this one allows overnight windows (e.g. 22:00–07:00).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenBlockRuleCreatorScreen(
    ruleId: Int?,
    onNavigateToAppPicker: () -> Unit,
    onBack: () -> Unit,
    viewModel: OpenBlockRuleCreatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(ruleId) {
        if (ruleId != null) viewModel.loadRule(ruleId)
    }

    Scaffold(
        containerColor = Brand900,
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId != null) "Edit App Lock" else "New App Lock") },
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
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                BoPrimaryButton(
                    text = "Save app lock",
                    onClick = { viewModel.saveRule(onSaved = onBack) },
                    enabled = uiState.isValid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LockSectionCard(title = "Lock Name") {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    placeholder = { Text("e.g. Deep Work", color = TextDisabled) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = Brand600,
                        focusedContainerColor = Brand700,
                        unfocusedContainerColor = Brand700,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )
            }

            LockSectionCard(title = "Apps that can't be opened") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.selectedPackages.isEmpty()) {
                                "No apps selected"
                            } else {
                                "${uiState.selectedPackages.size} app${if (uiState.selectedPackages.size != 1) "s" else ""} selected"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.selectedPackages.isEmpty()) TextDisabled else TextPrimary
                        )
                        TextButton(onClick = onNavigateToAppPicker) {
                            Text("Choose Apps", color = AccentPrimary)
                        }
                    }

                    if (uiState.selectedAppInfos.isNotEmpty()) {
                        HorizontalDivider(color = Brand600)
                        uiState.selectedAppInfos.forEach { appInfo ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = appInfo.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.deselectPackage(appInfo.packageName) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove ${appInfo.appName}",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LockSectionCard(title = "Lock Type") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleType.values().forEach { type ->
                        LockTypeOption(
                            type = type,
                            isSelected = uiState.ruleType == type,
                            onSelect = { viewModel.onRuleTypeChange(type) }
                        )
                    }
                }
            }

            if (uiState.ruleType == RuleType.SCHEDULED) {
                LockSectionCard(title = "Schedule") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LockTimePickerField(
                                label = "Start Time",
                                hour = uiState.startHour,
                                minute = uiState.startMinute,
                                onTimeSet = { h, m -> viewModel.onStartTimeSet(h, m) },
                                modifier = Modifier.weight(1f)
                            )
                            LockTimePickerField(
                                label = "End Time",
                                hour = uiState.endHour,
                                minute = uiState.endMinute,
                                onTimeSet = { h, m -> viewModel.onEndTimeSet(h, m) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text("Repeat Days", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        LockDaySelector(activeDays = uiState.activeDays, onToggleDay = viewModel::onToggleDay)
                        uiState.overnightHint?.let { hint ->
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentSecondary
                            )
                        }
                        uiState.scheduleError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusDanger
                            )
                        }
                    }
                }
            }

            if (uiState.ruleType == RuleType.TIMER) {
                LockSectionCard(title = "Lock Duration") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 120).forEach { mins ->
                            val label = if (mins >= 60) "${mins / 60}h" + (if (mins % 60 != 0) " ${mins % 60}m" else "") else "${mins}m"
                            OutlinedButton(
                                onClick = { viewModel.onTimerMinutesChange(mins) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (uiState.timerMinutes == mins) AccentPrimary.copy(alpha = 0.2f) else Brand700
                                )
                            ) {
                                Text(
                                    text = label,
                                    color = if (uiState.timerMinutes == mins) AccentSecondary else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        BoEyebrow(title)
        BoCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun LockTypeOption(type: RuleType, isSelected: Boolean, onSelect: () -> Unit) {
    val (icon, description) = when (type) {
        RuleType.PERMANENT -> Icons.Default.Block to "Locked until you turn it off"
        RuleType.SCHEDULED -> Icons.Default.Schedule to "A window that repeats on chosen days"
        RuleType.TIMER -> Icons.Default.Timer to "One session of a set length"
    }

    val title = when (type) {
        RuleType.PERMANENT -> "Always on"
        RuleType.SCHEDULED -> "Scheduled"
        RuleType.TIMER -> "Timer"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AccentPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(10.dp)
            )
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isSelected) AccentSecondary else TextTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) TextPrimary else TextMuted
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) TextSecondary else TextTertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(if (isSelected) AccentPrimary else Color.Transparent)
                .border(1.dp, if (isSelected) AccentPrimary else Brand600, CircleShape)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockTimePickerField(
    label: String,
    hour: Int?,
    minute: Int?,
    onTimeSet: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = hour ?: 9,
        initialMinute = minute ?: 0,
        is24Hour = false
    )

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = if (hour != null) formatLockTime12Hour(hour, minute ?: 0) else "--:--",
                style = MaterialTheme.typography.titleMedium,
                color = if (hour != null) TextPrimary else TextDisabled
            )
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Brand800)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = Brand700,
                            clockDialSelectedContentColor = TextPrimary,
                            clockDialUnselectedContentColor = TextSecondary,
                            selectorColor = AccentPrimary,
                            containerColor = Brand800,
                            timeSelectorSelectedContainerColor = AccentPrimary.copy(alpha = 0.3f),
                            timeSelectorUnselectedContainerColor = Brand700,
                            timeSelectorSelectedContentColor = TextPrimary,
                            timeSelectorUnselectedContentColor = TextSecondary,
                            periodSelectorSelectedContainerColor = AccentPrimary.copy(alpha = 0.3f),
                            periodSelectorUnselectedContainerColor = Brand700,
                            periodSelectorSelectedContentColor = TextPrimary,
                            periodSelectorUnselectedContentColor = TextSecondary
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            onTimeSet(timePickerState.hour, timePickerState.minute)
                            showDialog = false
                        }) {
                            Text("OK", color = AccentPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockDaySelector(activeDays: List<Int>, onToggleDay: (Int) -> Unit) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        dayLabels.forEachIndexed { index, label ->
            val day = index + 1
            val selected = day in activeDays
            Button(
                onClick = { onToggleDay(day) },
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) AccentPrimary else Brand700,
                    contentColor = if (selected) TextPrimary else TextSecondary
                )
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun formatLockTime12Hour(hour: Int, minute: Int): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val period = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(displayHour, minute, period)
}
