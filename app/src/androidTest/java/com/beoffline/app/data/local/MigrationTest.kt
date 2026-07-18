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
}
