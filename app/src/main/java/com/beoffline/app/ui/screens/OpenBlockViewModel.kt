package com.beoffline.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isLoading: Boolean = true
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
    private val backgroundProtectionManager: BackgroundProtectionManager
) : ViewModel() {

    private val prefs = context.getSharedPreferences(OpenBlockPrefs.FILE, Context.MODE_PRIVATE)

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
    }

    fun deleteRule(rule: OpenBlockRule) {
        viewModelScope.launch {
            OpenBlockScheduler.cancelRule(context, rule.id)
            repository.deleteRule(rule)
        }
    }

    /** Play-compliant flow: persist affirmative consent, then open settings. */
    fun acceptDisclosureAndOpenSettings() {
        prefs.edit().putBoolean(OpenBlockPrefs.KEY_DISCLOSURE_ACCEPTED, true).apply()
        _uiState.update { it.copy(disclosureAccepted = true) }
        backgroundProtectionManager.openAccessibilitySettings()
    }

    fun openAccessibilitySettings() = backgroundProtectionManager.openAccessibilitySettings()
}
