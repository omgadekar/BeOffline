package com.beoffline.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.BoEyebrow
import com.beoffline.app.ui.theme.BoPrimaryButton
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.ui.theme.TextTertiary

/**
 * First-launch setup. Three permissions decide whether a rule actually holds
 * once the phone is in a pocket, so they're asked for up front rather than
 * discovered later when a block silently fails.
 *
 * Accessibility is deliberately NOT here. App Lock is the only feature that
 * needs it, most people never turn App Lock on, and Play policy requires a
 * prominent disclosure immediately before that grant — so it stays behind the
 * disclosure screen in the App Lock tab, where the ask has context.
 */
@Composable
fun StartupPermissionsDialog(
    onRequestVpn: (List<String>) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    if (!uiState.showWelcomeDialog) return

    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshBackgroundProtection() }

    // Each grant happens in a system screen, so the only moment we can notice
    // it is coming back — re-check on every resume while this is up.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBackgroundProtection()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val status = uiState.backgroundProtection
    val batteryOk = status?.batteryOptimizationIgnored ?: false
    val notificationsOk = status?.notificationsEnabled ?: false
    val vpnOk = uiState.isVpnPermissionGranted
    val hideNoticesOk = status?.notificationAccessEnabled ?: false
    val requiredComplete = batteryOk && notificationsOk && vpnOk

    // One action at a time, in the order they matter — a wall of four buttons
    // is how people tap the wrong one and think setup is done.
    val nextAction: Pair<String, () -> Unit>? = when {
        !batteryOk -> "Allow background activity" to viewModel::openBatteryOptimizationSettings
        !notificationsOk -> "Allow notifications" to {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.openNotificationSettings()
            }
        }
        !vpnOk -> "Allow VPN access" to { onRequestVpn(emptyList()) }
        !hideNoticesOk -> "Hide blocked-app notices" to viewModel::openNotificationAccessSettings
        else -> null
    }

    Dialog(
        // Not dismissible by tapping away: the required three aren't optional,
        // and a rule that can't enforce is worse than no rule.
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        BoxWithConstraints {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brand900)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            ) {
                // The same accent bloom the home screen opens with.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(180.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AccentPrimary.copy(alpha = 0.16f), Color.Transparent),
                                radius = 240f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp)
                ) {
                    BoEyebrow("Setup")
                    Text(
                        text = "Three things, then you're set",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = "Android sleeps background apps aggressively. Without these, a rule can look active and quietly stop enforcing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(Modifier.height(18.dp))

                    PermissionLine(
                        title = "Background activity",
                        description = "Keeps the rule engine awake",
                        granted = batteryOk
                    )
                    PermissionLine(
                        title = "Notifications",
                        description = "Ongoing status while rules enforce",
                        granted = notificationsOk
                    )
                    PermissionLine(
                        title = "VPN access",
                        description = "The local tunnel that blocks traffic",
                        granted = vpnOk
                    )
                    PermissionLine(
                        title = "Hide blocked-app notices",
                        description = "Dismisses their alerts too",
                        granted = hideNoticesOk,
                        optional = true
                    )

                    Spacer(Modifier.height(18.dp))

                    nextAction?.let { (label, onClick) ->
                        BoPrimaryButton(
                            text = label,
                            onClick = onClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    Text(
                        text = if (requiredComplete) {
                            "You're set. Hiding blocked-app notices is optional — you can turn it on later in Settings."
                        } else {
                            "Allow the three required permissions to continue."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )

                    Spacer(Modifier.height(12.dp))

                    BoPrimaryButton(
                        text = "Continue",
                        onClick = viewModel::dismissWelcomeDialog,
                        enabled = requiredComplete,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionLine(
    title: String,
    description: String,
    granted: Boolean,
    optional: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        when {
            granted -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text("Allowed", style = MaterialTheme.typography.labelMedium, color = AccentSecondary)
            }
            optional -> Text(
                text = "Optional",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .background(Brand800)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
            else -> Text(
                text = "Needed",
                style = MaterialTheme.typography.labelMedium,
                color = AccentBright,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .border(1.dp, Brand600, BoShape.Control)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}
