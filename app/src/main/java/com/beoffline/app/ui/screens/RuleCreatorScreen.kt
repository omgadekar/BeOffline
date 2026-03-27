package com.beoffline.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleCreatorScreen(
    ruleId: Int?,
    onNavigateToAppPicker: () -> Unit,
    onBack: () -> Unit,
    viewModel: RuleCreatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(ruleId) { if (ruleId != null) viewModel.loadRule(ruleId) }

    Scaffold(
        containerColor = Brand900,
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId != null) "Edit Rule" else "New Rule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(Brand900, titleContentColor = TextPrimary)
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.saveRule(); onBack() },
                enabled = uiState.isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Save Rule", style = MaterialTheme.typography.labelLarge)
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
            // Rule Name
            SectionCard(title = "Rule Name") {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    placeholder = { Text("e.g. Morning Focus", color = TextDisabled) },
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

            // Apps to Block
            SectionCard(title = "Apps to Block") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.selectedPackages.isEmpty()) "No apps selected"
                               else "${uiState.selectedPackages.size} app${if (uiState.selectedPackages.size != 1) "s" else ""} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.selectedPackages.isEmpty()) TextDisabled else TextPrimary
                    )
                    TextButton(onClick = onNavigateToAppPicker) {
                        Text("Choose Apps", color = AccentPrimary)
                    }
                }
            }

            // Rule Type
            SectionCard(title = "Block Type") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleType.values().forEach { type ->
                        RuleTypeOption(
                            type = type,
                            isSelected = uiState.ruleType == type,
                            onSelect = { viewModel.onRuleTypeChange(type) }
                        )
                    }
                }
            }

            // Scheduled time pickers (shown only for SCHEDULED)
            if (uiState.ruleType == RuleType.SCHEDULED) {
                SectionCard(title = "Schedule") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerField(
                                label = "Start Time",
                                hour = uiState.startHour,
                                minute = uiState.startMinute,
                                onTimeSet = { h, m -> viewModel.onStartTimeSet(h, m) },
                                modifier = Modifier.weight(1f)
                            )
                            TimePickerField(
                                label = "End Time",
                                hour = uiState.endHour,
                                minute = uiState.endMinute,
                                onTimeSet = { h, m -> viewModel.onEndTimeSet(h, m) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text("Repeat Days", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        DaySelector(activeDays = uiState.activeDays, onToggleDay = viewModel::onToggleDay)
                    }
                }
            }

            // Timer duration (shown only for TIMER)
            if (uiState.ruleType == RuleType.TIMER) {
                SectionCard(title = "Focus Duration") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 90, 120).forEach { mins ->
                            val selected = uiState.timerMinutes == mins
                            OutlinedButton(
                                onClick = { viewModel.onTimerMinutesChange(mins) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) AccentPrimary.copy(alpha = 0.2f) else Brand700
                                ),
                                border = if (selected) ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp) else ButtonDefaults.outlinedButtonBorder
                            ) {
                                val hours = mins / 60
                                val remaining = mins % 60
                                Text(
                                    if (hours > 0) "${hours}h${if (remaining > 0) " ${remaining}m" else ""}"
                                    else "${mins}m",
                                    color = if (selected) AccentSecondary else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-components ──────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Brand800)
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun RuleTypeOption(type: RuleType, isSelected: Boolean, onSelect: () -> Unit) {
    val (icon, desc) = when (type) {
        RuleType.PERMANENT -> Icons.Default.Block to "Block until I turn it off"
        RuleType.SCHEDULED -> Icons.Default.Schedule to "Block on a recurring schedule"
        RuleType.TIMER     -> Icons.Default.Timer to "Block for a set duration"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = AccentPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = if (isSelected) AccentPrimary else TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = TextDisabled)
        }
    }
}

@Composable
private fun TimePickerField(label: String, hour: Int?, minute: Int?, onTimeSet: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = { onTimeSet(hour ?: 9, minute ?: 0) }, modifier = modifier, shape = RoundedCornerShape(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                if (hour != null) "%02d:%02d".format(hour, minute ?: 0) else "--:--",
                style = MaterialTheme.typography.titleMedium,
                color = if (hour != null) TextPrimary else TextDisabled
            )
        }
    }
}

@Composable
private fun DaySelector(activeDays: List<Int>, onToggleDay: (Int) -> Unit) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        dayLabels.forEachIndexed { idx, label ->
            val day = idx + 1
            val selected = day in activeDays
            Button(
                onClick = { onToggleDay(day) },
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) AccentPrimary else Brand700,
                    contentColor = if (selected) com.beoffline.app.ui.theme.TextPrimary else TextSecondary
                )
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
