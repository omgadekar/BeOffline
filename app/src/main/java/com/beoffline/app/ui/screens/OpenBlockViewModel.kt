package com.beoffline.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.background.BackgroundProtectionManager
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.data.repository.OpenBlockRuleRepository
import com.beoffline.app.openblock.OpenBlockController
import com.beoffline.app.openblock.OpenBlockPrefs
import com.beoffline.app.scheduler.OpenBlockScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpenBlockUiState(
    val rules: List<OpenBlockRule> = emptyList(),
    val appNamesByRule: Map<Int, List<String>> = emptyMap(),
    /** User enabled the service in Accessibility settings (the engine works). */
    val accessibilityEnabled: Boolean = false,
    /** Live binding state — false right after revocation even if settings lag. */
    val serviceConnected: Boolean = false,
    val disclosureAccepted: Boolean = false,
    /** Minutes a solved teaser unlocks an app for (user preference). */
    val teaserAllowanceMinutes: Int = OpenBlockPrefs.DEFAULT_TEASER_ALLOWANCE_MINUTES,
    val isLoading: Boolean = true,
    /** Transient user-facing note (e.g. why a lock can't be deleted right now). */
    val message: String? = null
) {
    val engineReady: Boolean get() = accessibilityEnabled
    val activeRules: List<OpenBlockRule> get() = rules.filter { it.isActive }
}

/**
 * ViewModel for the App Lock (open-block) feature area.
 * Deliberately separate from DashboardViewModel — the two features must not conflict.
 */
@HiltViewModel
class OpenBlockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OpenBlockRuleRepository,
    private val blockRuleRepository: BlockRuleRepository,
    private val controller: OpenBlockController,
    private val backgroundProtectionManager: BackgroundProtectionManager,
    private val accountabilityRepository: AccountabilityRepository
) : ViewModel() {

    private val prefs = context.getSharedPreferences(OpenBlockPrefs.FILE, Context.MODE_PRIVATE)

    companion object {
        // Turning off an actively-enforcing lock is as slow-and-visible as
        // dropping an accountability partner (mirrors the server removal
        // cooldown). Lower this to test the flow without waiting a day.
        private const val DISABLE_COOLDOWN_MILLIS = 24L * 60 * 60 * 1000
    }

    private val _uiState = MutableStateFlow(
        OpenBlockUiState(
            disclosureAccepted = prefs.getBoolean(OpenBlockPrefs.KEY_DISCLOSURE_ACCEPTED, false),
            teaserAllowanceMinutes = prefs.getInt(
                OpenBlockPrefs.KEY_TEASER_ALLOWANCE_MINUTES,
                OpenBlockPrefs.DEFAULT_TEASER_ALLOWANCE_MINUTES
            )
        )
    )
    val uiState: StateFlow<OpenBlockUiState> = _uiState.asStateFlow()

    init {
        observeRules()
        observeServiceState()
        refreshStatus()
    }

    private fun observeRules() {
        viewModelScope.launch {
            repository.getAllRules().collect { rules ->
                // Belt-and-braces cleanup: if a disable cooldown elapsed while the
                // finalize worker was delayed (or the app was killed), settle it
                // now. The resulting DB write re-emits with the field cleared, so
                // this doesn't loop.
                val now = System.currentTimeMillis()
                rules.filter { it.disableEffectiveAt != null && it.disableEffectiveAt <= now }
                    .forEach { finalizeDisableNow(it.id) }

                _uiState.update { it.copy(rules = rules, isLoading = false) }
                resolveAppNames(rules)
            }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            controller.serviceConnected.collect { connected ->
                _uiState.update { it.copy(serviceConnected = connected) }
            }
        }
    }

    /** Call on screen resume — settings-level checks can change behind our back. */
    fun refreshStatus() {
        _uiState.update {
            it.copy(
                accessibilityEnabled = backgroundProtectionManager.isAccessibilityServiceEnabled(),
                disclosureAccepted = prefs.getBoolean(OpenBlockPrefs.KEY_DISCLOSURE_ACCEPTED, false)
            )
        }
    }

    fun setTeaserAllowanceMinutes(minutes: Int) {
        prefs.edit().putInt(OpenBlockPrefs.KEY_TEASER_ALLOWANCE_MINUTES, minutes).apply()
        _uiState.update { it.copy(teaserAllowanceMinutes = minutes) }
    }

    private suspend fun resolveAppNames(rules: List<OpenBlockRule>) {
        val names = rules.associate { rule ->
            rule.id to try {
                blockRuleRepository.getAppInfoForPackages(rule.blockedPackages).map { it.appName }
            } catch (_: Exception) {
                rule.blockedPackages.map { it.substringAfterLast('.') }
            }
        }
        _uiState.update { it.copy(appNamesByRule = names) }
    }

    fun activateRule(rule: OpenBlockRule) {
        viewModelScope.launch {
            // A lock can't actually block anything without the accessibility
            // service, so don't let it be switched "on" until that's granted —
            // otherwise it looks protected but isn't.
            if (!backgroundProtectionManager.isAccessibilityServiceEnabled()) {
                _uiState.update {
                    it.copy(message = "Turn on App Lock protection first — it needs the accessibility permission to actually block apps.")
                }
                return@launch
            }
            // Re-enabling a lock that was counting down to off → call off the
            // cooldown first, then re-arm normally.
            if (rule.disableEffectiveAt != null) cancelDisableInternal(rule.id)
            when (rule.ruleType) {
                RuleType.PERMANENT -> repository.setRuleActive(rule.id, true)
                RuleType.TIMER -> {
                    val startedAt = System.currentTimeMillis()
                    repository.setTimerStartedAt(rule.id, startedAt)
                    repository.setRuleActive(rule.id, true)
                    OpenBlockScheduler.scheduleTimerStop(context, rule.copy(timerStartedAt = startedAt))
                }
                RuleType.SCHEDULED ->
                    // Arms the window alarms; if we're inside the window right
                    // now, the start alarm fires ~1s later and activates it.
                    OpenBlockScheduler.scheduleRule(context, rule.copy(isActive = false))
            }
        }
    }

    fun deactivateRule(rule: OpenBlockRule) {
        viewModelScope.launch {
            // Already counting down → ignore repeat toggles (Cancel undoes it).
            if (rule.disableEffectiveAt != null) return@launch

            val now = System.currentTimeMillis()
            // "Enforcing" for the cooldown means the lock is genuinely blocking:
            // active, in-window, AND the accessibility engine is actually on.
            // Without the permission there was no protection to bypass, so turning
            // it off is instant and silent — no cooldown, no partner alert.
            val enforcing = OpenBlockController.isEnforcingNow(rule, now) &&
                backgroundProtectionManager.isAccessibilityServiceEnabled()
            val watched = accountabilityRepository.hasPartner() || accountabilityRepository.hasGroup()

            if (enforcing && watched) {
                // Accountability path: keep enforcing through a cooldown, and tell
                // the partner/group the moment the user starts backing out. Never
                // enforce longer than the rule itself would have — a TIMER just
                // runs out its own clock; open-ended locks get the full cooldown.
                val cooldownEnd = now + DISABLE_COOLDOWN_MILLIS
                val effectiveAt = timerEndMillis(rule)?.let { minOf(cooldownEnd, it) } ?: cooldownEnd
                if (effectiveAt <= now) {
                    immediateDeactivate(rule)
                    return@launch
                }
                repository.setDisableEffectiveAt(rule.id, effectiveAt)
                OpenBlockScheduler.scheduleDisableFinalize(context, rule.id, effectiveAt - now)
                accountabilityRepository.reportTamper(
                    type = "RESTRICTION_DISABLED",
                    packageName = null,
                    dedupeKey = "disable-${rule.id}-$effectiveAt"
                )
                _uiState.update {
                    it.copy(message = "Your partner was notified. This lock stays on until the cooldown ends.")
                }
            } else {
                immediateDeactivate(rule)
            }
        }
    }

    /** Undo a pending disable — the lock stays fully on. */
    fun cancelDisable(rule: OpenBlockRule) {
        viewModelScope.launch { cancelDisableInternal(rule.id) }
    }

    fun deleteRule(rule: OpenBlockRule) {
        viewModelScope.launch {
            val enforcing = OpenBlockController.isEnforcingNow(rule) &&
                backgroundProtectionManager.isAccessibilityServiceEnabled()
            val watched = accountabilityRepository.hasPartner() || accountabilityRepository.hasGroup()
            if (enforcing && watched) {
                // Can't silently delete protection out from under an approver.
                _uiState.update {
                    it.copy(message = "This lock is protecting you right now. Turn it off first — your partner will be notified.")
                }
                return@launch
            }
            OpenBlockScheduler.cancelRule(context, rule.id)
            OpenBlockScheduler.cancelDisableFinalize(context, rule.id)
            repository.deleteRule(rule)
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun immediateDeactivate(rule: OpenBlockRule) {
        repository.setRuleActive(rule.id, false)
        when (rule.ruleType) {
            RuleType.TIMER -> {
                repository.setTimerStartedAt(rule.id, null)
                OpenBlockScheduler.cancelTimerStop(context, rule.id)
            }
            RuleType.SCHEDULED ->
                // Manual off: disarm entirely; user re-activates to re-arm.
                OpenBlockScheduler.cancelRule(context, rule.id)
            RuleType.PERMANENT -> Unit
        }
    }

    private suspend fun cancelDisableInternal(ruleId: Int) {
        repository.setDisableEffectiveAt(ruleId, null)
        OpenBlockScheduler.cancelDisableFinalize(context, ruleId)
    }

    private fun finalizeDisableNow(ruleId: Int) {
        viewModelScope.launch {
            repository.finalizeDisable(ruleId)
            OpenBlockScheduler.cancelRule(context, ruleId)
            OpenBlockScheduler.cancelDisableFinalize(context, ruleId)
        }
    }

    /** Natural end of a TIMER rule (start + duration); null for other types. */
    private fun timerEndMillis(rule: OpenBlockRule): Long? =
        if (rule.ruleType == RuleType.TIMER) {
            rule.timerStartedAt?.let { start ->
                rule.timerDurationMinutes?.let { minutes -> start + minutes * 60_000L }
            }
        } else {
            null
        }

    /** Play-compliant flow: persist affirmative consent, then open settings. */
    fun acceptDisclosureAndOpenSettings() {
        prefs.edit().putBoolean(OpenBlockPrefs.KEY_DISCLOSURE_ACCEPTED, true).apply()
        _uiState.update { it.copy(disclosureAccepted = true) }
        backgroundProtectionManager.openAccessibilitySettings()
    }

    fun openAccessibilitySettings() = backgroundProtectionManager.openAccessibilitySettings()
}
