package com.beoffline.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEV TOOL, not an assertion test: seeds the app's REAL database with an
 * active open-block rule so the engine can be exercised end-to-end on an
 * emulator without driving the UI.
 *
 * Run explicitly:
 *   adb shell am instrument -w -e class com.beoffline.app.data.local.E2ESeedTool \
 *     com.beoffline.app.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Uses the app's own Room stack so serialization (JSON package lists) is
 * byte-identical to production writes.
 */
@RunWith(AndroidJUnit4::class)
class E2ESeedTool {

    @Test
    fun seedActiveChromeLock() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, BeOfflineDatabase::class.java, "beoffline.db")
            .addMigrations(BeOfflineDatabase.MIGRATION_1_2)
            .build()
        runBlocking {
            db.openBlockRuleDao().insertRule(
                OpenBlockRule(
                    name = "E2E Test Lock",
                    blockedPackages = listOf("com.android.chrome"),
                    ruleType = RuleType.PERMANENT,
                    isActive = true
                )
            )
        }
        db.close()
    }
}
