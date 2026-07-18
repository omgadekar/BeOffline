package com.beoffline.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.SoloTeaserState

@Database(
    entities = [BlockRule::class, OpenBlockRule::class, Allowance::class, SoloTeaserState::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BeOfflineDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun openBlockRuleDao(): OpenBlockRuleDao
    abstract fun allowanceDao(): AllowanceDao
    abstract fun soloTeaserStateDao(): SoloTeaserStateDao

    companion object {
        /**
         * v1 → v2: adds the open-blocking engine's rule table.
         *
         * PURE ADDITION — `block_rules` (the shipped internet-block feature)
         * is never altered, so existing users' rules are fully preserved.
         * The CREATE statement must stay byte-identical to the schema Room
         * expects (see app/schemas/…/2.json) or Room's integrity check fails
         * at open.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `open_block_rules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`blockedPackages` TEXT NOT NULL, " +
                        "`ruleType` TEXT NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`startHour` INTEGER, " +
                        "`startMinute` INTEGER, " +
                        "`endHour` INTEGER, " +
                        "`endMinute` INTEGER, " +
                        "`activeDays` TEXT, " +
                        "`timerDurationMinutes` INTEGER, " +
                        "`timerStartedAt` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v2 → v3: solo-teaser unlock support (M2). Pure additions again —
         * existing tables untouched. Must match schemas/3.json exactly.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `allowances` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`ruleId` INTEGER NOT NULL, " +
                        "`grantedUntil` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `solo_teaser_state` (" +
                        "`ruleId` INTEGER NOT NULL, " +
                        "`sessionKey` TEXT NOT NULL, " +
                        "`unlockCount` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ruleId`))"
                )
            }
        }
    }
}
