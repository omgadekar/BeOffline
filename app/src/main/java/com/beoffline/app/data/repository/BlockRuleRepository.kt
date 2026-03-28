package com.beoffline.app.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.LruCache
import com.beoffline.app.data.local.BlockRuleDao
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.BlockRule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockRuleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BlockRuleDao
) {
    // ── Rules ─────────────────────────────────────────────────────────────────

    fun getAllRules(): Flow<List<BlockRule>> = dao.getAllRules()
    fun getActiveRules(): Flow<List<BlockRule>> = dao.getActiveRules()
    suspend fun getRuleById(id: Int) = dao.getRuleById(id)
    suspend fun saveRule(rule: BlockRule): Long = dao.insertRule(rule)
    suspend fun updateRule(rule: BlockRule) = dao.updateRule(rule)
    suspend fun deleteRule(rule: BlockRule) = dao.deleteRule(rule)
    suspend fun setRuleActive(id: Int, active: Boolean) = dao.setRuleActive(id, active)
    suspend fun setTimerStartedAt(id: Int, startedAt: Long?) = dao.setTimerStartedAt(id, startedAt)
    suspend fun deactivateAllRules() = dao.deactivateAllRules()
    suspend fun getActiveRulesOnce(): List<BlockRule> = dao.getActiveRules().first()

    // ── Installed Apps (with in-memory cache) ──────────────────────────────────

    /**
     * In-memory LRU cache: packageName → AppInfo.
     * Survives for the lifetime of the app process — no disk I/O needed.
     * 200 entries is plenty for a typical device (avg ~100 user apps).
     */
    private val iconCache = LruCache<String, AppInfo>(200)

    /**
     * Fast path: loads AppInfo only for the specific [packages] needed.
     * Checks the LruCache first; only hits PackageManager for uncached entries.
     *
     * Use this for the Dashboard (3–10 packages) instead of getInstalledApps()
     * which loads ALL 100+ apps and takes ~2 seconds on first call.
     */
    suspend fun getAppInfoForPackages(packages: List<String>): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            packages.mapNotNull { packageName ->
                // Cache hit — instant
                iconCache.get(packageName) ?: try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val info = AppInfo(
                        packageName = packageName,
                        appName = appInfo.loadLabel(pm).toString(),
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        icon = try { appInfo.loadIcon(pm) } catch (e: Exception) { null }
                    )
                    iconCache.put(packageName, info)
                    info
                } catch (e: Exception) {
                    null  // package uninstalled or unavailable
                }
            }
        }

    /**
     * Returns all installed, non-system, launchable apps on the device.
     * Use this for the AppPicker (needs full list). For dashboard use
     * getAppInfoForPackages() instead — it's much faster.
     *
     * First call scans all apps (slow). Results are cached per-package in
     * LruCache, so subsequent calls (and getAppInfoForPackages) are instant.
     */
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val rawApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                (!isSystemApp || isUpdatedSystemApp) &&
                pm.getLaunchIntentForPackage(appInfo.packageName) != null &&
                appInfo.packageName != context.packageName
            }

        rawApps.map { appInfo ->
            // Return cached entry if available (avoids icon decode cost)
            iconCache.get(appInfo.packageName) ?: run {
                val info = AppInfo(
                    packageName = appInfo.packageName,
                    appName = appInfo.loadLabel(pm).toString(),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    icon = try { appInfo.loadIcon(pm) } catch (e: Exception) { null }
                )
                iconCache.put(appInfo.packageName, info)
                info
            }
        }.sortedBy { it.appName.lowercase() }
    }

    /**
     * Returns all currently active rule packages as a flat list.
     * Used to reconstruct the VPN allow list after a device reboot.
     *
     * IMPORTANT: Uses .first() — NOT .collect() — because getActiveRules()
     * returns a Room Flow that never completes, and calling collect() inside
     * a BroadcastReceiver / one-shot coroutine causes an ANR.
     */
    suspend fun getActiveBlockedPackages(): List<String> {
        val rules = dao.getActiveRules().first()   // ← was .collect{} — caused ANR
        return rules.flatMap { it.blockedPackages }.distinct()
    }
}
