package com.beoffline.app.openblock

import com.beoffline.app.background.BackgroundProtectionManager
import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.scheduler.RuleScheduler
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenBlockController — the single seam for the open-block engine, mirroring
 * [com.beoffline.app.vpn.VpnController].
 *
 * Unlike the VPN there is no service to start/stop: the AccessibilityService
 * is bound by the SYSTEM once the user enables it in Accessibility settings,
 * and the system keeps it alive across reboots. This controller therefore
 * only answers status questions and hosts the pure enforcement policy that
 * the service evaluates per foreground-change event.
 */
@Singleton
class OpenBlockController @Inject constructor(
    private val stateManager: OpenBlockStateManager,
    private val backgroundProtectionManager: BackgroundProtectionManager
) {
    /** Live: is the accessibility service currently bound and watching. */
    val serviceConnected: StateFlow<Boolean> = stateManager.serviceConnected

    /** Settings-level check: has the user enabled the service (survives process death). */
    fun isAccessibilityEnabled(): Boolean =
        backgroundProtectionManager.isAccessibilityServiceEnabled()

    companion object {
        /**
         * The enforcement policy, kept pure for unit testing.
         *
         * A package is enforced when any ACTIVE rule lists it, AND — for
         * SCHEDULED rules — we are inside the rule's window right now,
         * AND no unexpired [Allowance] covers it (a solved teaser, or in M3
         * a partner/group grant, temporarily lifts the block).
         * Allowance expiry needs no alarms: this runs per foreground event,
         * so the first open after grantedUntil is blocked again.
         */
        fun enforcedPackages(
            activeRules: List<OpenBlockRule>,
            allowances: List<Allowance> = emptyList(),
            nowMillis: Long = System.currentTimeMillis()
        ): Set<String> {
            val allowed = allowances
                .filter { it.grantedUntil > nowMillis }
                .map { it.packageName }
                .toSet()
            return activeRules
                .filter { rule -> isEnforcingNow(rule, nowMillis) }
                .flatMap { it.blockedPackages }
                .filter { it !in allowed }
                .toSet()
        }

        /**
         * Is this rule actively blocking right now — i.e. would opening one of
         * its apps be stopped? True when the rule is active, its disable
         * cooldown (if any) has not yet elapsed, and — for SCHEDULED rules — we
         * are inside the window. Allowances are handled separately by the caller.
         */
        fun isEnforcingNow(rule: OpenBlockRule, nowMillis: Long = System.currentTimeMillis()): Boolean {
            if (!rule.isActive) return false
            // Pending-disable cooldown: still enforcing until it elapses, then off.
            if (rule.disableEffectiveAt != null && rule.disableEffectiveAt <= nowMillis) return false
            return rule.ruleType != RuleType.SCHEDULED ||
                RuleScheduler.isWithinScheduledWindow(rule, nowMillis)
        }

        /**
         * Human text for the block screen: when does this block end?
         * Null for PERMANENT rules (blocked until manually turned off).
         */
        fun blockedUntilText(
            activeRules: List<OpenBlockRule>,
            packageName: String,
            nowMillis: Long = System.currentTimeMillis()
        ): String? {
            val rule = activeRules.firstOrNull { packageName in it.blockedPackages } ?: return null
            return when (rule.ruleType) {
                RuleType.SCHEDULED -> {
                    val endHour = rule.endHour ?: return null
                    val endMinute = rule.endMinute ?: return null
                    "Blocked until ${formatClock(endHour, endMinute)}"
                }
                RuleType.TIMER -> {
                    val startedAt = rule.timerStartedAt ?: return null
                    val durationMin = rule.timerDurationMinutes ?: return null
                    val endMillis = startedAt + durationMin * 60_000L
                    if (endMillis <= nowMillis) return null
                    val cal = Calendar.getInstance().apply { timeInMillis = endMillis }
                    "Blocked until ${formatClock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))}"
                }
                RuleType.PERMANENT -> null
            }
        }

        private fun formatClock(hour: Int, minute: Int): String =
            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }
}
