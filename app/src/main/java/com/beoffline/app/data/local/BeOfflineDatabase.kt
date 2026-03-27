package com.beoffline.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.beoffline.app.data.model.BlockRule

@Database(
    entities = [BlockRule::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BeOfflineDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
}
