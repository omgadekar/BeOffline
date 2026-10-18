package com.beoffline.app.openblock

import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

class ChallengeEngineTest {

    // ── Escalation: friction must never decrease within a session ────────────

    @Test
    fun difficulty_isMonotonicallyNonDecreasing_andStrictlyHarderEarly() {
        var previous = ChallengeEngine.difficultyFor(0)
        for (level in 1..10) {
            val current = ChallengeEngine.difficultyFor(level)
            assertTrue("problemCount decreased at level $level", current.problemCount >= previous.problemCount)
            assertTrue("preWait decreased at level $level", current.preWaitSeconds >= previous.preWaitSeconds)
            assertTrue("operands shrank at level $level", current.addSubOperandBound >= previous.addSubOperandBound)
            previous = current
        }
        // The first several unlocks must be STRICTLY harder each time.
        for (level in 0..3) {
            val a = ChallengeEngine.difficultyFor(level)
            val b = ChallengeEngine.difficultyFor(level + 1)
            assertTrue(
                "level ${level + 1} not strictly harder",
                b.problemCount > a.problemCount || b.preWaitSeconds > a.preWaitSeconds
            )
        }
    }

    @Test
    fun firstUnlock_hasNoWait_laterUnlocksDo() {
        assertEquals(0, ChallengeEngine.difficultyFor(0).preWaitSeconds)
        assertTrue(ChallengeEngine.difficultyFor(2).preWaitSeconds >= 30)
    }

    // ── Problem generation ────────────────────────────────────────────────────

    @Test
    fun generatedProblems_matchCount_andAnswersAreCorrect() {
        for (level in 0..5) {
            val difficulty = ChallengeEngine.difficultyFor(level)
            val problems = ChallengeEngine.generateProblems(difficulty, Random(42))
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

    // ── The other three kinds ────────────────────────────────────────────────

    @Test
    fun everyKind_getsHarder_asTheLevelClimbs() {
        var previous = ChallengeEngine.difficultyFor(0)
        for (level in 1..10) {
            val current = ChallengeEngine.difficultyFor(level)
            assertTrue("sentences dropped at $level", current.sentenceCount >= previous.sentenceCount)
            assertTrue("pattern shortened at $level", current.patternLength >= previous.patternLength)
            assertTrue("pattern flash lengthened at $level", current.patternStepMillis <= previous.patternStepMillis)
            assertTrue("hold shortened at $level", current.holdSeconds >= previous.holdSeconds)
            previous = current
        }
    }

    @Test
    fun generatedChallenge_hasStepsMatchingItsDifficulty() {
        val difficulty = ChallengeEngine.difficultyFor(3)

        val arithmetic = ChallengeEngine.generate(ChallengeKind.Arithmetic, difficulty, Random(1))
        assertEquals(difficulty.problemCount, arithmetic.steps)

        val retype = ChallengeEngine.generate(ChallengeKind.Retype, difficulty, Random(1))
        assertEquals(difficulty.sentenceCount, retype.steps)

        val pattern = ChallengeEngine.generate(ChallengeKind.Pattern, difficulty, Random(1))
        assertEquals(difficulty.patternLength, pattern.steps)

        // Hold is one long step, not many short ones.
        val hold = ChallengeEngine.generate(ChallengeKind.Hold, difficulty, Random(1))
        assertEquals(1, hold.steps)
        assertEquals(difficulty.holdSeconds, (hold as ChallengeEngine.Challenge.Hold).seconds)
    }

    @Test
    fun pattern_staysOnTheGrid_andNeverRepeatsATileBackToBack() {
        for (level in 0..8) {
            val difficulty = ChallengeEngine.difficultyFor(level)
            val sequence = ChallengeEngine.generatePattern(difficulty, Random(level))
            assertEquals(difficulty.patternLength, sequence.size)
            sequence.forEach { assertTrue("tile $it off the 3x3 grid", it in 0..8) }
            sequence.zipWithNext().forEach { (a, b) ->
                assertNotEquals("a repeated tile can't be tapped twice", a, b)
            }
        }
    }

    @Test
    fun retype_sentencesAreNonEmpty_andCountMatches() {
        for (level in 0..8) {
            val difficulty = ChallengeEngine.difficultyFor(level)
            val sentences = ChallengeEngine.generateSentences(difficulty, Random(level))
            assertEquals(difficulty.sentenceCount, sentences.size)
            sentences.forEach { assertTrue("empty sentence", it.isNotBlank()) }
        }
    }

    // ── Kind selection ───────────────────────────────────────────────────────

    @Test
    fun kindRotation_onlyEverServesAnEnabledKind() {
        val enabled = setOf(ChallengeKind.Retype, ChallengeKind.Hold)
        val served = (0..12)
            .flatMap { level -> listOf("timer-1", "timer-2", "day-1").map { ChallengeEngine.kindFor(enabled, level, it) } }
            .toSet()
        assertEquals(enabled, served)
    }

    @Test
    fun kindRotation_isFixedWithinASession_soBackingOutCannotReroll() {
        val enabled = ChallengeKind.entries.toSet()
        for (level in 0..8) {
            assertEquals(
                ChallengeEngine.kindFor(enabled, level, "timer-4242"),
                ChallengeEngine.kindFor(enabled, level, "timer-4242")
            )
        }
    }

    @Test
    fun kindRotation_variesAcrossSessions_notJustAcrossLevels() {
        // The bug this guards: every focus session's FIRST unlock is level 0, so
        // keying the rotation on the level alone served the same kind — in
        // practice, Arithmetic — essentially every time.
        val enabled = ChallengeKind.entries.toSet()
        val firstUnlockKinds = (1..40)
            .map { ChallengeEngine.kindFor(enabled, level = 0, sessionKey = "timer-$it") }
            .toSet()

        // Not just "more than one" — over a run of sessions every enabled kind
        // should turn up, otherwise a kind the user switched on is dead weight.
        assertEquals(
            "level 0 must reach every enabled kind across sessions",
            enabled, firstUnlockKinds
        )
    }

    @Test
    fun noEnabledKinds_stillProducesAChallenge() {
        // An empty set would otherwise mean a locked app opens for free.
        assertEquals(ChallengeKind.Arithmetic, ChallengeEngine.kindFor(emptySet(), 0, "timer-1"))
    }

    @Test
    fun storedKinds_roundTrip_andEmptyOrJunkFallsBackToAll() {
        val kinds = setOf(ChallengeKind.Pattern, ChallengeKind.Hold)
        assertEquals(kinds, OpenBlockPrefs.parseKinds(OpenBlockPrefs.storeKinds(kinds)))

        val all = ChallengeKind.entries.toSet()
        assertEquals(all, OpenBlockPrefs.parseKinds(null))
        assertEquals(all, OpenBlockPrefs.parseKinds(""))
        assertEquals(all, OpenBlockPrefs.parseKinds("Sudoku,Wordle"))
        assertEquals(all, OpenBlockPrefs.storeKinds(emptySet()).let(OpenBlockPrefs::parseKinds))
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
        val key1a = ChallengeEngine.sessionKeyFor(rule, day1Noon)
        val key1b = ChallengeEngine.sessionKeyFor(rule, at(2026, 7, 13, 16, 59))
        val key2 = ChallengeEngine.sessionKeyFor(rule, at(2026, 7, 14, 12, 0))

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
        assertEquals("timer-111", ChallengeEngine.sessionKeyFor(rule, at(2026, 7, 13, 12, 0)))
        assertNotEquals(
            ChallengeEngine.sessionKeyFor(rule.copy(timerStartedAt = 222L), at(2026, 7, 13, 12, 0)),
            ChallengeEngine.sessionKeyFor(rule, at(2026, 7, 13, 12, 0))
        )
    }

    @Test
    fun permanentRule_sessionKey_resetsDaily() {
        val rule = OpenBlockRule(
            id = 3, name = "t", blockedPackages = listOf("x"), ruleType = RuleType.PERMANENT, isActive = true
        )
        val keyDay1 = ChallengeEngine.sessionKeyFor(rule, at(2026, 7, 13, 12, 0))
        val keyDay1Later = ChallengeEngine.sessionKeyFor(rule, at(2026, 7, 13, 23, 0))
        val keyDay2 = ChallengeEngine.sessionKeyFor(rule, at(2026, 7, 14, 1, 0))
        assertEquals(keyDay1, keyDay1Later)
        assertNotEquals(keyDay1, keyDay2)
    }
}
