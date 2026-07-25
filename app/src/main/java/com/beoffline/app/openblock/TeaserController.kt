package com.beoffline.app.openblock

import android.content.Context
import com.beoffline.app.data.local.AllowanceDao
import com.beoffline.app.data.local.SoloTeaserStateDao
import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.AllowanceSource
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.SoloTeaserState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TeaserController — coordinates the solo teaser's escalation state and the
 * allowance it grants on success. Local-only; mode 1 needs no backend.
 */
@Singleton
class TeaserController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val teaserStateDao: SoloTeaserStateDao,
    private val allowanceDao: AllowanceDao
) {
    private val prefs = context.getSharedPreferences(OpenBlockPrefs.FILE, Context.MODE_PRIVATE)

    /**
     * Escalation level for the NEXT unlock attempt on this rule: the number of
     * successful unlocks already made this focus session (0 = first).
     */
    suspend fun currentLevel(rule: OpenBlockRule, nowMillis: Long = System.currentTimeMillis()): Int {
        val key = TeaserEngine.sessionKeyFor(rule, nowMillis)
        val stored = teaserStateDao.getForRule(rule.id)
        return if (stored != null && stored.sessionKey == key) stored.unlockCount else 0
    }

    fun allowanceMinutes(): Int = prefs.getInt(
        OpenBlockPrefs.KEY_TEASER_ALLOWANCE_MINUTES,
        OpenBlockPrefs.DEFAULT_TEASER_ALLOWANCE_MINUTES
    )

    /**
     * Records a solved challenge: bumps this session's unlock count (making
     * the next unlock harder) and grants the allowance for [packageName].
     * Returns the granted minutes for the (deliberately dull) confirmation.
     */
    suspend fun recordSuccess(
        rule: OpenBlockRule,
        packageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val key = TeaserEngine.sessionKeyFor(rule, nowMillis)
        val level = currentLevel(rule, nowMillis)
        teaserStateDao.upsert(
            SoloTeaserState(
                ruleId = rule.id,
                sessionKey = key,
                unlockCount = level + 1,
                updatedAt = nowMillis
            )
        )
        val minutes = allowanceMinutes()
        allowanceDao.insert(
            Allowance(
                packageName = packageName,
                ruleId = rule.id,
                grantedUntil = nowMillis + minutes * 60_000L,
                source = AllowanceSource.TEASER
            )
        )
        // Opportunistic cleanup of long-expired rows.
        allowanceDao.deleteExpired(nowMillis)
        return minutes
    }
}
