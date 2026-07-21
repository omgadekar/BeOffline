package com.beoffline.app.openblock

import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.AllowanceSource
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenBlockControllerTest {

    private val rule = OpenBlockRule(
        id = 1,
        name = "test",
        blockedPackages = listOf("com.a", "com.b"),
        ruleType = RuleType.PERMANENT,
        isActive = true
    )

    @Test
    fun enforced_withoutAllowances_blocksAllRulePackages() {
        val enforced = OpenBlockController.enforcedPackages(listOf(rule), emptyList(), 1_000L)
        assertEquals(setOf("com.a", "com.b"), enforced)
    }

    @Test
    fun unexpiredAllowance_liftsOnlyItsPackage() {
        val allowance = Allowance(
            packageName = "com.a", ruleId = 1, grantedUntil = 2_000L, source = AllowanceSource.TEASER
        )
        val enforced = OpenBlockController.enforcedPackages(listOf(rule), listOf(allowance), 1_000L)
        assertFalse("allowed package must not be enforced", "com.a" in enforced)
        assertTrue("other package stays enforced", "com.b" in enforced)
    }

    @Test
    fun expiredAllowance_noLongerLiftsTheBlock() {
        val allowance = Allowance(
            packageName = "com.a", ruleId = 1, grantedUntil = 2_000L, source = AllowanceSource.TEASER
        )
        val enforced = OpenBlockController.enforcedPackages(listOf(rule), listOf(allowance), 2_001L)
        assertTrue("expired allowance must re-block", "com.a" in enforced)
    }

    @Test
    fun pendingDisable_inFuture_stillEnforces() {
        // During the disable cooldown the lock must keep blocking.
        val disabling = rule.copy(disableEffectiveAt = 5_000L)
        val enforced = OpenBlockController.enforcedPackages(listOf(disabling), emptyList(), 1_000L)
        assertEquals(setOf("com.a", "com.b"), enforced)
        assertTrue(OpenBlockController.isEnforcingNow(disabling, 1_000L))
    }

    @Test
    fun pendingDisable_elapsed_stopsEnforcing() {
        // Once the cooldown passes the lock is effectively off — no alarm needed.
        val disabling = rule.copy(disableEffectiveAt = 1_000L)
        val enforced = OpenBlockController.enforcedPackages(listOf(disabling), emptyList(), 2_000L)
        assertTrue("elapsed disable must lift the block", enforced.isEmpty())
        assertFalse(OpenBlockController.isEnforcingNow(disabling, 2_000L))
    }
}
