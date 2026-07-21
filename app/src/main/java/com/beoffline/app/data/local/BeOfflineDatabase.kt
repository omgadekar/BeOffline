package com.beoffline.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.CachedUnlockRequest
import com.beoffline.app.data.model.ChatMessageCache
import com.beoffline.app.data.model.GroupCache
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.OutboxItem
import com.beoffline.app.data.model.Partner
import com.beoffline.app.data.model.SoloTeaserState

@Database(
    entities = [
        BlockRule::class, OpenBlockRule::class, Allowance::class, SoloTeaserState::class,
        Partner::class, CachedUnlockRequest::class, OutboxItem::class,
        GroupCache::class, ChatMessageCache::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BeOfflineDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun openBlockRuleDao(): OpenBlockRuleDao
    abstract fun allowanceDao(): AllowanceDao
    abstract fun soloTeaserStateDao(): SoloTeaserStateDao
    abstract fun partnerDao(): PartnerDao
    abstract fun unlockRequestCacheDao(): UnlockRequestCacheDao
    abstract fun outboxDao(): OutboxDao
    abstract fun groupCacheDao(): GroupCacheDao
    abstract fun chatMessageDao(): ChatMessageDao

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

        /**
         * v3 → v4: accountability layer local cache + offline outbox (M3).
         * Pure additions; must match schemas/4.json exactly.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `partners` (" +
                        "`pairingId` TEXT NOT NULL, " +
                        "`partnerUid` TEXT NOT NULL, " +
                        "`partnerName` TEXT, " +
                        "`status` TEXT NOT NULL, " +
                        "`canApproveAfterUtc` INTEGER NOT NULL, " +
                        "`removalPending` INTEGER NOT NULL, " +
                        "`removalEffectiveAtUtc` INTEGER, " +
                        "`syncedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`pairingId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `unlock_request_cache` (" +
                        "`id` TEXT NOT NULL, " +
                        "`direction` TEXT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`appLabel` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`requesterName` TEXT, " +
                        "`requestedAtUtc` INTEGER NOT NULL, " +
                        "`expiresAtUtc` INTEGER, " +
                        "`grantedUntilUtc` INTEGER, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`clientKey` TEXT NOT NULL, " +
                        "`payloadJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`attempts` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v4 → v5: groups + chat (M4). Two new cache tables plus three nullable
         * columns on the request cache for group-scoped requests. Additive only;
         * must match schemas/5.json exactly.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `group_cache` (" +
                        "`groupId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`ownerUid` TEXT NOT NULL, " +
                        "`membersJson` TEXT NOT NULL, " +
                        "`syncedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`groupId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` TEXT NOT NULL, " +
                        "`conversationKey` TEXT NOT NULL, " +
                        "`senderUid` TEXT NOT NULL, " +
                        "`senderName` TEXT, " +
                        "`body` TEXT NOT NULL, " +
                        "`sentAtUtc` INTEGER NOT NULL, " +
                        "`pending` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("ALTER TABLE `unlock_request_cache` ADD COLUMN `groupId` TEXT")
                db.execSQL("ALTER TABLE `unlock_request_cache` ADD COLUMN `groupName` TEXT")
                db.execSQL("ALTER TABLE `unlock_request_cache` ADD COLUMN `resolvedByName` TEXT")
            }
        }

        /**
         * v5 → v6: disable-cooldown for App Locks (accountability). Adds one
         * nullable column so turning off an actively-enforcing lock can be
         * delayed and made visible to a partner. Additive; must match schemas/6.json.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `open_block_rules` ADD COLUMN `disableEffectiveAt` INTEGER")
            }
        }

        /**
         * v6 → v7: @-mentions in group chat. One nullable column holding the
         * comma-separated mentioned UIDs. Additive; must match schemas/7.json.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `mentionedUids` TEXT")
            }
        }
    }
}
