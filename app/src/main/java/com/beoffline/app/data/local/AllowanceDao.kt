package com.beoffline.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.beoffline.app.data.model.Allowance
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowanceDao {

    /**
     * All rows — enforcement filters by grantedUntil per foreground event
     * (a Flow parameterized on "now" would go stale between emissions).
     * Expired rows are cleaned up opportunistically via [deleteExpired].
     */
    @Query("SELECT * FROM allowances")
    fun getAll(): Flow<List<Allowance>>

    @Insert
    suspend fun insert(allowance: Allowance): Long

    @Query("DELETE FROM allowances WHERE grantedUntil <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)

    @Query("DELETE FROM allowances WHERE ruleId = :ruleId")
    suspend fun deleteForRule(ruleId: Int)

    /** Remote (partner/group) grants use ruleId 0 — cleared on account deletion. */
    @Query("DELETE FROM allowances WHERE ruleId = 0")
    suspend fun deleteRemoteGrants()
}
