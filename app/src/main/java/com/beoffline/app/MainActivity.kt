package com.beoffline.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.beoffline.app.ui.navigation.BeOfflineNavGraph
import com.beoffline.app.ui.theme.BeOfflineTheme
import com.beoffline.app.vpn.VpnController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var vpnController: VpnController

    // ── VPN Permission Launcher ────────────────────────────────────────────────
    // Android requires the user to explicitly approve a VPN connection on first use.
    // The vpnPermissionLauncher handles this system dialog flow.
    private var pendingBlockedPackages: List<String> = emptyList()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User approved — start the VPN
            if (pendingBlockedPackages.isNotEmpty()) {
                vpnController.startVpn(pendingBlockedPackages)
                pendingBlockedPackages = emptyList()
            }
        }
        // If RESULT_CANCELED, user denied — the UI will reflect inactive state
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeOfflineTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BeOfflineNavGraph(onRequestVpn = ::requestVpnPermission)
                }
            }
        }
    }

    /**
     * Checks if VPN permission is needed. If yes, shows the system dialog.
     * If already granted, starts the VPN directly.
     */
    fun requestVpnPermission(blockedPackages: List<String>) {
        val permissionIntent = vpnController.getVpnPermissionIntent()
        if (permissionIntent != null) {
            pendingBlockedPackages = blockedPackages
            vpnPermissionLauncher.launch(permissionIntent)
        } else if (blockedPackages.isNotEmpty()) {
            // Already approved, start immediately
            vpnController.startVpn(blockedPackages)
        }
    }
}
