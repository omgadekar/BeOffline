package com.beoffline.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentGlow
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSurface
import com.beoffline.app.ui.theme.BoEyebrow
import com.beoffline.app.ui.theme.BoPrimaryButton
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextSecondary

/**
 * The permissions a block needs, asked for at the moment of saving it.
 *
 * Two things this deliberately is not. It is not a launch wall: nobody is asked
 * for anything until they have chosen an app and decided how strict to be, so
 * the ask arrives with a reason attached. And it is not a detour: battery,
 * notifications and VPN consent are all system dialogs drawn over the app, so
 * granting them never leaves this screen. Only the accessibility switch does,
 * and only someone who chose "will not open" is ever shown it.
 *
 * Nothing is saved until everything required is granted. A block that cannot
 * enforce should not exist — it reads as working and does nothing.
 */
@Composable
fun PermissionGate(
    state: BlockCreatorUiState,
    onRefresh: () -> Unit,
    onRequestBattery: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestVpn: (List<String>) -> Unit,
    onShowAccessibilityDisclosure: () -> Unit,
    onReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Battery and accessibility both hand control to the system and come back
    // without telling us, so re-read everything whenever we are resumed.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    fun request(need: PermissionNeed) {
        when (need) {
            PermissionNeed.Battery -> onRequestBattery()
            PermissionNeed.Notifications ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onOpenNotificationSettings()
                }
            // An empty list means "consent only" — MainActivity launches the
            // system dialog and starts nothing, because there is nothing to
            // start until the block is saved.
            PermissionNeed.VpnConsent -> onRequestVpn(emptyList())
            // NEVER opens Android's settings directly. Play's accessibility
            // policy requires the prominent disclosure to be shown and
            // acknowledged BEFORE the user is sent to enable the service, so
            // this routes through the disclosure screen, whose own button is
            // what finally opens settings.
            PermissionNeed.Accessibility -> onShowAccessibilityDisclosure()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoEyebrow("Before this can work")
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (state.isLock) {
                "Two switches here, and one in Android's settings."
            } else {
                "Three switches, all of them right here."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))

        state.essential.forEach { need ->
            PermissionRow(
                need = need,
                granted = state.isGranted(need),
                onAllow = { request(need) }
            )
            Spacer(Modifier.height(9.dp))
        }

        if (state.missingRecommended.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            BoEyebrow("Worth allowing too")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "The block works without these. It just works less reliably, " +
                    "and you cannot see that it is running.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            state.recommended.forEach { need ->
                PermissionRow(
                    need = need,
                    granted = state.isGranted(need),
                    onAllow = { request(need) }
                )
                Spacer(Modifier.height(9.dp))
            }
        }

        Spacer(Modifier.height(7.dp))
        BoPrimaryButton(
            text = if (state.isEditing) "Save changes" else "Start blocking",
            enabled = state.canStart,
            onClick = onReady,
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.canStart) {
            Spacer(Modifier.height(9.dp))
            Text(
                text = "Nothing is saved until the one above is allowed — without it this " +
                    "block would look active and do nothing.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PermissionRow(
    need: PermissionNeed,
    granted: Boolean,
    onAllow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BoShape.Card)
            .background(if (granted) Brand700 else Brand800)
            .border(1.dp, if (granted) Brand600 else AccentGlow, BoShape.Card)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = need.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (granted) TextSecondary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(text = need.why, style = MaterialTheme.typography.bodyMedium)
        }

        if (granted) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(AccentSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Allowed",
                    tint = AccentBright,
                    modifier = Modifier.size(15.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .clip(BoShape.Control)
                    .border(1.dp, AccentPrimary, BoShape.Control)
                    .clickable(role = Role.Button, onClick = onAllow)
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Allow",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentPrimary
                )
                // The one that leaves the app says so before you tap it.
                if (!need.inApp) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = TextDisabled,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
