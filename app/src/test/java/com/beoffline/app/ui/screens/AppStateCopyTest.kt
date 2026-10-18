package com.beoffline.app.ui.screens

import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * The redesign's whole claim is that a person can read a row and know what
 * their phone is doing without being taught anything. That claim lives in
 * these strings, so they are pinned here rather than left to be eyeballed on
 * a device.
 *
 * All times are built with java.util.Calendar in the JVM's local timezone —
 * the same clock the production code uses.
 */
class AppStateCopyTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    /** The app's 1(Mon)..7(Sun) day number for the given local date. */
    private fun customDayOf(year: Int, month: Int, day: Int): Int {
        val cal = Calendar.getInstance().apply { clear(); set(year, month - 1, day) }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    private fun window(
        startHour: Int? = null,
        startMinute: Int? = null,
        endHour: Int? = null,
        endMinute: Int? = null,
        activeDays: List<Int>? = null
    ) = OpenBlockRule(
        name = "w",
        blockedPackages = listOf("com.example"),
        ruleType = RuleType.SCHEDULED,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        activeDays = activeDays
    )

    // An allowance outranks the rule ─────────────────────────────────────────

    @Test
    fun `allowance from a person names them and counts down`() {
        val now = at(2026, 6, 10, 14, 0)
        val state = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.PERMANENT,
            window = window(),
            isActive = true,
            allowanceUntil = now + 4 * 60_000 + 2_000,
            allowanceGrantedBy = "Priya",
            now = now
        )
        assertEquals(AppStatus.BorrowedTime, state.status)
        assertEquals("Open for 5 more minutes - Priya said yes", state.sentence)
        assertEquals("4:02", state.trailing)
    }

    @Test
    fun `an earned allowance says so without naming anyone`() {
        val now = at(2026, 6, 10, 14, 0)
        val state = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.PERMANENT,
            window = window(),
            isActive = true,
            allowanceUntil = now + 60_000,
            allowanceGrantedBy = null,
            now = now
        )
        assertEquals("Open for 1 more minute - you earned it", state.sentence)
    }

    @Test
    fun `an expired allowance falls back to the rule`() {
        val now = at(2026, 6, 10, 14, 0)
        val state = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.PERMANENT,
            window = window(),
            isActive = true,
            allowanceUntil = now - 1,
            allowanceGrantedBy = "Priya",
            now = now
        )
        assertEquals(AppStatus.BlockedNow, state.status)
        assertEquals("Will not open until you turn it off", state.sentence)
    }

    // The two strictness levels keep their own verb ──────────────────────────

    @Test
    fun `offline and locked read differently for the same rule`() {
        val now = at(2026, 6, 10, 14, 0)
        val locked = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.PERMANENT,
            window = window(),
            isActive = true,
            now = now
        )
        val offline = AppStateCopy.describe(
            strictness = Strictness.Offline,
            ruleType = RuleType.PERMANENT,
            window = window(),
            isActive = true,
            now = now
        )
        assertEquals("Will not open until you turn it off", locked.sentence)
        assertEquals("Nothing loads until you turn it off", offline.sentence)
    }

    // Schedules ──────────────────────────────────────────────────────────────

    @Test
    fun `inside its window a row says when it lifts`() {
        val now = at(2026, 6, 10, 14, 0)
        val today = customDayOf(2026, 6, 10)
        val state = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.SCHEDULED,
            window = window(9, 0, 17, 0, listOf(today)),
            isActive = true,
            now = now
        )
        assertEquals(AppStatus.BlockedNow, state.status)
        assertEquals("Will not open until 5:00 PM", state.sentence)
    }

    @Test
    fun `outside its window a row says when it starts`() {
        val now = at(2026, 6, 10, 20, 0)
        val today = customDayOf(2026, 6, 10)
        val state = AppStateCopy.describe(
            strictness = Strictness.Offline,
            ruleType = RuleType.SCHEDULED,
            window = window(9, 0, 17, 0, listOf(today)),
            isActive = true,
            now = now
        )
        assertEquals(AppStatus.Later, state.status)
        assertEquals("Nothing loads - ${AppStateCopy.days(listOf(today))}, 9:00 AM to 5:00 PM", state.sentence)
    }

    // Timers ─────────────────────────────────────────────────────────────────

    @Test
    fun `a running timer says how much is left`() {
        val now = at(2026, 6, 10, 14, 0)
        val state = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.TIMER,
            window = window(),
            isActive = true,
            timerDurationMinutes = 90,
            timerStartedAt = now - 43 * 60_000,
            now = now
        )
        assertEquals(AppStatus.BlockedNow, state.status)
        assertEquals("Will not open for another 47 min", state.sentence)
        assertEquals("47:00", state.trailing)
    }

    @Test
    fun `a long timer is spoken in hours`() {
        val now = at(2026, 6, 10, 14, 0)
        val state = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.TIMER,
            window = window(),
            isActive = true,
            timerDurationMinutes = 120,
            timerStartedAt = now - 48 * 60_000,
            now = now
        )
        assertEquals("Will not open for another 1h 12m", state.sentence)
    }

    @Test
    fun `an elapsed timer reads as paused, not as blocking`() {
        val now = at(2026, 6, 10, 14, 0)
        val state = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.TIMER,
            window = window(),
            isActive = true,
            timerDurationMinutes = 30,
            timerStartedAt = now - 31 * 60_000,
            now = now
        )
        assertEquals(AppStatus.Paused, state.status)
    }

    @Test
    fun `a timer that was never started reads as paused`() {
        val now = at(2026, 6, 10, 14, 0)
        val state = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.TIMER,
            window = window(),
            isActive = true,
            timerDurationMinutes = 30,
            timerStartedAt = null,
            now = now
        )
        assertEquals(AppStatus.Paused, state.status)
    }

    // A switched-off rule ────────────────────────────────────────────────────

    @Test
    fun `an inactive rule never claims to be blocking`() {
        val now = at(2026, 6, 10, 14, 0)
        val today = customDayOf(2026, 6, 10)
        val state = AppStateCopy.describe(
            strictness = Strictness.Locked,
            ruleType = RuleType.SCHEDULED,
            window = window(9, 0, 17, 0, listOf(today)),
            isActive = false,
            now = now
        )
        assertEquals(AppStatus.Paused, state.status)
        assertEquals("Paused - not blocking right now", state.sentence)
    }

    // Formatting helpers ─────────────────────────────────────────────────────

    @Test
    fun `clock reads midnight and noon the way a person would`() {
        assertEquals("12:00 AM", AppStateCopy.clock(0, 0))
        assertEquals("12:30 PM", AppStateCopy.clock(12, 30))
        assertEquals("1:05 PM", AppStateCopy.clock(13, 5))
        assertEquals("--:--", AppStateCopy.clock(null, 0))
    }

    @Test
    fun `day sets collapse to the phrase people use`() {
        assertEquals("Every day", AppStateCopy.days(null))
        assertEquals("Every day", AppStateCopy.days(emptyList()))
        assertEquals("Every day", AppStateCopy.days(listOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals("Mon-Fri", AppStateCopy.days(listOf(5, 1, 2, 3, 4)))
        assertEquals("Weekends", AppStateCopy.days(listOf(7, 6)))
        assertEquals("Mon, Wed, Fri", AppStateCopy.days(listOf(5, 1, 3)))
    }

    @Test
    fun `the creator summary names the apps it will act on`() {
        assertEquals(
            "Instagram and Reddit will not open on weekdays, 9 to 5.",
            AppStateCopy.summarise(listOf("Instagram", "Reddit"), Strictness.Locked, "on weekdays, 9 to 5")
        )
        assertEquals(
            "Instagram, Reddit and 2 more will open but stay empty at all times.",
            AppStateCopy.summarise(
                listOf("Instagram", "Reddit", "X", "TikTok"),
                Strictness.Offline,
                "at all times"
            )
        )
    }
}
