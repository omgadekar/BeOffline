package com.beoffline.app.data.repository

import com.beoffline.app.data.local.OpenBlockRuleDao
import com.beoffline.app.data.model.OpenBlockRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the open-blocking engine's rules.
 *
 * Deliberately mirrors [BlockRuleRepository]'s rules section only — the
 * installed-app listing (and its icon cache) stays in [BlockRuleRepository]
 * and is shared by both features' pickers.
 */
@Singleton
class OpenBlockRuleRepository @Inject constructor(
    private val dao: OpenBlockRuleDao
) {
    fun getAllRules(): Flow<List<OpenBlockRule>> = dao.getAllRules()
    fun getActiveRules(): Flow<List<OpenBlockRule>> = dao.getActiveRules()
    suspend fun getAllRulesOnce(): List<OpenBlockRule> = dao.getAllRules().first()
    suspend fun getActiveRulesOnce(): List<OpenBlockRule> = dao.getActiveRules().first()
    suspend fun getRuleById(id: Int) = dao.getRuleById(id)
    suspend fun saveRule(rule: OpenBlockRule): Long = dao.insertRule(rule)
    suspend fun updateRule(rule: OpenBlockRule) = dao.updateRule(rule)
    suspend fun deleteRule(rule: OpenBlockRule) = dao.deleteRule(rule)
    suspend fun setRuleActive(id: Int, active: Boolean) = dao.setRuleActive(id, active)
    suspend fun setTimerStartedAt(id: Int, startedAt: Long?) = dao.setTimerStartedAt(id, startedAt)
    suspend fun setDisableEffectiveAt(id: Int, effectiveAt: Long?) = dao.setDisableEffectiveAt(id, effectiveAt)
    suspend fun deactivateAllRules() = dao.deactivateAllRules()

    /** Finalizes a pending-disable cooldown: the lock is now fully off. */
    suspend fun finalizeDisable(id: Int) {
        dao.setDisableEffectiveAt(id, null)
        dao.setTimerStartedAt(id, null)
        dao.setRuleActive(id, false)
    }

    /**
     * Returns all currently open-blocked packages as a flat list.
     * Used by the open-block engine to know what to enforce (and by
     * BootReceiver-style reconciliation after reboot).
     *
     * IMPORTANT: Uses .first() — NOT .collect() — because getActiveRules()
     * returns a Room Flow that never completes, and calling collect() inside
     * a BroadcastReceiver / one-shot coroutine causes an ANR.
     */
    suspend fun getActiveOpenBlockedPackages(): List<String> {
        val rules = dao.getActiveRules().first()
        return rules.flatMap { it.blockedPackages }.distinct()
    }
}
