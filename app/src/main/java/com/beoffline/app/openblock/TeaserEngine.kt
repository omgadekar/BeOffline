package com.beoffline.app.openblock

import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.scheduler.RuleScheduler
import java.util.Calendar
import kotlin.random.Random

/**
 * TeaserEngine — pure logic for the solo unlock challenge.
 *
 * DESIGN INTENT: this is FRICTION, not a game. Difficulty must be strictly
 * non-decreasing within a focus session (each unlock harder than the last),
 * and nothing here should feel rewarding.
 */
object TeaserEngine {

    /** Everything the UI needs to run one challenge at a given escalation level. */
    data class Difficulty(
        val level: Int,
        /** Problems that must ALL be answered correctly, in sequence. */
        val problemCount: Int,
        /** Unskippable wait before the first problem appears. */
        val preWaitSeconds: Int,
        /** Whether multiplication problems are mixed in. */
        val useMultiplication: Boolean,
        /** Upper bound (exclusive) for addition/subtraction operands. */
        val addSubOperandBound: Int,
        /** Upper bound (exclusive) for multiplication operands. */
        val multOperandBound: Int
    )

    fun difficultyFor(level: Int): Difficulty {
        val clamped = level.coerceAtLeast(0)
        return Difficulty(
            level = clamped,
            problemCount = (3 + clamped).coerceAtMost(8),
            preWaitSeconds = when (clamped) {
                0 -> 0
                1 -> 15
                2 -> 30
                3 -> 60
                else -> 90
            },
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
            }
        )
    }

    data class Problem(val text: String, val answer: Int)

    /** A fresh set of problems; called again (fresh set) after any wrong answer. */
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
}
