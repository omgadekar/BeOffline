package com.beoffline.app.scheduler

import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Locks the behavior of the shared ScheduledWindow evaluation across the
 * generalization refactor: both rule types must evaluate windows identically,
 * including midnight-crossing windows and activeDays gating.
 *
 * All times are built with java.util.Calendar in the JVM's local timezone —
 * the same clock the production code uses.
 */
class RuleSchedulerWindowTest {

    /** Epoch millis for the given local date/time. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    /** The app's 1(Mon)..7(Sun) day number for the given local date. */
    private fun customDayOf(year: Int, month: Int, day: Int): Int {
        val cal = Calendar.getInstance().apply { clear(); set(year, month - 1, day) }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    private fun blockRule(
        startHour: Int?, startMinute: Int?, endHour: Int?, endMinute: Int?,
        activeDays: List<Int>?
    ) = BlockRule(
        name = "test", blockedPackages = emptyList(), ruleType = RuleType.SCHEDULED,
        startHour = startHour, startMinute = startMinute,
        endHour = endHour, endMinute = endMinute, activeDays = activeDays
    )

    private fun openBlockRule(
        startHour: Int?, startMinute: Int?, endHour: Int?, endMinute: Int?,
        activeDays: List<Int>?
    ) = OpenBlockRule(
        name = "test", blockedPackages = emptyList(), ruleType = RuleType.SCHEDULED,
        startHour = startHour, startMinute = startMinute,
        endHour = endHour, endMinute = endMinute, activeDays = activeDays
    )

    // ── Simple same-day window ────────────────────────────────────────────────

    @Test
    fun insideSimpleWindow_isTrue() {
        val day = customDayOf(2026, 7, 13)
        val rule = blockRule(9, 0, 12, 30, listOf(day))
        assertTrue(RuleScheduler.isWithinScheduledWindow(rule, at(2026, 7, 13, 10, 0)))
    }

    @Test
    fun beforeWindow_isFalse() {
        val day = customDayOf(2026, 7, 13)
        val rule = blockRule(9, 0, 12, 30, listOf(day))
        assertFalse(RuleScheduler.isWithinScheduledWindow(rule, at(2026, 7, 13, 8, 59)))
    }

    @Test
    fun afterWindow_isFalse() {
        val day = customDayOf(2026, 7, 13)
        val rule = blockRule(9, 0, 12, 30, listOf(day))
        assertFalse(RuleScheduler.isWithinScheduledWindow(rule, at(2026, 7, 13, 12, 30)))
    }

    // ── Midnight-crossing window (the open-block "social 10pm–7am" case) ─────

    @Test
    fun midnightCrossing_beforeMidnight_isTrue() {
        val day = customDayOf(2026, 7, 13)
        val rule = blockRule(22, 0, 7, 0, listOf(day))
        assertTrue(RuleScheduler.isWithinScheduledWindow(rule, at(2026, 7, 13, 23, 30)))
    }

    @Test
    fun midnightCrossing_afterMidnight_isTrue() {
        // Window starts on day X at 22:00; at X+1 06:00 it is still active.
        val startDay = customDayOf(2026, 7, 13)
        val rule = blockRule(22, 0, 7, 0, listOf(startDay))
        assertTrue(RuleScheduler.isWithinScheduledWindow(rule, at(2026, 7, 14, 6, 0)))
    }

    @Test
    fun midnightCrossing_afterEnd_isFalse() {
        val startDay = customDayOf(2026, 7, 13)
        val rule = blockRule(22, 0, 7, 0, listOf(startDay))
        assertFalse(RuleScheduler.isWithinScheduledWindow(rule, at(2026, 7, 14, 7, 1)))
    }

    // ── activeDays gating ─────────────────────────────────────────────────────

    @Test
    fun dayNotInActiveDays_isFalse() {
        val day = customDayOf(2026, 7, 13)
        val otherDay = if (day == 7) 1 else day + 1
        val rule = blockRule(9, 0, 12, 30, listOf(otherDay))
        assertFalse(RuleScheduler.isWithinScheduledWindow(rule, at(2026, 7, 13, 10, 0)))
    }

    @Test
    fun emptyActiveDays_meansEveryDay() {
        val rule = blockRule(9, 0, 12, 30, emptyList())
        assertTrue(RuleScheduler.isWithinScheduledWindow(rule, at(2026, 7, 13, 10, 0)))
    }

    @Test
    fun nullWindowFields_isFalse() {
        val rule = blockRule(null, null, null, null, null)
        assertFalse(RuleScheduler.isWithinScheduledWindow(rule, at(2026, 7, 13, 10, 0)))
    }

    // ── Both rule types share identical evaluation ────────────────────────────

    @Test
    fun openBlockRule_evaluatesIdenticallyToBlockRule() {
        val day = customDayOf(2026, 7, 13)
        val cases = listOf(
            at(2026, 7, 13, 23, 30),
            at(2026, 7, 14, 6, 0),
            at(2026, 7, 14, 7, 1),
            at(2026, 7, 13, 12, 0)
        )
        val block = blockRule(22, 0, 7, 0, listOf(day))
        val open = openBlockRule(22, 0, 7, 0, listOf(day))
        for (now in cases) {
            assertTrue(
                "Divergence at $now",
                RuleScheduler.isWithinScheduledWindow(block, now) ==
                    RuleScheduler.isWithinScheduledWindow(open, now)
            )
        }
    }
}
