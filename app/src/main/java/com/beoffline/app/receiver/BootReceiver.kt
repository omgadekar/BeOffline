package com.beoffline.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.vpn.VpnController
import com.beoffline.app.vpn.VpnResilienceScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BootReceiver — restores the VPN state after a device reboot.
 *
 * When the device reboots, the VPN service is killed. If the user had active
 * blocking rules, we need to restart the VPN automatically.
 *
 * This receiver is triggered by:
 *   - android.intent.action.BOOT_COMPLETED (device boot)
 *   - android.intent.action.MY_PACKAGE_REPLACED (app update)
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: BlockRuleRepository

    @Inject
    lateinit var vpnController: VpnController

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.d(TAG, "Device boot/update detected. Checking for active rules...")

        // Use goAsync to safely do async work in a BroadcastReceiver
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activePackages = repository.getActiveBlockedPackages()
                if (activePackages.isNotEmpty()) {
                    Log.d(TAG, "Restoring VPN for ${activePackages.size} packages.")
                    VpnResilienceScheduler.ensureHealthMonitor(context)
                    vpnController.startVpn(activePackages)
                } else {
                    Log.d(TAG, "No active rules. VPN not restarted.")
                    VpnResilienceScheduler.cancelHealthMonitor(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring VPN on boot: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
