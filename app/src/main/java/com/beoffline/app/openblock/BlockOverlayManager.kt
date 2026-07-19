package com.beoffline.app.openblock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.BeOfflineTheme
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.StatusDanger
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BlockOverlayManager — the full-screen "app blocked" surface, now hosting the
 * M2 solo-teaser unlock flow.
 *
 * Window strategy unchanged from M1: TYPE_ACCESSIBILITY_OVERLAY via the
 * service (no extra permission) → TYPE_APPLICATION_OVERLAY if granted →
 * send-home only. The teaser deliberately uses an in-overlay numpad so the
 * window can stay FLAG_NOT_FOCUSABLE (no IME/focus juggling).
 */
@Singleton
class BlockOverlayManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val blockRuleRepository: BlockRuleRepository,
    private val teaserController: TeaserController,
    private val accountabilityRepository: AccountabilityRepository
) {
    companion object {
        private const val TAG = "BlockOverlayManager"
        private const val AUTO_DISMISS_MS = 8_000L
    }

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var serviceRef: WeakReference<AccessibilityService>? = null
    private var overlayView: ComposeView? = null
    private var overlayWindowManager: WindowManager? = null
    private var lifecycleHost: OverlayLifecycleHost? = null
    private var dismissJob: Job? = null

    fun attach(service: AccessibilityService) {
        serviceRef = WeakReference(service)
    }

    fun detach() {
        mainScope.launch { hideNow() }
        serviceRef = null
    }

    fun showBlockScreen(packageName: String, untilText: String?, rule: OpenBlockRule? = null) {
        mainScope.launch {
            val appName = resolveAppName(packageName)
            val hasPartner = try {
                accountabilityRepository.hasPartner()
            } catch (_: Exception) {
                false
            }
            try {
                showOverlay(appName, packageName, untilText, rule, hasPartner)
            } catch (e: Exception) {
                // Never let overlay failure break enforcement — send-home already happened.
                Log.e(TAG, "Failed to show block overlay", e)
                return@launch
            }
            armAutoDismiss()
        }
    }

    private fun armAutoDismiss() {
        dismissJob?.cancel()
        dismissJob = mainScope.launch {
            delay(AUTO_DISMISS_MS)
            hideNow()
        }
    }

    /** Called when the user enters the teaser — the overlay must stay up. */
    private fun cancelAutoDismiss() {
        dismissJob?.cancel()
        dismissJob = null
    }

    private suspend fun resolveAppName(packageName: String): String =
        try {
            blockRuleRepository.getAppInfoForPackages(listOf(packageName))
                .firstOrNull()?.appName ?: packageName.substringAfterLast('.')
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }

    private fun showOverlay(
        appName: String,
        packageName: String,
        untilText: String?,
        rule: OpenBlockRule?,
        hasPartner: Boolean
    ) {
        val service = serviceRef?.get()
        val (windowManager, windowType) = when {
            service != null -> {
                val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            }
            Settings.canDrawOverlays(appContext) -> {
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
            else -> {
                Log.w(TAG, "No overlay path available; send-home only")
                return
            }
        }

        val content: @Composable () -> Unit = {
            BeOfflineTheme {
                BlockOverlayRoot(
                    appName = appName,
                    packageName = packageName,
                    untilText = untilText,
                    rule = rule,
                    hasPartner = hasPartner,
                    teaserController = teaserController,
                    askPartner = { accountabilityRepository.enqueueUnlockRequest(packageName, appName) },
                    onTeaserStarted = ::cancelAutoDismiss,
                    onDismiss = { mainScope.launch { hideNow() } }
                )
            }
        }

        // Already showing → just update content.
        overlayView?.let { existing ->
            existing.setContent(content)
            return
        }

        val host = OverlayLifecycleHost().also { it.onCreate() }
        val view = ComposeView(service ?: appContext).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setContent(content)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(view, params)
        overlayView = view
        overlayWindowManager = windowManager
        lifecycleHost = host
        host.onResume()
    }

    private fun hideNow() {
        dismissJob?.cancel()
        dismissJob = null
        val view = overlayView ?: return
        try {
            overlayWindowManager?.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "Failed removing overlay view", e)
        }
        lifecycleHost?.onDestroy()
        lifecycleHost = null
        overlayView = null
        overlayWindowManager = null
    }
}

private class OverlayLifecycleHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

// ── Overlay content ──────────────────────────────────────────────────────────

private sealed interface OverlayStage {
    data object Blocked : OverlayStage
    data class Countdown(val difficulty: TeaserEngine.Difficulty) : OverlayStage
    data class Solving(val difficulty: TeaserEngine.Difficulty) : OverlayStage
    data class Granted(val minutes: Int) : OverlayStage
    data object AskSent : OverlayStage
}

@Composable
private fun BlockOverlayRoot(
    appName: String,
    packageName: String,
    untilText: String?,
    rule: OpenBlockRule?,
    hasPartner: Boolean,
    teaserController: TeaserController,
    askPartner: suspend () -> Unit,
    onTeaserStarted: () -> Unit,
    onDismiss: () -> Unit
) {
    var stage by remember { mutableStateOf<OverlayStage>(OverlayStage.Blocked) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand900.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center
    ) {
        when (val s = stage) {
            is OverlayStage.Blocked -> BlockedStage(
                appName = appName,
                untilText = untilText,
                showUnlock = rule != null,
                showAskPartner = hasPartner,
                onDismiss = onDismiss,
                onUnlock = {
                    if (rule != null) {
                        onTeaserStarted()
                        scope.launch {
                            val level = teaserController.currentLevel(rule)
                            val difficulty = TeaserEngine.difficultyFor(level)
                            stage = if (difficulty.preWaitSeconds > 0) {
                                OverlayStage.Countdown(difficulty)
                            } else {
                                OverlayStage.Solving(difficulty)
                            }
                        }
                    }
                },
                onAskPartner = {
                    onTeaserStarted() // pause auto-dismiss while we queue
                    scope.launch {
                        try {
                            askPartner()
                        } catch (_: Exception) {
                            // Outbox insert is local; a failure here is exceptional.
                        }
                        stage = OverlayStage.AskSent
                    }
                }
            )

            is OverlayStage.AskSent -> {
                LaunchedEffect(Unit) {
                    delay(4_000)
                    onDismiss()
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Request sent to your partner.",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        // Fail-closed, stated plainly: nothing unlocks until approval.
                        text = "You'll get a notification when they respond. $appName stays blocked until then — if you're offline, the request goes out once you reconnect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is OverlayStage.Countdown -> CountdownStage(
                difficulty = s.difficulty,
                onFinished = { stage = OverlayStage.Solving(s.difficulty) },
                onCancel = onDismiss
            )

            is OverlayStage.Solving -> SolvingStage(
                difficulty = s.difficulty,
                onSolved = {
                    scope.launch {
                        val minutes = teaserController.recordSuccess(rule!!, packageName)
                        stage = OverlayStage.Granted(minutes)
                    }
                },
                onCancel = onDismiss
            )

            is OverlayStage.Granted -> {
                LaunchedEffect(Unit) {
                    delay(2_500)
                    onDismiss()
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    // Deliberately flat: no celebration, no color, no praise.
                    Text(
                        text = "Unlocked for ${s.minutes} minute${if (s.minutes != 1) "s" else ""}.",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Open $appName again to use it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedStage(
    appName: String,
    untilText: String?,
    showUnlock: Boolean,
    showAskPartner: Boolean,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    onAskPartner: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = AccentPrimary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "$appName is blocked",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = untilText ?: "Stay focused — this app is off-limits right now.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentPrimary,
                contentColor = Color.White
            )
        ) {
            Text("OK")
        }
        if (showAskPartner) {
            TextButton(onClick = onAskPartner) {
                Text("Ask my partner to unlock", color = TextSecondary)
            }
        }
        if (showUnlock) {
            TextButton(onClick = onUnlock) {
                Text("Solve a challenge to unlock", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun CountdownStage(
    difficulty: TeaserEngine.Difficulty,
    onFinished: () -> Unit,
    onCancel: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(difficulty.preWaitSeconds) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        onFinished()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "Wait ${secondsLeft}s",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = "This wait cannot be skipped. Unlock #${difficulty.level + 1} this session.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onCancel) {
            Text("Never mind", color = TextDisabled)
        }
    }
}

@Composable
private fun SolvingStage(
    difficulty: TeaserEngine.Difficulty,
    onSolved: () -> Unit,
    onCancel: () -> Unit
) {
    var problems by remember { mutableStateOf(TeaserEngine.generateProblems(difficulty)) }
    var index by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var wrongFlash by remember { mutableStateOf(false) }

    fun submit() {
        val answer = input.toIntOrNull()
        if (answer != null && answer == problems[index].answer) {
            wrongFlash = false
            input = ""
            if (index + 1 >= problems.size) {
                onSolved()
            } else {
                index++
            }
        } else {
            // Any mistake restarts the whole set with fresh problems.
            problems = TeaserEngine.generateProblems(difficulty)
            index = 0
            input = ""
            wrongFlash = true
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Text(
            text = "Problem ${index + 1} of ${problems.size}",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        if (wrongFlash) {
            Text(
                text = "Wrong. Starting over.",
                style = MaterialTheme.typography.bodyMedium,
                color = StatusDanger
            )
        }
        Text(
            text = problems[index].text,
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )
        Text(
            text = if (input.isEmpty()) " " else input,
            style = MaterialTheme.typography.headlineSmall,
            color = AccentPrimary
        )

        OverlayNumpad(
            onDigit = { d -> if (input.length < 6) { input += d; wrongFlash = false } },
            onBackspace = { input = input.dropLast(1) },
            onSubmit = { if (input.isNotEmpty()) submit() }
        )

        TextButton(onClick = onCancel) {
            Text("Never mind", color = TextDisabled)
        }
    }
}

@Composable
private fun OverlayNumpad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "OK")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "OK" -> onSubmit()
                                else -> onDigit(key[0])
                            }
                        },
                        modifier = Modifier
                            .width(84.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (key == "OK") AccentPrimary.copy(alpha = 0.25f) else Brand700
                        )
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
