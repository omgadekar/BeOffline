package com.beoffline.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.accountability.RealtimeClient
import com.beoffline.app.ui.navigation.BeOfflineNavGraph
import com.beoffline.app.ui.theme.BeOfflineTheme
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.vpn.VpnController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var vpnController: VpnController

    @Inject
    lateinit var realtimeClient: RealtimeClient

    // Deep-link target from a tapped notification (null = normal launch).
    private val navRoute = MutableStateFlow<String?>(null)

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
        // Draw behind the status and navigation bars: the app's ground should
        // run to the physical edges of the screen, not stop at a black band.
        // Every screen pads its own content off the bars via WindowInsets, and
        // the bottom nav sits above the gesture/button bar rather than under it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        navRoute.value = intent?.getStringExtra(AccountabilityRepository.EXTRA_NAV_ROUTE)
        setContent {
            BeOfflineTheme {
                Box(modifier = Modifier.fillMaxSize().background(Brand900)) {
                    BeOfflineNavGraph(
                        onRequestVpn = ::requestVpnPermission,
                        navRoute = navRoute,
                        onNavRouteHandled = { navRoute.value = null }
                    )
                }
            }
        }
    }

    // App already running: a tapped notification arrives here (singleTop).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(AccountabilityRepository.EXTRA_NAV_ROUTE)?.let { navRoute.value = it }
    }

    // Single-activity app: onStart/onStop ≈ app foreground/background. SignalR
    // rides the foreground; FCM covers everything else.
    override fun onStart() {
        super.onStart()
        realtimeClient.start()
    }

    override fun onStop() {
        realtimeClient.stop()
        super.onStop()
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
