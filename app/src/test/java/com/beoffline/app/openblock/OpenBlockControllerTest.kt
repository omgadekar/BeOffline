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
}
