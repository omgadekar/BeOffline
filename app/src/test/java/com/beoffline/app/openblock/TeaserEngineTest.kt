package com.beoffline.app.openblock

import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

class TeaserEngineTest {

    // ── Escalation: friction must never decrease within a session ────────────

    @Test
    fun difficulty_isMonotonicallyNonDecreasing_andStrictlyHarderEarly() {
        var previous = TeaserEngine.difficultyFor(0)
        for (level in 1..10) {
            val current = TeaserEngine.difficultyFor(level)
            assertTrue("problemCount decreased at level $level", current.problemCount >= previous.problemCount)
            assertTrue("preWait decreased at level $level", current.preWaitSeconds >= previous.preWaitSeconds)
            assertTrue("operands shrank at level $level", current.addSubOperandBound >= previous.addSubOperandBound)
            previous = current
        }
        // The first several unlocks must be STRICTLY harder each time.
        for (level in 0..3) {
            val a = TeaserEngine.difficultyFor(level)
            val b = TeaserEngine.difficultyFor(level + 1)
            assertTrue(
                "level ${level + 1} not strictly harder",
                b.problemCount > a.problemCount || b.preWaitSeconds > a.preWaitSeconds
            )
        }
    }

    @Test
    fun firstUnlock_hasNoWait_laterUnlocksDo() {
        assertEquals(0, TeaserEngine.difficultyFor(0).preWaitSeconds)
        assertTrue(TeaserEngine.difficultyFor(2).preWaitSeconds >= 30)
    }

    // ── Problem generation ────────────────────────────────────────────────────

    @Test
    fun generatedProblems_matchCount_andAnswersAreCorrect() {
        for (level in 0..5) {
            val difficulty = TeaserEngine.difficultyFor(level)
            val problems = TeaserEngine.generateProblems(difficulty, Random(42))
            assertEquals(difficulty.problemCount, problems.size)
            problems.forEach { p ->
                val parts = p.text.split(" ")
                assertEquals(3, parts.size)
                val a = parts[0].toInt()
                val b = parts[2].toInt()
                val expected = when (parts[1]) {
                    "+" -> a + b
                    "−" -> a - b
                    "×" -> a * b
                    else -> error("unknown operator ${parts[1]}")
                }
                assertEquals("wrong answer for '${p.text}'", expected, p.answer)
                assertTrue("negative answer for '${p.text}'", p.answer >= 0)
            }
        }
    }

    // ── Session keys: escalation resets per focus session ────────────────────

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute, 0) }.timeInMillis

    private fun customDayOf(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    @Test
    fun scheduledRule_sessionKey_isStableWithinWindow_andChangesAcrossOccurrences() {
        val day1Noon = at(2026, 7, 13, 12, 0)
        val rule = OpenBlockRule(
            id = 1, name = "t", blockedPackages = listOf("x"), ruleType = RuleType.SCHEDULED,
            isActive = true,
            startHour = 9, startMinute = 0, endHour = 17, endMinute = 0,
            activeDays = listOf(customDayOf(day1Noon), customDayOf(at(2026, 7, 14, 12, 0)))
        )
        val key1a = TeaserEngine.sessionKeyFor(rule, day1Noon)
        val key1b = TeaserEngine.sessionKeyFor(rule, at(2026, 7, 13, 16, 59))
        val key2 = TeaserEngine.sessionKeyFor(rule, at(2026, 7, 14, 12, 0))

        assertEquals("same window must share a key", key1a, key1b)
        assertNotEquals("next occurrence must reset", key1a, key2)
        assertTrue(key1a.startsWith("window-"))
    }

    @Test
    fun timerRule_sessionKey_followsTimerStart() {
        val rule = OpenBlockRule(
            id = 2, name = "t", blockedPackages = listOf("x"), ruleType = RuleType.TIMER,
            isActive = true, timerDurationMinutes = 30, timerStartedAt = 111L
        )
        assertEquals("timer-111", TeaserEngine.sessionKeyFor(rule, at(2026, 7, 13, 12, 0)))
        assertNotEquals(
            TeaserEngine.sessionKeyFor(rule.copy(timerStartedAt = 222L), at(2026, 7, 13, 12, 0)),
            TeaserEngine.sessionKeyFor(rule, at(2026, 7, 13, 12, 0))
        )
    }

    @Test
    fun permanentRule_sessionKey_resetsDaily() {
        val rule = OpenBlockRule(
            id = 3, name = "t", blockedPackages = listOf("x"), ruleType = RuleType.PERMANENT, isActive = true
        )
        val keyDay1 = TeaserEngine.sessionKeyFor(rule, at(2026, 7, 13, 12, 0))
        val keyDay1Later = TeaserEngine.sessionKeyFor(rule, at(2026, 7, 13, 23, 0))
        val keyDay2 = TeaserEngine.sessionKeyFor(rule, at(2026, 7, 14, 1, 0))
        assertEquals(keyDay1, keyDay1Later)
        assertNotEquals(keyDay1, keyDay2)
    }
}
