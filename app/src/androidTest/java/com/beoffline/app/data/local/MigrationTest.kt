package com.beoffline.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the v1 → v2 migration on a REAL v1 database file:
 *  - existing block_rules rows (the shipped internet-block feature) survive untouched
 *  - the new open_block_rules table is created exactly as Room expects
 *    (runMigrationsAndValidate diffs the migrated DB against schemas/2.json)
 *
 * This is the safety gate for shipping M0 to the published user base.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BeOfflineDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesBlockRules_andCreatesOpenBlockTable() {
        // Seed a realistic v1 database (as shipped in production).
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO block_rules " +
                    "(name, blockedPackages, ruleType, isActive, startHour, startMinute, " +
                    "endHour, endMinute, activeDays, timerDurationMinutes, timerStartedAt, createdAt) " +
                    "VALUES ('Morning Focus', '[\"com.instagram.android\",\"com.whatsapp\"]', " +
                    "'SCHEDULED', 1, 9, 0, 12, 30, '[1,2,3,4,5]', NULL, NULL, 1721000000000)"
            )
            execSQL(
                "INSERT INTO block_rules " +
                    "(name, blockedPackages, ruleType, isActive, startHour, startMinute, " +
                    "endHour, endMinute, activeDays, timerDurationMinutes, timerStartedAt, createdAt) " +
                    "VALUES ('Always Off', '[\"com.zhiliaoapp.musically\"]', " +
                    "'PERMANENT', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1721000000001)"
            )
            close()
        }

        // Run the real production migration and validate the result against 2.json.
        val db = helper.runMigrationsAndValidate(
            dbName, 2, true, BeOfflineDatabase.MIGRATION_1_2
        )

        // Existing internet-block rules survived byte-for-byte.
        db.query("SELECT name, blockedPackages, ruleType, isActive, startHour, endMinute, activeDays FROM block_rules ORDER BY createdAt").use { c ->
            assertEquals(2, c.count)

            assertTrue(c.moveToFirst())
            assertEquals("Morning Focus", c.getString(0))
            assertEquals("[\"com.instagram.android\",\"com.whatsapp\"]", c.getString(1))
            assertEquals("SCHEDULED", c.getString(2))
            assertEquals(1, c.getInt(3))
            assertEquals(9, c.getInt(4))
            assertEquals(30, c.getInt(5))
            assertEquals("[1,2,3,4,5]", c.getString(6))

            assertTrue(c.moveToNext())
            assertEquals("Always Off", c.getString(0))
            assertEquals("PERMANENT", c.getString(2))
            assertEquals(0, c.getInt(3))
            assertTrue(c.isNull(4))
        }

        // The new open-block table exists, starts empty, and is writable/readable.
        db.query("SELECT COUNT(*) FROM open_block_rules").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.execSQL(
            "INSERT INTO open_block_rules (name, blockedPackages, ruleType, isActive, createdAt) " +
                "VALUES ('Deep Work', '[\"com.twitter.android\"]', 'PERMANENT', 0, 1721000000002)"
        )
        db.query("SELECT name, blockedPackages FROM open_block_rules").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Deep Work", c.getString(0))
            assertEquals("[\"com.twitter.android\"]", c.getString(1))
        }
    }

    @Test
    fun migrate1To3_fullChain_preservesRules_andCreatesM2Tables() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO block_rules " +
                    "(name, blockedPackages, ruleType, isActive, startHour, startMinute, " +
                    "endHour, endMinute, activeDays, timerDurationMinutes, timerStartedAt, createdAt) " +
                    "VALUES ('Survivor', '[\"com.whatsapp\"]', 'PERMANENT', 1, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1721000000000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName, 3, true,
            BeOfflineDatabase.MIGRATION_1_2, BeOfflineDatabase.MIGRATION_2_3
        )

        db.query("SELECT name FROM block_rules").use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("Survivor", c.getString(0))
        }

        // M2 tables exist and are writable.
        db.execSQL(
            "INSERT INTO allowances (packageName, ruleId, grantedUntil, source, createdAt) " +
                "VALUES ('com.android.chrome', 1, 1721000300000, 'TEASER', 1721000000000)"
        )
        db.execSQL(
            "INSERT INTO solo_teaser_state (ruleId, sessionKey, unlockCount, updatedAt) " +
                "VALUES (1, 'day-20260718', 2, 1721000000000)"
        )
        db.query("SELECT COUNT(*) FROM allowances").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        db.query("SELECT unlockCount FROM solo_teaser_state WHERE ruleId = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(2, c.getInt(0))
        }
    }

    @Test
    fun migrate1To6_fullChain_preservesRules_andCreatesM3M4Tables() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO block_rules " +
                    "(name, blockedPackages, ruleType, isActive, startHour, startMinute, " +
                    "endHour, endMinute, activeDays, timerDurationMinutes, timerStartedAt, createdAt) " +
                    "VALUES ('Survivor', '[\"com.whatsapp\"]', 'PERMANENT', 1, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1721000000000)"
            )
            close()
        }

        // The exact chain a v1 production install walks on upgrade to this build
        // (runMigrationsAndValidate diffs the end state against schemas/6.json —
        // including the M4 columns on unlock_request_cache and the v6
        // disableEffectiveAt column on open_block_rules).
        val db = helper.runMigrationsAndValidate(
            dbName, 6, true,
            BeOfflineDatabase.MIGRATION_1_2, BeOfflineDatabase.MIGRATION_2_3,
            BeOfflineDatabase.MIGRATION_3_4, BeOfflineDatabase.MIGRATION_4_5,
            BeOfflineDatabase.MIGRATION_5_6
        )

        db.query("SELECT name FROM block_rules").use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("Survivor", c.getString(0))
        }

        // M3 + M4 tables exist and are writable.
        db.execSQL(
            "INSERT INTO unlock_request_cache " +
                "(id, direction, packageName, appLabel, status, requesterName, requestedAtUtc, " +
                "expiresAtUtc, grantedUntilUtc, groupId, groupName, resolvedByName) " +
                "VALUES ('r1', 'OUTGOING', 'com.whatsapp', 'WhatsApp', 'Approved', NULL, " +
                "1721000000000, 1721000900000, 1721001800000, 'g1', 'Focus crew', 'Sam')"
        )
        db.execSQL(
            "INSERT INTO group_cache (groupId, name, ownerUid, membersJson, syncedAt) " +
                "VALUES ('g1', 'Focus crew', 'uid1', '[]', 1721000000000)"
        )
        db.execSQL(
            "INSERT INTO chat_messages (id, conversationKey, senderUid, senderName, body, sentAtUtc, pending) " +
                "VALUES ('m1', 'group:g1', 'uid1', 'Sam', 'hello', 1721000000000, 0)"
        )
        db.query("SELECT groupName, resolvedByName FROM unlock_request_cache WHERE id = 'r1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Focus crew", c.getString(0))
            assertEquals("Sam", c.getString(1))
        }
        db.query("SELECT body FROM chat_messages WHERE conversationKey = 'group:g1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("hello", c.getString(0))
        }

        // v6: the disable-cooldown column exists on open_block_rules and round-trips.
        db.execSQL(
            "INSERT INTO open_block_rules (name, blockedPackages, ruleType, isActive, disableEffectiveAt, createdAt) " +
                "VALUES ('Deep Work', '[\"com.instagram.android\"]', 'PERMANENT', 1, 1721003600000, 1721000000002)"
        )
        db.query("SELECT disableEffectiveAt FROM open_block_rules WHERE name = 'Deep Work'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1721003600000L, c.getLong(0))
        }
    }
}
