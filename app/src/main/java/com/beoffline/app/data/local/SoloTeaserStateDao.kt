package com.beoffline.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.beoffline.app.data.model.SoloTeaserState

@Dao
interface SoloTeaserStateDao {

    @Query("SELECT * FROM solo_teaser_state WHERE ruleId = :ruleId")
    suspend fun getForRule(ruleId: Int): SoloTeaserState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SoloTeaserState)

    @Query("DELETE FROM solo_teaser_state WHERE ruleId = :ruleId")
    suspend fun deleteForRule(ruleId: Int)
}
