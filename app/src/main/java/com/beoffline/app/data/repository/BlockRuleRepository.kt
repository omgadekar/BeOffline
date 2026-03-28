package com.beoffline.app.data.repository

import android.content.Context
import android.content.Intent
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
    suspend fun getAllRulesOnce(): List<BlockRule> = dao.getAllRules().first()
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
     * Returns all visible, non-system launcher apps on the device.
     * Use this for the AppPicker (needs the selectable app list). For dashboard use
     * getAppInfoForPackages() instead — it's much faster.
     *
     * This intentionally queries launcher activities instead of all installed
     * applications so the app can work without QUERY_ALL_PACKAGES.
     */
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val launcherApps = pm.queryIntentActivitiesCompat(launcherIntent)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val packageName = appInfo.packageName
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                if (((isSystemApp && !isUpdatedSystemApp) || packageName == context.packageName)) {
                    return@mapNotNull null
                }

                iconCache.get(packageName) ?: run {
                    val info = AppInfo(
                        packageName = packageName,
                        appName = resolveInfo.loadLabel(pm).toString(),
                        isSystemApp = isSystemApp,
                        icon = try { resolveInfo.loadIcon(pm) } catch (e: Exception) { null }
                    )
                    iconCache.put(packageName, info)
                    info
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
            .toList()

        launcherApps
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

    @Suppress("DEPRECATION")
    private fun PackageManager.queryIntentActivitiesCompat(intent: Intent) =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            queryIntentActivities(intent, 0)
        }
}
