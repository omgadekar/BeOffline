package com.beoffline.app.data.local

import androidx.room.*
import com.beoffline.app.data.model.OpenBlockRule
import kotlinx.coroutines.flow.Flow

/**
 * DAO for all OpenBlockRule CRUD operations.
 * Returns Flows so the UI automatically reacts to DB changes.
 * Mirrors [BlockRuleDao] — the two restriction engines keep independent rules.
 */
@Dao
interface OpenBlockRuleDao {

    // ── Read ──────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM open_block_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<OpenBlockRule>>

    @Query("SELECT * FROM open_block_rules WHERE isActive = 1")
    fun getActiveRules(): Flow<List<OpenBlockRule>>

    @Query("SELECT * FROM open_block_rules WHERE id = :id")
    suspend fun getRuleById(id: Int): OpenBlockRule?

    // ── Write ─────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: OpenBlockRule): Long

    @Update
    suspend fun updateRule(rule: OpenBlockRule)

    @Delete
    suspend fun deleteRule(rule: OpenBlockRule)

    @Query("DELETE FROM open_block_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)

    // ── Activation ────────────────────────────────────────────────────────────

    @Query("UPDATE open_block_rules SET isActive = :isActive WHERE id = :id")
    suspend fun setRuleActive(id: Int, isActive: Boolean)

    @Query("UPDATE open_block_rules SET isActive = 0")
    suspend fun deactivateAllRules()

    // ── Timer ─────────────────────────────────────────────────────────────────

    @Query("UPDATE open_block_rules SET timerStartedAt = :startedAt WHERE id = :id")
    suspend fun setTimerStartedAt(id: Int, startedAt: Long?)
}
