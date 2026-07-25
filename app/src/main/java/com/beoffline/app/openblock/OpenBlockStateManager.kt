package com.beoffline.app.openblock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenBlockStateManager — holds the live state of the open-block engine.
 *
 * Mirrors [com.beoffline.app.vpn.VpnStateManager]: injected as a Singleton so
 * the AccessibilityService (background) and UI ViewModels observe the same
 * state without coupling. The service is the sole writer.
 *
 * Note: like the VPN flag this is process-memory only. The durable source of
 * truth for "should open-blocking be on" is always the DB (active
 * OpenBlockRule rows); the system itself keeps the accessibility service
 * alive/restarted once the user enables it.
 */
@Singleton
class OpenBlockStateManager @Inject constructor() {

    private val _serviceConnected = MutableStateFlow(false)
    /** True while [OpenBlockAccessibilityService] is bound and receiving events. */
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }
}
