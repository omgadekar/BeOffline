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

@AndroidEntryPoint
class VpnRecoveryReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: BlockRuleRepository

    @Inject
    lateinit var vpnController: VpnController

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activePackages = repository.getActiveBlockedPackages()
                if (activePackages.isNotEmpty()) {
                    Log.d("VpnRecoveryReceiver", "Restarting VPN after unexpected stop.")
                    vpnController.startVpn(activePackages)
                    VpnResilienceScheduler.ensureHealthMonitor(context)
                } else {
                    VpnResilienceScheduler.cancelHealthMonitor(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
