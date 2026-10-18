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
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.scheduler.RuleScheduler
import com.beoffline.app.support.BlockedTrafficAlertManager
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
 * internet connection at all. Note that some push notifications may still be delivered
 * by Android system services unless they are separately dismissed by notification access.
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

        /**
         * The IPv6 half of the tunnel.
         *
         * Without these, only IPv4 was claimed, and a blocked app's IPv6 traffic
         * went straight out of the real interface — so apps on dual-stack
         * networks (every major carrier, and most home broadband) carried on
         * loading while the UI said they were offline. Instagram and the rest
         * of Meta are IPv6-first, which is why they were the obvious symptom.
         *
         * A unique-local address: it exists only inside the tunnel and is never
         * routable anywhere.
         */
        const val VPN_IPV6 = "fd00:beef:0:0:0:0:0:1"
        const val VPN_IPV6_PREFIX = 128
        const val VPN_ROUTE_V6 = "::"
        const val VPN_ROUTE_V6_PREFIX = 0

        /**
         * DNS inside the tunnel, so name lookups from a blocked app are dropped
         * with everything else. This used to be 8.8.8.8, which both leaked the
         * fact of each lookup to Google and was pointless, since the tunnel
         * discards the query anyway.
         */
        const val VPN_DNS = "10.0.0.1"

        // Intent actions to control the service
        const val ACTION_START = "com.beoffline.vpn.START"
        const val ACTION_STOP  = "com.beoffline.vpn.STOP"
        const val EXTRA_BLOCKED_PACKAGES = "blocked_packages"
    }

    @Inject
    lateinit var vpnStateManager: VpnStateManager

    @Inject
    lateinit var repository: BlockRuleRepository

    @Inject
    lateinit var blockedTrafficAlertManager: BlockedTrafficAlertManager

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var stopRequested = false

    // The list of package names whose internet access will be blocked.
    private var blockedPackages: List<String> = emptyList()

    // =========================================================================
    // Service Lifecycle
    // =========================================================================

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                stopRequested = false
                blockedPackages = intent.getStringArrayListExtra(EXTRA_BLOCKED_PACKAGES)
                    ?: emptyList<String>() as ArrayList<String>
                blockedTrafficAlertManager.markSessionStarted()

                Log.d(TAG, "Starting VPN. Blocking ${blockedPackages.size} apps: $blockedPackages")
                startForeground(NOTIFICATION_ID, buildNotification(blockedPackages.size))
                startVpnTunnel()
                START_STICKY // OS will restart service if killed
            }
            ACTION_STOP -> {
                stopRequested = true
                blockedTrafficAlertManager.markSessionStopped()
                Log.d(TAG, "Stop command received.")
                serviceScope.launch {
                    runCatching {
                        repository.getActiveRulesOnce().forEach { activeRule ->
                            if (activeRule.ruleType == RuleType.TIMER) {
                                repository.setTimerStartedAt(activeRule.id, null)
                                RuleScheduler.cancelTimerStop(this@BeOfflineVpnService, activeRule.id)
                            }
                            repository.setRuleActive(activeRule.id, false)
                        }
                    }.onFailure { error ->
                        Log.e(TAG, "Failed clearing active rules from notification stop.", error)
                    }

                    withContext(Dispatchers.Main) {
                        stopVpn()
                        stopSelf()
                    }
                }
                START_NOT_STICKY
            }
            // Android restarting a START_STICKY service hands us a null intent.
            // This branch used to do nothing at all, so after an OS kill the
            // service came back alive with no tunnel — the notification was
            // gone, the UI still said the rules were active, and every blocked
            // app had working internet. Rebuild from the database instead.
            else -> {
                stopRequested = false
                startForeground(NOTIFICATION_ID, buildNotification(blockedPackages.size))
                serviceScope.launch {
                    val packages = runCatching { repository.getActiveBlockedPackages() }
                        .onFailure { Log.e(TAG, "Could not reload blocked packages.", it) }
                        .getOrDefault(emptyList())

                    if (packages.isEmpty()) {
                        Log.d(TAG, "Restarted with no active rules; standing down.")
                        withContext(Dispatchers.Main) {
                            stopVpn()
                            stopSelf()
                        }
                        return@launch
                    }

                    Log.i(TAG, "Restarted by the system; re-blocking ${packages.size} apps.")
                    blockedPackages = packages
                    blockedTrafficAlertManager.markSessionStarted()
                    withContext(Dispatchers.Main) {
                        startForeground(NOTIFICATION_ID, buildNotification(packages.size))
                    }
                    startVpnTunnel()
                }
                START_STICKY
            }
        }
    }

    override fun onRevoke() {
        // Called by Android when the user revokes VPN permission from system settings
        Log.w(TAG, "VPN permission revoked by user.")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        if (shouldScheduleRecovery()) {
            VpnResilienceScheduler.scheduleRecovery(this)
        }
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (shouldScheduleRecovery()) {
            VpnResilienceScheduler.scheduleRecovery(this)
        }
        super.onTaskRemoved(rootIntent)
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
        // An empty allow-list means "allow nothing specific", which Android
        // reads as "tunnel EVERY app" — the whole phone would go offline. The
        // callers guard against this, but the consequence is severe enough to
        // check again here.
        if (blockedPackages.isEmpty()) {
            Log.w(TAG, "No packages to block; refusing to establish a catch-all tunnel.")
            stopVpn()
            stopSelf()
            return
        }

        // Re-establishing (a rule changed while the tunnel was up) must tear the
        // old one down first. Previously the fd and the packet loop were simply
        // overwritten, leaving an orphaned loop reading a stale descriptor.
        teardownTunnel()

        try {
            val builder = Builder()
                .setSession("BeOffline")
                .addAddress(VPN_IP, 32)
                .addDnsServer(VPN_DNS)
                .addRoute(VPN_ROUTE, VPN_ROUTE_PREFIX) // Catch-all route, IPv4

            // IPv6, added separately: a device or ROM without IPv6 support can
            // reject these, and that must not cost us the IPv4 block as well.
            try {
                builder.addAddress(VPN_IPV6, VPN_IPV6_PREFIX)
                builder.addRoute(VPN_ROUTE_V6, VPN_ROUTE_V6_PREFIX)
                Log.d(TAG, "IPv6 claimed by the tunnel.")
            } catch (e: Exception) {
                Log.w(TAG, "IPv6 unavailable on this device, IPv4 only: ${e.message}")
            }

            // ── Add each blocked app to the tunnel (their traffic gets trapped) ──
            var tunnelled = 0
            for (pkg in blockedPackages) {
                try {
                    builder.addAllowedApplication(pkg)
                    tunnelled++
                    Log.d(TAG, "Tunneling (blocking): $pkg")
                } catch (e: Exception) {
                    Log.w(TAG, "Package not found, skipping: $pkg")
                }
            }

            // Every package was uninstalled or unresolvable. Establishing now
            // would again mean "tunnel everything".
            if (tunnelled == 0) {
                Log.w(TAG, "None of the blocked packages resolved; not establishing.")
                stopVpn()
                stopSelf()
                return
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
                            blockedTrafficAlertManager.notifyBlockedTrafficAttempt(blockedPackages)
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
        teardownTunnel()
        vpnStateManager.setRunning(false)
        blockedTrafficAlertManager.markSessionStopped()
    }

    /**
     * Closes the tunnel without touching the session state, so a rebuild does
     * not briefly report itself as stopped.
     */
    private fun teardownTunnel() {
        vpnJob?.cancel()
        vpnJob = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing VPN interface: ${e.message}")
        }
        vpnInterface = null
    }

    private fun shouldScheduleRecovery(): Boolean {
        return !stopRequested && blockedPackages.isNotEmpty()
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
