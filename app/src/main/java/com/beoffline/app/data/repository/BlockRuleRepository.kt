package com.beoffline.app.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.beoffline.app.data.local.BlockRuleDao
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.BlockRule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    suspend fun deactivateAllRules() = dao.deactivateAllRules()

    // ── Installed Apps ────────────────────────────────────────────────────────
    /**
     * Returns all installed, non-system, launchable apps on the device.
     * Runs on IO dispatcher as PackageManager calls can be slow on devices
     * with many apps installed.
     */
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                // Filter out system apps (keep user-installed apps only)
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                // Must have a launcher activity (be a foreground app)
                pm.getLaunchIntentForPackage(appInfo.packageName) != null &&
                // Exclude ourselves
                appInfo.packageName != context.packageName
            }
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = appInfo.loadLabel(pm).toString(),
                    isSystemApp = false
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    /**
     * Returns all currently active rule packages as a flat list — 
     * used to reconstruct the VPN allow list after a device reboot.
     */
    suspend fun getActiveBlockedPackages(): List<String> {
        val rules = mutableListOf<BlockRule>()
        dao.getActiveRules().collect { rules.addAll(it) }
        return rules.flatMap { it.blockedPackages }.distinct()
    }
}
