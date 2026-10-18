package com.beoffline.app.ui.challenge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beoffline.app.openblock.ChallengeKind

/**
 * The Settings "preview an unlock challenge" surface.
 *
 * Runs the real challenge component, not a mock — the point of the preview is
 * to know exactly what a locked app will ask for before you rely on it. It
 * grants nothing and records nothing; solving one here just loops you back to
 * a fresh challenge one level up so you can feel the ladder.
 */
@Composable
fun ChallengePreviewOverlay(
    kinds: Set<ChallengeKind>,
    rewardMinutes: Int,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(kinds.firstOrNull() ?: ChallengeKind.Arithmetic) }
    // Start at level 1 so the preview shows a real pre-wait rather than the
    // free first unlock, which would misrepresent what this costs.
    var level by remember { mutableIntStateOf(1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ChallengeSurface(
            appName = "This app",
            level = level,
            kind = kind,
            rewardMinutes = rewardMinutes,
            onSolved = { level += 1 },
            onCancel = onDismiss,
            onSwitchKind = { picked ->
                kind = picked
                level = 1
            }
        )
    }
}
