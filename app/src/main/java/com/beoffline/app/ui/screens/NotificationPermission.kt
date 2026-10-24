package com.beoffline.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Asking for POST_NOTIFICATIONS, including the part Android does not tell you
 * about.
 *
 * After two refusals the system stops showing the dialog, and
 * `launcher.launch()` then returns "denied" without putting anything on
 * screen. A button wired straight to it quietly becomes a button that does
 * nothing — which was true of the Allow control on the block creator's
 * permission gate, on the People tab, and in Settings, the one screen whose
 * whole job is to be the way back. The only honest next step at that point is
 * the switch in Android's own settings.
 *
 * Telling "never asked" from "asked twice and refused" is not something the
 * platform exposes: `shouldShowRequestPermissionRationale` is false for both.
 * So the first ask is recorded, and from then on that flag plus the rationale
 * flag say which case this is. It is persisted rather than remembered, because
 * refusing a permission dialog is exactly the moment someone closes the app.
 */
class NotificationPermissionRequest internal constructor(
    /** For a control the user tapped: the system dialog, or Settings when that is gone. */
    val onTap: () -> Unit,
    /**
     * For an automatic ask, where there was no tap to justify throwing someone
     * out into Settings. Shows the dialog if it can still be shown, and
     * otherwise does nothing — leaving whatever card prompted it on screen.
     */
    val askIfPossible: () -> Unit
)

@Composable
fun rememberNotificationPermissionRequest(
    openSettings: () -> Unit,
    onResult: (granted: Boolean) -> Unit = {}
): NotificationPermissionRequest {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    return remember(context, openSettings, onResult) {
        fun showDialog() {
            context.markNotificationAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        NotificationPermissionRequest(
            onTap = {
                when {
                    // Below Tiramisu there is no runtime permission to ask for;
                    // notifications can still be off, and only Settings fixes that.
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> openSettings()
                    context.hasNotificationPermission() -> onResult(true)
                    context.canShowNotificationDialog() -> showDialog()
                    else -> openSettings()
                }
            },
            askIfPossible = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !context.hasNotificationPermission() &&
                    context.canShowNotificationDialog()
                ) {
                    showDialog()
                }
            }
        )
    }
}

private const val PERMISSION_PREFS = "permission_asks"
private const val KEY_ASKED_NOTIFICATIONS = "asked_post_notifications"

private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

/**
 * True while Android will still draw the dialog: either it has never been
 * asked, or it has been refused once and is willing to be asked again.
 */
private fun Context.canShowNotificationDialog(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val prefs = getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return true
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS
    )
}

private fun Context.markNotificationAsked() {
    getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_ASKED_NOTIFICATIONS, true)
        .apply()
}

/** Compose hands out a wrapped context; the rationale check needs the Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
