package com.beoffline.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.repository.BlockRuleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VpnController — the single point of truth for starting/stopping the VPN.
 *
 * All UI and Worker components should use this class rather than constructing
 * service intents directly. This ensures consistent state management across
 * the dashboard, quick-toggle, and scheduled rule starting.
 *
 * VPN Permission Flow:
 *   Before starting the VPN for the first time, Android requires the user to
 *   explicitly approve the VPN connection. The VpnService.prepare() method
 *   returns an Intent if approval is needed, or null if already approved.
 *   The Activity must launch this intent for the user to approve.
 */
@Singleton
class VpnController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateManager: VpnStateManager
) {
    companion object {
        private const val TAG = "VpnController"
    }

    val vpnRunning: StateFlow<Boolean> get() = stateManager.isRunning

    /**
     * Returns an Intent that the Activity must start (via startActivityForResult)
     * to request VPN permission from the user.
     * Returns null if permission is already granted.
     */
    fun getVpnPermissionIntent(): Intent? {
        return VpnService.prepare(context)
    }

    /**
     * Starts the VPN for the given list of package names.
     * Call only after VPN permission has been granted.
     */
    fun startVpn(blockedPackages: List<String>) {
        if (blockedPackages.isEmpty()) {
            Log.w(TAG, "startVpn called with no packages to block. Ignoring.")
            return
        }
        Log.d(TAG, "Starting VPN for packages: $blockedPackages")
        val intent = Intent(context, BeOfflineVpnService::class.java).apply {
            action = BeOfflineVpnService.ACTION_START
            putStringArrayListExtra(
                BeOfflineVpnService.EXTRA_BLOCKED_PACKAGES,
                ArrayList(blockedPackages)
            )
        }
        context.startForegroundService(intent)
    }

    /**
     * Stops the running VPN tunnel.
     */
    fun stopVpn() {
        Log.d(TAG, "Stopping VPN.")
        val intent = Intent(context, BeOfflineVpnService::class.java).apply {
            action = BeOfflineVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}
