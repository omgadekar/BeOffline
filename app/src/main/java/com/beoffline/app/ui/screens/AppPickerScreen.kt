package com.beoffline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = Brand900,
        topBar = {
            TopAppBar(
                title = { Text("Select Apps to Block") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand900,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    if (uiState.selectedPackages.isNotEmpty()) {
                        TextButton(onClick = onNavigateBack) {
                            Text("Done (${uiState.selectedPackages.size})", color = AccentPrimary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange  = viewModel::onSearch,
                placeholder = { Text("Search apps...", color = TextDisabled) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = Brand600,
                    focusedContainerColor = Brand800,
                    unfocusedContainerColor = Brand800,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentPrimary)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(uiState.filteredApps, key = { it.packageName }) { appInfo ->
                        AppPickerRow(
                            appInfo = appInfo,
                            isSelected = appInfo.packageName in uiState.selectedPackages,
                            onToggle  = { viewModel.toggleSelection(appInfo.packageName) }
                        )
                    }
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .background(if (isSelected) AccentPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        // App icon placeholder (gray circle)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brand700),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appInfo.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(appInfo.appName, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(appInfo.packageName, style = MaterialTheme.typography.labelSmall, color = TextDisabled, maxLines = 1)
        }

        if (isSelected) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = AccentPrimary)
        } else {
            Checkbox(
                checked = false,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(uncheckedColor = Brand600)
            )
        }
    }
}
