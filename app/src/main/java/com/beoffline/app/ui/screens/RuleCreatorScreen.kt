package com.beoffline.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.ui.theme.*

/** Converts any Drawable (including AdaptiveIconDrawable) to an ImageBitmap for Compose */
private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
    val width  = intrinsicWidth.takeIf  { it > 0 } ?: 48
    val height = intrinsicHeight.takeIf { it > 0 } ?: 48
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}

@Composable
private fun SmallAppIcon(drawable: Drawable?, appName: String, modifier: Modifier = Modifier) {
    val imageBitmap = remember(drawable) { drawable?.toImageBitmap() }
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

            // Apps to Block — with selected app list + deselect (Feature 4)
            SectionCard(title = "Apps to Block") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    // Vertical list of selected apps with deselect button
                    if (uiState.selectedPackages.isNotEmpty()) {
                        HorizontalDivider(color = Brand600)
                        uiState.selectedAppInfos.forEach { appInfo ->
                            SelectedAppRow(
                                appInfo = appInfo,
                                onDeselect = { viewModel.deselectPackage(appInfo.packageName) }
                            )
                        }
                        // Show any remaining packages that don't have AppInfo resolved yet
                        val resolvedPackages = uiState.selectedAppInfos.map { it.packageName }.toSet()
                        uiState.selectedPackages.filter { it !in resolvedPackages }.forEach { pkg ->
                            SelectedAppRow(
                                appInfo = AppInfo(packageName = pkg, appName = pkg.substringAfterLast(".")),
                                onDeselect = { viewModel.deselectPackage(pkg) }
                            )
                        }
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

            // Scheduled time pickers (shown only for SCHEDULED) — Feature 3: proper dialog-based TimePicker
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

            // Timer duration (shown only for TIMER) — Feature 2: custom hours/minutes option
            if (uiState.ruleType == RuleType.TIMER) {
                SectionCard(title = "Focus Duration") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 90, 120).forEach { mins ->
                            val selected = uiState.timerMinutes == mins && !uiState.isCustomTimer
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

                        // Custom duration option
                        CustomTimerOption(
                            isSelected = uiState.isCustomTimer,
                            customHours = uiState.customTimerHours,
                            customMinutes = uiState.customTimerMinutes,
                            onSelect = { viewModel.onSelectCustomTimer() },
                            onHoursChange = { viewModel.onCustomTimerHoursChange(it) },
                            onMinutesChange = { viewModel.onCustomTimerMinutesChange(it) }
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-components ──────────────────────────────────────────────────────────

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
        IconButton(
            onClick = onDeselect,
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

@Composable
private fun CustomTimerOption(
    isSelected: Boolean,
    customHours: Int,
    customMinutes: Int,
    onSelect: () -> Unit,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    OutlinedButton(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) AccentPrimary.copy(alpha = 0.2f) else Brand700
        ),
        border = if (isSelected) ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp) else ButtonDefaults.outlinedButtonBorder
    ) {
        Text(
            "Custom",
            color = if (isSelected) AccentSecondary else TextSecondary
        )
    }

    if (isSelected) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Brand700)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hours picker
                NumberPickerColumn(
                    label = "Hours",
                    value = customHours,
                    range = 0..23,
                    onValueChange = onHoursChange
                )
                Text(":", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                // Minutes picker
                NumberPickerColumn(
                    label = "Minutes",
                    value = customMinutes,
                    range = 0..59,
                    onValueChange = onMinutesChange
                )
            }
        }
    }
}

@Composable
private fun NumberPickerColumn(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = TextSecondary)
            }
            Text(
                text = "%02d".format(value),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.widthIn(min = 48.dp),
            )
            IconButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = TextSecondary)
            }
        }
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
                if (hour != null) "%02d:%02d".format(hour, minute ?: 0) else "--:--",
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
                            periodSelectorUnselectedContentColor = TextSecondary,
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
