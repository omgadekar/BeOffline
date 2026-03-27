package com.beoffline.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VpnStateManager — holds the live running state of the VPN.
 *
 * This is injected as a Singleton so that both the VpnService (background)
 * and the UI ViewModels can observe and update the same state without coupling.
 *
 * The VpnService calls setRunning(true/false) to update state.
 * ViewModels observe [isRunning] to update the UI accordingly.
 */
@Singleton
class VpnStateManager @Inject constructor() {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}
