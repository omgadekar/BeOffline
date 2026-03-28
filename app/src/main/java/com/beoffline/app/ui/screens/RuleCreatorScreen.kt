package com.beoffline.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary

private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
    val width = intrinsicWidth.takeIf { it > 0 } ?: 48
    val height = intrinsicHeight.takeIf { it > 0 } ?: 48
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}

@Composable
private fun SmallAppIcon(drawable: Drawable?, appName: String, modifier: Modifier = Modifier) {
    val imageBitmap = androidx.compose.runtime.remember(drawable) { drawable?.toImageBitmap() }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = appName,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Brand700, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleCreatorScreen(
    ruleId: Int?,
    onNavigateToAppPicker: () -> Unit,
    onBack: () -> Unit,
    viewModel: RuleCreatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(ruleId) {
        if (ruleId != null) viewModel.loadRule(ruleId)
    }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand900,
                    titleContentColor = TextPrimary
                )
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

            SectionCard(title = "Apps to Block") {
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

                    if (uiState.selectedPackages.isNotEmpty()) {
                        HorizontalDivider(color = Brand600)
                        uiState.selectedAppInfos.forEach { appInfo ->
                            SelectedAppRow(
                                appInfo = appInfo,
                                onDeselect = { viewModel.deselectPackage(appInfo.packageName) }
                            )
                        }
                        val resolvedPackages = uiState.selectedAppInfos.map { it.packageName }.toSet()
                        uiState.selectedPackages
                            .filter { it !in resolvedPackages }
                            .forEach { pkg ->
                                SelectedAppRow(
                                    appInfo = AppInfo(packageName = pkg, appName = pkg.substringAfterLast(".")),
                                    onDeselect = { viewModel.deselectPackage(pkg) }
                                )
                            }
                    }
                }
            }

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

            if (uiState.ruleType == RuleType.SCHEDULED) {
                SectionCard(title = "Schedule") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerDialogField(
                                label = "Start Time",
                                hour = uiState.startHour,
                                minute = uiState.startMinute,
                                onTimeSet = { h, m -> viewModel.onStartTimeSet(h, m) },
                                modifier = Modifier.weight(1f)
                            )
                            TimePickerDialogField(
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

            if (uiState.ruleType == RuleType.TIMER) {
                SectionCard(title = "Focus Duration") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.currentCustomTimerMinutes?.let { customMinutes ->
                            DurationOptionButton(
                                label = "Current • ${formatTimerDurationShort(customMinutes)}",
                                selected = uiState.isCustomTimer,
                                onClick = viewModel::onSelectCustomTimer
                            )
                        }

                        listOf(15, 30, 45, 60).forEach { mins ->
                            DurationOptionButton(
                                label = formatTimerDurationShort(mins),
                                selected = uiState.timerMinutes == mins && !uiState.isCustomTimer,
                                onClick = { viewModel.onTimerMinutesChange(mins) }
                            )
                        }

                        CustomTimerOption(
                            isSelected = uiState.isCustomTimer,
                            customHoursInput = uiState.customTimerHoursInput,
                            customMinutesInput = uiState.customTimerMinutesInput,
                            errorMessage = uiState.customTimerError,
                            onSelect = viewModel::onSelectCustomTimer,
                            onHoursChange = viewModel::onCustomTimerHoursChange,
                            onMinutesChange = viewModel::onCustomTimerMinutesChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedAppRow(appInfo: AppInfo, onDeselect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        SmallAppIcon(
            drawable = appInfo.icon,
            appName = appInfo.appName,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(appInfo.appName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        IconButton(onClick = onDeselect, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove ${appInfo.appName}",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DurationOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AccentPrimary.copy(alpha = 0.2f) else Brand700
        ),
        border = if (selected) {
            ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
        } else {
            ButtonDefaults.outlinedButtonBorder
        }
    ) {
        Text(
            text = label,
            color = if (selected) AccentSecondary else TextSecondary
        )
    }
}

@Composable
private fun CustomTimerOption(
    isSelected: Boolean,
    customHoursInput: String,
    customMinutesInput: String,
    errorMessage: String?,
    onSelect: () -> Unit,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit
) {
    OutlinedButton(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) AccentPrimary.copy(alpha = 0.2f) else Brand700
        ),
        border = if (isSelected) {
            ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
        } else {
            ButtonDefaults.outlinedButtonBorder
        }
    ) {
        Text("Custom", color = if (isSelected) AccentSecondary else TextSecondary)
    }

    if (isSelected) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Brand700)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    NumberPickerColumn(
                        modifier = Modifier.weight(1f),
                        label = "Hours",
                        value = customHoursInput,
                        rangeLabel = "0-24",
                        onValueChange = onHoursChange
                    )
                    NumberPickerColumn(
                        modifier = Modifier.weight(1f),
                        label = "Minutes",
                        value = customMinutesInput,
                        rangeLabel = "0-59",
                        onValueChange = onMinutesChange
                    )
                }
            }

            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusDanger
                )
            }
        }
    }
}

@Composable
private fun NumberPickerColumn(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    rangeLabel: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(rangeLabel, style = MaterialTheme.typography.labelSmall, color = TextDisabled)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isDigit).take(4)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = TextPrimary,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = {
                Text(
                    text = "00",
                    color = TextDisabled,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = Brand600,
                focusedContainerColor = Brand800,
                unfocusedContainerColor = Brand800,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogField(
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
                text = if (hour != null) "%02d:%02d".format(hour, minute ?: 0) else "--:--",
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
    val (icon, description) = when (type) {
        RuleType.PERMANENT -> Icons.Default.Block to "Block until I turn it off"
        RuleType.SCHEDULED -> Icons.Default.Schedule to "Block on a recurring schedule"
        RuleType.TIMER -> Icons.Default.Timer to "Block for a set duration"
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
        Icon(
            icon,
            contentDescription = null,
            tint = if (isSelected) AccentPrimary else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(description, style = MaterialTheme.typography.bodyMedium, color = TextDisabled)
        }
    }
}

@Composable
private fun DaySelector(activeDays: List<Int>, onToggleDay: (Int) -> Unit) {
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

private fun formatTimerDurationShort(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
