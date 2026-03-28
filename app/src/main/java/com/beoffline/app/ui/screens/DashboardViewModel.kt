package com.beoffline.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.background.BackgroundProtectionManager
import com.beoffline.app.background.BackgroundProtectionStatus
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.scheduler.RuleScheduler
import com.beoffline.app.support.BlockedTrafficAlertManager
import com.beoffline.app.support.IssueReporter
import com.beoffline.app.vpn.VpnController
import com.beoffline.app.vpn.VpnStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isVpnRunning: Boolean = false,
    val rules: List<BlockRule> = emptyList(),
    val activeRules: List<BlockRule> = emptyList(),
    val appInfosByRule: Map<Int, List<AppInfo>> = emptyMap(),
    val backgroundProtection: BackgroundProtectionStatus? = null,
    val blockedTrafficAlertsEnabled: Boolean = true,
    val showWelcomeDialog: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backgroundProtectionManager: BackgroundProtectionManager,
    private val repository: BlockRuleRepository,
    private val blockedTrafficAlertManager: BlockedTrafficAlertManager,
    private val issueReporter: IssueReporter,
    private val vpnController: VpnController,
    private val vpnStateManager: VpnStateManager
) : ViewModel() {

    private val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        DashboardUiState(
            isLoading = true,
            blockedTrafficAlertsEnabled = blockedTrafficAlertManager.isEnabled()
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeRules()
        observeVpnState()
        refreshBackgroundProtection()
    }

    private fun observeRules() {
        viewModelScope.launch {
            combine(
                repository.getAllRules(),
                repository.getActiveRules()
            ) { allRules, activeRules ->
                _uiState.update {
                    it.copy(
                        rules = allRules,
                        activeRules = activeRules,
                        isLoading = false
                    )
                }
                resolveAppIcons(allRules)
            }.collect { }
        }
    }

    private suspend fun resolveAppIcons(rules: List<BlockRule>) {
        try {
            val allPackages = rules.flatMap { it.blockedPackages }.distinct()
            val infos = repository.getAppInfoForPackages(allPackages)
            val lookup = infos.associateBy { it.packageName }
            val infoMap = rules.associate { rule ->
                rule.id to rule.blockedPackages.mapNotNull { pkg -> lookup[pkg] }
            }
            _uiState.update { it.copy(appInfosByRule = infoMap) }
        } catch (_: Exception) {
        }
    }

    private fun observeVpnState() {
        viewModelScope.launch {
            vpnStateManager.isRunning.collect { running ->
                _uiState.update { it.copy(isVpnRunning = running) }
            }
        }
    }

    fun activateRule(rule: BlockRule, onNeedVpnPermission: (List<String>) -> Unit) {
        viewModelScope.launch {
            val timerStartedAt = if (rule.ruleType == RuleType.TIMER) System.currentTimeMillis() else null

            repository.setRuleActive(rule.id, true)
            if (rule.ruleType == RuleType.TIMER) {
                repository.setTimerStartedAt(rule.id, timerStartedAt)
                RuleScheduler.scheduleTimerStop(
                    context,
                    rule.copy(isActive = true, timerStartedAt = timerStartedAt)
                )
            }

            val activePackages = repository
                .getActiveRulesOnce()
                .flatMap { it.blockedPackages }
                .distinct()

            onNeedVpnPermission(activePackages)
        }
    }

    fun deactivateRule(rule: BlockRule) {
        viewModelScope.launch {
            deactivateRuleInternal(rule)
        }
    }

    fun deleteRule(rule: BlockRule) {
        viewModelScope.launch {
            RuleScheduler.cancelRule(context, rule.id)
            if (rule.isActive) deactivateRuleInternal(rule)
            repository.deleteRule(rule)
        }
    }

    fun stopAll() {
        viewModelScope.launch {
            repository.getActiveRulesOnce().forEach { activeRule ->
                if (activeRule.ruleType == RuleType.TIMER) {
                    repository.setTimerStartedAt(activeRule.id, null)
                    RuleScheduler.cancelTimerStop(context, activeRule.id)
                }
            }
            repository.deactivateAllRules()
            vpnController.stopVpn()
        }
    }

    fun refreshBackgroundProtection() {
        val status = backgroundProtectionManager.getStatus()
        _uiState.update {
            it.copy(
                backgroundProtection = status,
                showWelcomeDialog = !status.needsAttention && !hasSeenWelcomeDialog()
            )
        }
    }

    fun openBatteryOptimizationSettings() {
        backgroundProtectionManager.openBatteryOptimizationFlow()
    }

    fun openExactAlarmSettings() {
        backgroundProtectionManager.openExactAlarmSettings()
    }

    fun openAppSettings() {
        backgroundProtectionManager.openAppDetailsSettings()
    }

    fun openNotificationSettings() {
        backgroundProtectionManager.openNotificationSettings()
    }

    fun dismissWelcomeDialog() {
        preferences.edit().putBoolean(KEY_WELCOME_DIALOG_SEEN, true).apply()
        _uiState.update { it.copy(showWelcomeDialog = false) }
    }

    fun setBlockedTrafficAlertsEnabled(enabled: Boolean) {
        blockedTrafficAlertManager.setEnabled(enabled)
        _uiState.update { it.copy(blockedTrafficAlertsEnabled = enabled) }
    }

    fun submitIssueReport(title: String, details: String) {
        val snapshot = _uiState.value
        issueReporter.submitIssue(
            title = title,
            details = details,
            isVpnRunning = snapshot.isVpnRunning,
            activeRulesCount = snapshot.activeRules.size,
            totalRulesCount = snapshot.rules.size
        )
    }

    private suspend fun deactivateRuleInternal(rule: BlockRule) {
        repository.setRuleActive(rule.id, false)
        if (rule.ruleType == RuleType.TIMER) {
            repository.setTimerStartedAt(rule.id, null)
            RuleScheduler.cancelTimerStop(context, rule.id)
        }

        val remaining = repository.getActiveRulesOnce()
        if (remaining.isEmpty()) {
            vpnController.stopVpn()
        } else {
            vpnController.startVpn(remaining.flatMap { it.blockedPackages }.distinct())
        }
    }

    private fun hasSeenWelcomeDialog(): Boolean {
        return preferences.getBoolean(KEY_WELCOME_DIALOG_SEEN, false)
    }

    private companion object {
        const val KEY_WELCOME_DIALOG_SEEN = "welcome_dialog_seen"
    }
}
