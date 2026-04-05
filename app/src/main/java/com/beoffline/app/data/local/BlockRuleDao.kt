package com.beoffline.app.data.local

import androidx.room.*
import com.beoffline.app.data.model.BlockRule
import kotlinx.coroutines.flow.Flow

/**
 * DAO for all BlockRule CRUD operations.
 * Returns Flows so the UI automatically reacts to DB changes.
 */
@Dao
interface BlockRuleDao {

    // ── Read ──────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM block_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE isActive = 1")
    fun getActiveRules(): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE id = :id")
    suspend fun getRuleById(id: Int): BlockRule?

    // ── Write ─────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlockRule): Long

    @Update
    suspend fun updateRule(rule: BlockRule)

    @Delete
    suspend fun deleteRule(rule: BlockRule)

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)

    // ── Activation ────────────────────────────────────────────────────────────

    @Query("UPDATE block_rules SET isActive = :isActive WHERE id = :id")
    suspend fun setRuleActive(id: Int, isActive: Boolean)

    @Query("UPDATE block_rules SET isActive = 0")
    suspend fun deactivateAllRules()

    // ── Timer ─────────────────────────────────────────────────────────────────

    @Query("UPDATE block_rules SET timerStartedAt = :startedAt WHERE id = :id")
    suspend fun setTimerStartedAt(id: Int, startedAt: Long?)
}
