package com.beoffline.app.openblock

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.beoffline.app.data.local.AllowanceDao
import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.repository.OpenBlockRuleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OpenBlockAccessibilityService — the open-block engine's foreground detector.
 *
 * Bound by the SYSTEM once the user enables it in Accessibility settings
 * (after the in-app prominent disclosure). The system keeps it alive and
 * restarts it after reboot — more kill-resistant than a normal FGS.
 *
 * Behavior: on every foreground window change, if the new package is in the
 * currently-enforced set, immediately send the user HOME (closes the
 * visual-leak window) and raise the block overlay.
 *
 * Deliberately minimal access: only TYPE_WINDOW_STATE_CHANGED events, no
 * window content (canRetrieveWindowContent=false in the XML config). Nothing
 * observed here ever leaves the device.
 */
@AndroidEntryPoint
class OpenBlockAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var repository: OpenBlockRuleRepository

    @Inject
    lateinit var stateManager: OpenBlockStateManager

    @Inject
    lateinit var overlayManager: BlockOverlayManager

    @Inject
    lateinit var allowanceDao: AllowanceDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Snapshot of active rules; window math is re-evaluated per event. */
    @Volatile
    private var activeRules: List<OpenBlockRule> = emptyList()

    /** Snapshot of allowances; grantedUntil is re-checked per event. */
    @Volatile
    private var allowances: List<Allowance> = emptyList()

    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMillis = 0L

    companion object {
        private const val TAG = "OpenBlockA11yService"
        private const val DEBOUNCE_MS = 700L

        /** System surfaces that can never be "blocked apps" — skip fast. */
        private val IGNORED_PACKAGES = setOf("com.android.systemui", "android")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Open-block engine connected")
        stateManager.setServiceConnected(true)
        overlayManager.attach(this)
        serviceScope.launch {
            repository.getActiveRules().collect { rules ->
                activeRules = rules
            }
        }
        serviceScope.launch {
            allowanceDao.getAll().collect { rows ->
                allowances = rows
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED_PACKAGES) return

        val rules = activeRules
        if (rules.isEmpty()) return

        val now = System.currentTimeMillis()
        if (pkg !in OpenBlockController.enforcedPackages(rules, allowances, now)) return

        // Debounce: window-state events can fire in quick bursts for one launch.
        if (pkg == lastBlockedPackage && now - lastBlockedAtMillis < DEBOUNCE_MS) return
        lastBlockedPackage = pkg
        lastBlockedAtMillis = now

        Log.d(TAG, "Blocking foreground open of $pkg")
        performGlobalAction(GLOBAL_ACTION_HOME)
        overlayManager.showBlockScreen(
            packageName = pkg,
            untilText = OpenBlockController.blockedUntilText(rules, pkg, now),
            rule = rules.firstOrNull { pkg in it.blockedPackages }
        )
    }

    override fun onInterrupt() {
        // Nothing to interrupt — enforcement is per-event.
    }

    override fun onDestroy() {
        Log.d(TAG, "Open-block engine disconnected")
        stateManager.setServiceConnected(false)
        overlayManager.detach()
        serviceScope.cancel()
        super.onDestroy()
    }
}
