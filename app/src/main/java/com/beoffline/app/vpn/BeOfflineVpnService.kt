package com.beoffline.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.beoffline.app.MainActivity
import com.beoffline.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
/**
 * BeOfflineVpnService — The heart of the application.
 *
 * HOW IT WORKS:
 * Android's VpnService allows us to create a local "tunnel" network interface (tun0).
 * When we add an app to the "allowed" set, Android routes ALL of that app's internet
 * traffic through our tun0 interface instead of the real WiFi/LTE interface.
 *
 * We then intentionally do NOTHING with the packets — they enter our tunnel and are
 * silently discarded. To the app (e.g., WhatsApp), the phone appears to have no
 * internet connection at all. Messages are never delivered; notifications never fire.
 *
 * Meanwhile, every app NOT in our blocked list bypasses the tunnel entirely and
 * continues to use the real network interface normally.
 *
 * PACKET FLOW:
 *   [Blocked App] → tun0 (our interface) → ByteBuffer reads → packets DROPPED
 *   [Other Apps]  → eth0/wlan0 (real interface) → internet as normal
 */
@AndroidEntryPoint
class BeOfflineVpnService : VpnService() {

    companion object {
        private const val TAG = "BeOfflineVpnService"
        const val CHANNEL_ID = "beoffline_vpn_channel"
        const val NOTIFICATION_ID = 1001
        const val VPN_IP = "10.0.0.2"        // Fake IP assigned to our tun interface
        const val VPN_ROUTE = "0.0.0.0"      // Route all IPv4 traffic through the tunnel
        const val VPN_ROUTE_PREFIX = 0        // /0 = all traffic

        // Intent actions to control the service
        const val ACTION_START = "com.beoffline.vpn.START"
        const val ACTION_STOP  = "com.beoffline.vpn.STOP"
        const val EXTRA_BLOCKED_PACKAGES = "blocked_packages"
    }

    @Inject
    lateinit var vpnStateManager: VpnStateManager

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // The list of package names whose internet access will be blocked.
    private var blockedPackages: List<String> = emptyList()

    // =========================================================================
    // Service Lifecycle
    // =========================================================================

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                blockedPackages = intent.getStringArrayListExtra(EXTRA_BLOCKED_PACKAGES)
                    ?: emptyList<String>() as ArrayList<String>

                Log.d(TAG, "Starting VPN. Blocking ${blockedPackages.size} apps: $blockedPackages")
                startForeground(NOTIFICATION_ID, buildNotification(blockedPackages.size))
                startVpnTunnel()
                START_STICKY // OS will restart service if killed
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stop command received.")
                stopVpn()
                stopSelf()
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        // Called by Android when the user revokes VPN permission from system settings
        Log.w(TAG, "VPN permission revoked by user.")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
    }

    // =========================================================================
    // Core VPN Tunnel Setup
    // =========================================================================

    /**
     * Establishes the VPN interface.
     *
     * The builder is configured to route the blocked apps' traffic into our tunnel.
     * All other apps are added as "disallowed" and bypass the tunnel entirely.
     *
     * We use addDisallowedApplication for all apps we want to keep unblocked,
     * and for blocked apps, we do the opposite by allowing only them through the
     * tunnel — effectively using the tunnel as a black hole for their traffic.
     *
     * DESIGN CHOICE: We use allowedApplications (whitelist to tunnel) mode rather
     * than disallowedApplications to have precise per-packet control. Only the
     * selected apps' packets enter our tun0 interface.
     */
    private fun startVpnTunnel() {
        try {
            val builder = Builder()
                .setSession("BeOffline")
                .addAddress(VPN_IP, 32)
                .addDnsServer("8.8.8.8")
                .addRoute(VPN_ROUTE, VPN_ROUTE_PREFIX) // Catch-all route

            // ── Add each blocked app to the tunnel (their traffic gets trapped) ──
            for (pkg in blockedPackages) {
                try {
                    builder.addAllowedApplication(pkg)
                    Log.d(TAG, "Tunneling (blocking): $pkg")
                } catch (e: Exception) {
                    Log.w(TAG, "Package not found, skipping: $pkg")
                }
            }

            // ── Build the virtual network interface ──
            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface. Permission may not have been granted.")
                vpnStateManager.setRunning(false)
                return
            }

            vpnStateManager.setRunning(true)
            Log.i(TAG, "VPN tunnel established. tun0 file descriptor: ${vpnInterface!!.fd}")

            // ── Start a coroutine to "consume" the tunnel's packets (drop them) ──
            startPacketDropLoop()

        } catch (e: Exception) {
            Log.e(TAG, "Error establishing VPN tunnel: ${e.message}", e)
            vpnStateManager.setRunning(false)
        }
    }

    /**
     * The packet drop loop.
     *
     * We must read packets from the tun0 interface to prevent the OS from blocking the
     * file descriptor. We simply read each packet into a buffer and discard it.
     * The blocked apps' TCP connections will time out naturally.
     *
     * This coroutine runs for the entire lifetime of the VPN session.
     */
    private fun startPacketDropLoop() {
        val vpnFd = vpnInterface ?: return
        // Use a plain ByteArray — 32KB covers max IP packet size
        val buffer = ByteArray(32767)

        vpnJob = serviceScope.launch {
            // FileInputStream from the raw fd — does NOT close the ParcelFileDescriptor
            // when the stream itself is closed. AutoCloseInputStream was closing the
            // VPN fd on any IOException, which crashed the service on high-traffic apps
            // like Instagram (which sends rapid QUIC/UDP bursts).
            val inputStream = java.io.FileInputStream(vpnFd.fileDescriptor)
            Log.d(TAG, "Packet drop loop started.")
            try {
                while (isActive) {
                    val bytesRead = try {
                        inputStream.read(buffer)
                    } catch (e: java.io.IOException) {
                        // EAGAIN: non-blocking fd with no data yet — not a real error
                        if (e.message?.contains("EAGAIN") == true) {
                            delay(5)
                            continue
                        }
                        // Real IO error (fd closed, VPN revoked) — exit cleanly
                        Log.w(TAG, "Packet loop IO error: ${e.message}")
                        break
                    }

                    when {
                        bytesRead > 0 -> {
                            // Packet received — silently discard. The packet dies here. 🎯
                        }
                        bytesRead == 0 -> {
                            // Empty non-blocking read
                            delay(5)
                        }
                        else -> {
                            // -1 = EOF — VPN interface was closed externally
                            Log.d(TAG, "Packet loop: EOF on tun fd, exiting.")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Packet loop unexpected error: ${e.message}", e)
                }
            }
            Log.d(TAG, "Packet drop loop ended.")
        }
    }

    // =========================================================================
    // Teardown
    // =========================================================================

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN and closing tunnel interface.")
        vpnJob?.cancel()
        vpnJob = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing VPN interface: ${e.message}")
        }
        vpnInterface = null
        vpnStateManager.setRunning(false)
    }

    // =========================================================================
    // Notification (required for foreground service)
    // =========================================================================

    private fun buildNotification(blockedCount: Int): Notification {
        createNotificationChannel()

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BeOfflineVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BeOffline Active")
            .setContentText("$blockedCount app${if (blockedCount != 1) "s" else ""} offline")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BeOffline VPN Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows which apps are currently offline"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
