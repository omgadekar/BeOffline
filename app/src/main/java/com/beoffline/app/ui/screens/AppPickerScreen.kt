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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentGlow
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.BoAppIcon
import com.beoffline.app.ui.theme.BoFadedDivider
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextMuted
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.ui.theme.TextTertiary

/**
 * Choose apps. Every icon is the real launcher icon from the PackageManager —
 * selected apps get an accent ring, unselected ones are dimmed back so the
 * list reads as "these ones" at a glance rather than row by row.
 */
@Composable
fun AppPickerScreen(
    initiallySelected: List<String> = emptyList(),
    onDone: (List<String>) -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel()
) {
    // Seed existing selections (e.g. when editing a rule)
    LaunchedEffect(Unit) {
        viewModel.initializeSelection(initiallySelected)
    }

    val uiState by viewModel.uiState.collectAsState()
    val onNavigateBack = { onDone(viewModel.getSelectedPackages()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand900)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onNavigateBack() }
                    .padding(4.dp)
                    .size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Choose apps",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Done ${uiState.selectedPackages.size}",
                style = MaterialTheme.typography.labelLarge,
                color = AccentBright,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .clickable(role = Role.Button) { onNavigateBack() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::onSearch,
            placeholder = { Text("Search apps", color = TextDisabled) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = TextTertiary)
            },
            singleLine = true,
            shape = BoShape.Control,
            colors = boFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 14.dp)
        )

        BoFadedDivider()

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPrimary)
            }
        } else {
            // This screen is pushed over the shell, so nothing else is holding
            // the bottom inset for it — the last app in the list has to clear
            // the gesture bar or the nav buttons itself.
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp + bottomInset
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(uiState.filteredApps, key = { it.packageName }) { appInfo ->
                    AppPickerRow(
                        appInfo = appInfo,
                        isSelected = appInfo.packageName in uiState.selectedPackages,
                        onToggle = { viewModel.toggleSelection(appInfo.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(appInfo: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AccentPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(12.dp)
    ) {
        Box(modifier = Modifier.size(42.dp)) {
            BoAppIcon(
                drawable = appInfo.icon,
                appName = appInfo.appName,
                size = 42.dp,
                dimmed = !isSelected
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, AccentGlow, BoShape.Icon)
                )
            }
        }

        Spacer(Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appInfo.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) TextPrimary else TextMuted
            )
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = TextDisabled,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(10.dp))

        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = AccentPrimary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Brand600, CircleShape)
            )
        }
    }
}
