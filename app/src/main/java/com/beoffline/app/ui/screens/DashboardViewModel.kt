package com.beoffline.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.vpn.VpnController
import com.beoffline.app.vpn.VpnStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isVpnRunning: Boolean = false,
    val rules: List<BlockRule> = emptyList(),
    val activeRules: List<BlockRule> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: BlockRuleRepository,
    private val vpnController: VpnController,
    private val vpnStateManager: VpnStateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeRules()
        observeVpnState()
    }

    private fun observeRules() {
        viewModelScope.launch {
            combine(
                repository.getAllRules(),
                repository.getActiveRules()
            ) { all, active ->
                _uiState.update { it.copy(rules = all, activeRules = active, isLoading = false) }
            }.collect()
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
            repository.setRuleActive(rule.id, true)
            // Collect all currently active packages
            val activePackages = mutableListOf<String>()
            repository.getActiveRules()
                .first()
                .forEach { activePackages.addAll(it.blockedPackages) }
            // Check if VPN is already running — if so, restart with updated package list
            onNeedVpnPermission(activePackages.distinct())
        }
    }

    fun deactivateRule(rule: BlockRule) {
        viewModelScope.launch {
            repository.setRuleActive(rule.id, false)
            // Check if there are still other active rules
            val remaining = repository.getActiveRules().first()
            if (remaining.isEmpty()) {
                vpnController.stopVpn()
            } else {
                vpnController.startVpn(remaining.flatMap { it.blockedPackages }.distinct())
            }
        }
    }

    fun deleteRule(rule: BlockRule) {
        viewModelScope.launch {
            if (rule.isActive) deactivateRule(rule)
            repository.deleteRule(rule)
        }
    }

    fun stopAll() {
        viewModelScope.launch {
            repository.deactivateAllRules()
            vpnController.stopVpn()
        }
    }
}
