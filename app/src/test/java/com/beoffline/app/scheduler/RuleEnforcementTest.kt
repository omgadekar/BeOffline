package com.beoffline.app.scheduler

import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.openblock.OpenBlockController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * What the two engines should be holding at a given instant.
 *
 * `isActive` on a rule means ARMED, not ENFORCING. The bug this locks down:
 * a block edited from "always" to 9am-6pm at 7pm stayed armed — correctly, so
 * the list could say when it starts — and every path that rebuilt the VPN
 * tunnel read "armed" as "block it now", so the app stayed offline right
 * through the evening and all of the next day.
 */
class RuleEnforcementTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun scheduled(packages: List<String>, active: Boolean = true) = BlockRule(
        id = 1,
        name = "test",
        blockedPackages = packages,
        ruleType = RuleType.SCHEDULED,
        isActive = active,
        startHour = 9, startMinute = 0,
        endHour = 18, endMinute = 0,
        activeDays = listOf(1, 2, 3, 4, 5, 6, 7)
    )

    // ── The reported bug: always -> scheduled, saved outside the window ───────

    @Test
    fun armedScheduledRule_outsideItsWindow_isNotEnforcing() {
        val rule = scheduled(listOf("com.instagram.android"))
        assertFalse(RuleScheduler.isEnforcingNow(rule, at(2026, 10, 21, 19, 0)))
    }

    @Test
    fun armedScheduledRule_outsideItsWindow_staysOutOfTheTunnel() {
        val rule = scheduled(listOf("com.instagram.android"))
        assertEquals(
            emptyList<String>(),
            RuleScheduler.enforcedPackages(listOf(rule), at(2026, 10, 21, 19, 0))
        )
    }

    @Test
    fun armedScheduledRule_insideItsWindow_isInTheTunnel() {
        val rule = scheduled(listOf("com.instagram.android"))
        assertEquals(
            listOf("com.instagram.android"),
            RuleScheduler.enforcedPackages(listOf(rule), at(2026, 10, 21, 11, 30))
        )
    }

    @Test
    fun enforcedPackages_mixesEngagedRulesOnly() {
        val now = at(2026, 10, 21, 19, 0)
        val permanent = BlockRule(
            id = 2, name = "always", blockedPackages = listOf("com.reddit.frontpage"),
            ruleType = RuleType.PERMANENT, isActive = true
        )
        val enforced = RuleScheduler.enforcedPackages(
            listOf(scheduled(listOf("com.instagram.android")), permanent),
            now
        )
        assertEquals(listOf("com.reddit.frontpage"), enforced)
    }

    @Test
    fun pausedRule_isNeverEnforcing() {
        val rule = scheduled(listOf("com.instagram.android"), active = false)
        assertFalse(RuleScheduler.isEnforcingNow(rule, at(2026, 10, 21, 11, 30)))
    }

    // ── Timers outliving their own clock ─────────────────────────────────────

    @Test
    fun timerRule_pastItsDuration_isNotEnforcing() {
        // Doze can swallow the stop alarm. The rule's own clock is the truth.
        val startedAt = at(2026, 10, 21, 9, 0)
        val rule = BlockRule(
            id = 3, name = "focus", blockedPackages = listOf("com.instagram.android"),
            ruleType = RuleType.TIMER, isActive = true,
            timerDurationMinutes = 60, timerStartedAt = startedAt
        )
        assertTrue(RuleScheduler.isEnforcingNow(rule, startedAt + 59 * 60_000L))
        assertFalse(RuleScheduler.isEnforcingNow(rule, startedAt + 61 * 60_000L))
    }

    @Test
    fun timerRule_withNoStart_isNotEnforcing() {
        val rule = BlockRule(
            id = 4, name = "focus", blockedPackages = listOf("com.instagram.android"),
            ruleType = RuleType.TIMER, isActive = true, timerDurationMinutes = 60
        )
        assertFalse(RuleScheduler.isEnforcingNow(rule, at(2026, 10, 21, 11, 0)))
    }

    // ── The lock engine answers the same questions the same way ──────────────

    @Test
    fun lockEngine_agreesWithTheInternetEngine_onWindows() {
        val now = at(2026, 10, 21, 19, 0)
        val lock = OpenBlockRule(
            id = 1, name = "test", blockedPackages = listOf("com.instagram.android"),
            ruleType = RuleType.SCHEDULED, isActive = true,
            startHour = 9, startMinute = 0, endHour = 18, endMinute = 0,
            activeDays = listOf(1, 2, 3, 4, 5, 6, 7)
        )
        assertFalse(OpenBlockController.isEnforcingNow(lock, now))
        assertTrue(OpenBlockController.enforcedPackages(listOf(lock), emptyList(), now).isEmpty())
    }

    @Test
    fun lockEngine_timerPastItsDuration_stopsBlocking() {
        val startedAt = at(2026, 10, 21, 9, 0)
        val lock = OpenBlockRule(
            id = 2, name = "focus", blockedPackages = listOf("com.instagram.android"),
            ruleType = RuleType.TIMER, isActive = true,
            timerDurationMinutes = 30, timerStartedAt = startedAt
        )
        assertTrue(OpenBlockController.isEnforcingNow(lock, startedAt + 10 * 60_000L))
        assertFalse(OpenBlockController.isEnforcingNow(lock, startedAt + 31 * 60_000L))
    }
}
