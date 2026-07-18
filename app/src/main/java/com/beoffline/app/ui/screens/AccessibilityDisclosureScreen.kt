package com.beoffline.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusActive
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary

/**
 * PROMINENT DISCLOSURE for the accessibility service (Play policy, Jan 2026
 * accessibility enforcement): shown in normal use, BEFORE navigating the user
 * to Accessibility settings, with affirmative consent. Do not weaken this
 * copy without re-checking the Play Console accessibility declaration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityDisclosureScreen(
    onBack: () -> Unit,
    viewModel: OpenBlockViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = Brand900,
        topBar = {
            TopAppBar(
                title = { Text("Before you enable") },
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.acceptDisclosureAndOpenSettings()
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Text("I understand — take me to settings")
                    }
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Not now", color = TextSecondary)
                    }
                }
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
            Text(
                text = "App Lock uses Android's accessibility service",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Text(
                text = "To stop a blocked app from being used, BeOffline needs to know the moment it comes to the foreground. Android's accessibility service is the mechanism that makes this possible.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            DisclosureCard(
                icon = Icons.Default.Visibility,
                title = "What it observes",
                body = "Only which app comes to the foreground — nothing else. This powers exactly one feature: showing your block screen when a locked app is opened during a restricted period."
            )
            DisclosureCard(
                icon = Icons.Default.Block,
                title = "What it never does",
                body = "It does not read your screen content, does not see what you type, does not collect passwords, and never sends any of this information off your device."
            )
            DisclosureCard(
                icon = Icons.Default.CheckCircle,
                title = "You stay in control",
                body = "You can turn the service off at any time in Accessibility settings, and App Lock rules only apply to apps you chose yourself."
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DisclosureCard(icon: ImageVector, title: String, body: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Brand800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = StatusActive,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}
