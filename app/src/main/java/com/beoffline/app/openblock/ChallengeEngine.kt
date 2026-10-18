package com.beoffline.app.openblock

import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.scheduler.RuleScheduler
import java.util.Calendar
import kotlin.random.Random

/**
 * The four things a locked app can ask of you before it opens.
 *
 * All four are friction, not games: none of them can be rushed, none of them
 * reward you for being good at them, and all four get harder every time you
 * use one inside the same focus session.
 */
enum class ChallengeKind(val displayName: String, val subtitle: String) {
    Arithmetic("Arithmetic", "Solve a set of sums, no calculator"),
    Retype("Retype", "Type a sentence back, character for character"),
    Pattern("Pattern", "Watch a sequence of tiles, then repeat it"),
    Hold("Hold", "Hold a circle down and do nothing else")
}

/**
 * ChallengeEngine — pure logic for the solo unlock challenge. Nothing here
 * touches Android, so the escalation ladder is unit-testable.
 *
 * DESIGN INTENT (unchanged from the arithmetic-only version): difficulty must
 * be strictly non-decreasing within a focus session, and nothing should feel
 * rewarding. Adding three more kinds does not add three more ways to get in
 * cheaply — every kind reads the same [Difficulty], so switching kinds mid-
 * session never buys an easier ride.
 */
object ChallengeEngine {

    /** Everything the UI needs to run one challenge at a given escalation level. */
    data class Difficulty(
        val level: Int,
        /** Unskippable wait before the challenge appears, whatever the kind. */
        val preWaitSeconds: Int,
        // ── Arithmetic ──
        /** Problems that must ALL be answered correctly, in sequence. */
        val problemCount: Int,
        val useMultiplication: Boolean,
        val addSubOperandBound: Int,
        val multOperandBound: Int,
        // ── Retype ──
        /** Sentences that must be typed back exactly, in sequence. */
        val sentenceCount: Int,
        /** Index into the sentence pool where this level starts drawing. */
        val sentenceFloor: Int,
        // ── Pattern ──
        /** Tiles in the sequence to memorise. */
        val patternLength: Int,
        /** How long each tile stays lit while the sequence is played back. */
        val patternStepMillis: Long,
        // ── Hold ──
        val holdSeconds: Int
    )

    fun difficultyFor(level: Int): Difficulty {
        val clamped = level.coerceAtLeast(0)
        return Difficulty(
            level = clamped,
            preWaitSeconds = when (clamped) {
                0 -> 0
                1 -> 15
                2 -> 30
                3 -> 60
                else -> 90
            },
            problemCount = (3 + clamped).coerceAtMost(8),
            useMultiplication = clamped >= 1,
            addSubOperandBound = when {
                clamped <= 1 -> 100
                clamped == 2 -> 500
                else -> 1000
            },
            multOperandBound = when {
                clamped <= 1 -> 13
                clamped == 2 -> 20
                else -> 30
            },
            sentenceCount = (1 + clamped / 2).coerceAtMost(4),
            // Later levels draw from the longer end of the pool.
            sentenceFloor = (clamped / 2).coerceAtMost(SENTENCES.lastIndex),
            patternLength = (3 + clamped).coerceAtMost(9),
            // Each tile flashes for less time the further in you are.
            patternStepMillis = (620L - clamped * 45L).coerceAtLeast(260L),
            holdSeconds = (10 + clamped * 5).coerceAtMost(40)
        )
    }

    /**
     * Which kind to serve, given the kinds the user left switched on.
     *
     * Two things have to be true at once. Within one focus session the choice
     * must be FIXED for a given [level], or you could back out of a challenge
     * you don't like and roll again until an easier kind came up. Across
     * sessions it must VARY, or the kind you meet first is the only kind you
     * ever meet — every session's first unlock is level 0, so keying on the
     * level alone served Arithmetic essentially forever.
     *
     * [sessionKey] gives the second property: it changes when the focus session
     * changes and is stable inside one, so the rotation is deterministic where
     * it matters and different where it should be.
     */
    fun kindFor(enabled: Set<ChallengeKind>, level: Int, sessionKey: String = ""): ChallengeKind {
        val kinds = ChallengeKind.entries.filter { it in enabled }
            .ifEmpty { listOf(ChallengeKind.Arithmetic) }
        // Math.floorMod, so a negative hashCode still lands inside the list.
        val offset = Math.floorMod(sessionKey.hashCode(), kinds.size)
        return kinds[(offset + level.coerceAtLeast(0)) % kinds.size]
    }

    /** A generated challenge, ready for the overlay to run. */
    sealed interface Challenge {
        val kind: ChallengeKind
        /** Steps the progress track draws, and how many must be cleared. */
        val steps: Int

        data class Arithmetic(val problems: List<Problem>) : Challenge {
            override val kind = ChallengeKind.Arithmetic
            override val steps = problems.size
        }

        data class Retype(val sentences: List<String>) : Challenge {
            override val kind = ChallengeKind.Retype
            override val steps = sentences.size
        }

        /** [sequence] holds tile indices 0..8 on a 3×3 grid, played back in order. */
        data class Pattern(val sequence: List<Int>, val stepMillis: Long) : Challenge {
            override val kind = ChallengeKind.Pattern
            override val steps = sequence.size
        }

        data class Hold(val seconds: Int) : Challenge {
            override val kind = ChallengeKind.Hold
            override val steps = 1
        }
    }

    data class Problem(val text: String, val answer: Int)

    /**
     * A fresh challenge of [kind]. Called again from scratch after any mistake —
     * partial progress is never banked, for any kind.
     */
    fun generate(
        kind: ChallengeKind,
        difficulty: Difficulty,
        random: Random = Random.Default
    ): Challenge = when (kind) {
        ChallengeKind.Arithmetic -> Challenge.Arithmetic(generateProblems(difficulty, random))
        ChallengeKind.Retype -> Challenge.Retype(generateSentences(difficulty, random))
        ChallengeKind.Pattern -> Challenge.Pattern(
            sequence = generatePattern(difficulty, random),
            stepMillis = difficulty.patternStepMillis
        )
        ChallengeKind.Hold -> Challenge.Hold(difficulty.holdSeconds)
    }

    fun generateProblems(difficulty: Difficulty, random: Random = Random.Default): List<Problem> {
        return List(difficulty.problemCount) { index ->
            val useMult = difficulty.useMultiplication && index % 2 == 1
            if (useMult) {
                val a = random.nextInt(2, difficulty.multOperandBound)
                val b = random.nextInt(2, difficulty.multOperandBound)
                Problem("$a × $b", a * b)
            } else {
                val a = random.nextInt(10, difficulty.addSubOperandBound)
                val b = random.nextInt(10, difficulty.addSubOperandBound)
                if (random.nextBoolean() && a > b) {
                    Problem("$a − $b", a - b)
                } else {
                    Problem("$a + $b", a + b)
                }
            }
        }
    }

    /**
     * Sentences the user has to type back. They are deliberately statements
     * about the choice being made, not slogans — reading one is part of the
     * friction.
     */
    fun generateSentences(difficulty: Difficulty, random: Random = Random.Default): List<String> {
        val pool = SENTENCES.drop(difficulty.sentenceFloor).ifEmpty { SENTENCES }
        val shuffled = pool.shuffled(random)
        return List(difficulty.sentenceCount) { index -> shuffled[index % shuffled.size] }
    }

    /** Tile indices on a 3×3 grid, never repeating the same tile twice in a row. */
    fun generatePattern(difficulty: Difficulty, random: Random = Random.Default): List<Int> {
        val sequence = ArrayList<Int>(difficulty.patternLength)
        while (sequence.size < difficulty.patternLength) {
            val next = random.nextInt(9)
            if (sequence.lastOrNull() != next) sequence.add(next)
        }
        return sequence
    }

    /**
     * Identity of the CURRENT focus session for a rule — escalation resets
     * when this changes (the user's "per focus session" decision):
     *   SCHEDULED → start of the current window occurrence
     *   TIMER     → timerStartedAt
     *   PERMANENT → local calendar day (resets daily)
     */
    fun sessionKeyFor(rule: OpenBlockRule, nowMillis: Long = System.currentTimeMillis()): String {
        return when (rule.ruleType) {
            RuleType.SCHEDULED ->
                RuleScheduler.currentWindowStartMillis(rule, nowMillis)?.let { "window-$it" }
                    ?: dayStamp(nowMillis)
            RuleType.TIMER ->
                rule.timerStartedAt?.let { "timer-$it" } ?: dayStamp(nowMillis)
            RuleType.PERMANENT -> dayStamp(nowMillis)
        }
    }

    private fun dayStamp(nowMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return "day-%04d%02d%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // Ordered shortest to longest — later levels draw from further down.
    private val SENTENCES = listOf(
        "I chose to put this app away for a reason.",
        "The feed will still be there when the window ends.",
        "Nothing in here needs me in the next ten minutes.",
        "I am opening this out of habit, not because I decided to.",
        "If this were actually urgent I would not be reading a sentence about it."
    )
}
