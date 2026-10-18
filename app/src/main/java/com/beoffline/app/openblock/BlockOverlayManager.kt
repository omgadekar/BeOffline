package com.beoffline.app.openblock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
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
import com.beoffline.app.data.model.GroupCache
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.ui.challenge.ChallengeSurface
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.BeOfflineTheme
import com.beoffline.app.ui.theme.BoPrimaryButton
import com.beoffline.app.ui.theme.BoSecondaryButton
import com.beoffline.app.ui.theme.BrandVoid
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.util.firstName
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
 * BlockOverlayManager — the full-screen "app blocked" surface, hosting the solo
 * unlock challenge.
 *
 * Window strategy unchanged: TYPE_ACCESSIBILITY_OVERLAY via the service (no
 * extra permission) → TYPE_APPLICATION_OVERLAY if granted → send-home only.
 *
 * The window is FLAG_NOT_FOCUSABLE by default so the numpad, tile and hold
 * challenges never fight the IME for focus. The Retype challenge is the one
 * kind that genuinely needs a keyboard, so the overlay drops that flag for the
 * duration of that stage and puts it straight back afterwards.
 */
@Singleton
class BlockOverlayManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val blockRuleRepository: BlockRuleRepository,
    private val challengeController: ChallengeController,
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
    private var overlayParams: WindowManager.LayoutParams? = null
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
            val partnerName = try {
                accountabilityRepository.firstPartnerName()
            } catch (_: Exception) {
                null
            }
            // v1: no in-overlay chooser — the oldest-joined group is the target.
            val group = try {
                accountabilityRepository.firstGroup()
            } catch (_: Exception) {
                null
            }
            try {
                showOverlay(appName, packageName, untilText, rule, partnerName, group)
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

    /** Called when the user enters the challenge — the overlay must stay up. */
    private fun cancelAutoDismiss() {
        dismissJob?.cancel()
        dismissJob = null
    }

    /**
     * Hands focus to the overlay so the soft keyboard can attach, or takes it
     * back. Only the Retype challenge asks for this.
     */
    private fun setFocusable(focusable: Boolean) {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val alreadyFocusable = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
        if (alreadyFocusable == focusable) return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            overlayWindowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "Could not change overlay focusability", e)
        }
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
        partnerName: String?,
        group: GroupCache?
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
                    partnerName = partnerName,
                    groupName = group?.name,
                    challengeController = challengeController,
                    askPartner = { accountabilityRepository.enqueueUnlockRequest(packageName, appName) },
                    askGroup = {
                        group?.let {
                            accountabilityRepository.enqueueUnlockRequest(
                                packageName, appName, groupId = it.groupId
                            )
                        }
                    },
                    onChallengeStarted = ::cancelAutoDismiss,
                    onNeedsKeyboard = ::setFocusable,
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
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        windowManager.addView(view, params)
        overlayView = view
        overlayWindowManager = windowManager
        overlayParams = params
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
        overlayParams = null
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
    data class Challenge(val level: Int, val kind: ChallengeKind) : OverlayStage
    /** [target] names who was asked — a partner's first name or the group's name. */
    data class AskSent(val target: String) : OverlayStage
}

@Composable
private fun BlockOverlayRoot(
    appName: String,
    packageName: String,
    untilText: String?,
    rule: OpenBlockRule?,
    partnerName: String?,
    groupName: String?,
    challengeController: ChallengeController,
    askPartner: suspend () -> Unit,
    askGroup: suspend () -> Unit,
    onChallengeStarted: () -> Unit,
    onNeedsKeyboard: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var stage by remember { mutableStateOf<OverlayStage>(OverlayStage.Blocked) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(BrandVoid),
        contentAlignment = Alignment.Center
    ) {
        when (val current = stage) {
            is OverlayStage.Blocked -> BlockedStage(
                appName = appName,
                untilText = untilText,
                showUnlock = rule != null,
                partnerName = partnerName,
                askGroupName = groupName,
                onDismiss = onDismiss,
                onUnlock = {
                    if (rule != null) {
                        onChallengeStarted()
                        scope.launch {
                            val level = challengeController.currentLevel(rule)
                            stage = OverlayStage.Challenge(
                                level = level,
                                kind = challengeController.kindFor(rule, level)
                            )
                        }
                    }
                },
                onAskPartner = {
                    onChallengeStarted() // pause auto-dismiss while we queue
                    scope.launch {
                        try {
                            askPartner()
                        } catch (_: Exception) {
                            // Outbox insert is local; a failure here is exceptional.
                        }
                        stage = OverlayStage.AskSent(partnerName ?: "your partner")
                    }
                },
                onAskGroup = {
                    onChallengeStarted()
                    scope.launch {
                        try {
                            askGroup()
                        } catch (_: Exception) {
                        }
                        stage = OverlayStage.AskSent(groupName ?: "your group")
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
                        text = "Request sent to ${current.target}.",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        // Fail-closed, stated plainly: nothing unlocks until approval.
                        text = "You'll get a notification when someone responds. $appName stays blocked until then — if you're offline, the request goes out once you reconnect.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is OverlayStage.Challenge -> ChallengeSurface(
                appName = appName,
                level = current.level,
                kind = current.kind,
                rewardMinutes = challengeController.allowanceMinutes(),
                onNeedsKeyboard = onNeedsKeyboard,
                onSolved = { solvedKind ->
                    scope.launch {
                        rule?.let {
                            challengeController.recordSuccess(
                                rule = it,
                                packageName = packageName,
                                appLabel = appName,
                                kind = solvedKind
                            )
                        }
                        delay(3_000)
                        onDismiss()
                    }
                },
                onCancel = onDismiss
            )
        }
    }
}

@Composable
private fun BlockedStage(
    appName: String,
    untilText: String?,
    showUnlock: Boolean,
    partnerName: String?,
    askGroupName: String?,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    onAskPartner: () -> Unit,
    onAskGroup: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = AccentPrimary,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = "$appName is locked",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = untilText ?: "You chose to put this away for now.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp).padding(bottom = 14.dp)
        )

        if (showUnlock) {
            BoPrimaryButton(
                text = "Unlock with a challenge",
                onClick = onUnlock,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (partnerName != null) {
            BoSecondaryButton(
                // First name only — this is a person, addressed the way you'd
                // say their name out loud.
                text = "Ask ${partnerName.firstName()}",
                onClick = onAskPartner,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (askGroupName != null) {
            BoSecondaryButton(
                text = "Ask $askGroupName",
                onClick = onAskGroup,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = "Close",
            style = MaterialTheme.typography.bodyLarge,
            color = TextDisabled,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(role = Role.Button, onClick = onDismiss)
                .padding(8.dp)
        )
    }
}
