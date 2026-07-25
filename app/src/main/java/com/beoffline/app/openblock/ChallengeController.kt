package com.beoffline.app.openblock

import android.content.Context
import android.util.Log
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.data.local.AllowanceDao
import com.beoffline.app.data.local.SoloTeaserStateDao
import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.AllowanceSource
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.SoloTeaserState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ChallengeController — owns the escalation state behind the solo unlock
 * challenge, the allowance it grants on success, and which kinds are in play.
 *
 * The ladder used to live only in Room, which meant "clear app data" was a
 * one-tap reset to the easiest challenge — a hole worth closing now that
 * passing one challenge is no longer the only way through. The server keeps a
 * copy per focus session and the effective level is the HIGHER of the two, so
 * wiping the device loses nothing that made the app harder, and being offline
 * never makes it easier than it already was locally.
 */
@Singleton
class ChallengeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val teaserStateDao: SoloTeaserStateDao,
    private val allowanceDao: AllowanceDao,
    private val accountabilityRepository: AccountabilityRepository
) {
    private companion object {
        const val TAG = "ChallengeController"
        /** The overlay is already on screen — never make it wait on the network. */
        const val SERVER_LEVEL_TIMEOUT_MS = 2_000L
    }

    private val prefs = context.getSharedPreferences(OpenBlockPrefs.FILE, Context.MODE_PRIVATE)

    /**
     * Escalation level for the NEXT unlock attempt on this rule: the number of
     * successful unlocks already made this focus session (0 = first).
     */
    suspend fun currentLevel(rule: OpenBlockRule, nowMillis: Long = System.currentTimeMillis()): Int {
        val key = ChallengeEngine.sessionKeyFor(rule, nowMillis)
        val local = localLevel(rule.id, key)

        val remote = withTimeoutOrNull(SERVER_LEVEL_TIMEOUT_MS) {
            try {
                accountabilityRepository.fetchChallengeLevel(ruleKeyFor(rule), key)
            } catch (e: Exception) {
                Log.d(TAG, "Server level unavailable, using local: ${e.message}")
                null
            }
        }

        val effective = maxOf(local, remote ?: 0)
        // Server knew about unlocks this device had forgotten — write them back
        // so the next attempt doesn't need the network to stay honest.
        if (effective > local) persistLevel(rule.id, key, effective, nowMillis)
        return effective
    }

    fun allowanceMinutes(): Int = prefs.getInt(
        OpenBlockPrefs.KEY_TEASER_ALLOWANCE_MINUTES,
        OpenBlockPrefs.DEFAULT_TEASER_ALLOWANCE_MINUTES
    )

    fun enabledKinds(): Set<ChallengeKind> =
        OpenBlockPrefs.parseKinds(prefs.getString(OpenBlockPrefs.KEY_ENABLED_CHALLENGE_KINDS, null))

    /**
     * The kind this attempt will serve. Scoped to the rule's focus session as
     * well as the level, so backing out can't reroll it but a fresh session
     * doesn't always open with the same kind.
     */
    fun kindFor(
        rule: OpenBlockRule,
        level: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): ChallengeKind =
        ChallengeEngine.kindFor(enabledKinds(), level, ChallengeEngine.sessionKeyFor(rule, nowMillis))

    /**
     * Records a solved challenge: bumps this session's unlock count (making the
     * next unlock harder), grants the allowance for [packageName], and reports
     * it so the ladder survives this install and any partner can see it.
     * Returns the granted minutes for the (deliberately dull) confirmation.
     */
    suspend fun recordSuccess(
        rule: OpenBlockRule,
        packageName: String,
        appLabel: String,
        kind: ChallengeKind,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val key = ChallengeEngine.sessionKeyFor(rule, nowMillis)
        val level = localLevel(rule.id, key)
        val newLevel = level + 1
        persistLevel(rule.id, key, newLevel, nowMillis)

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

        // Queued, not awaited: the unlock has already happened locally and must
        // not depend on connectivity.
        try {
            accountabilityRepository.reportSoloUnlock(
                ruleKey = ruleKeyFor(rule),
                sessionKey = key,
                level = newLevel,
                kind = kind.name,
                packageName = packageName,
                appLabel = appLabel,
                grantedMinutes = minutes
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not queue solo-unlock report", e)
        }
        return minutes
    }

    private suspend fun localLevel(ruleId: Int, sessionKey: String): Int {
        val stored = teaserStateDao.getForRule(ruleId)
        return if (stored != null && stored.sessionKey == sessionKey) stored.unlockCount else 0
    }

    private suspend fun persistLevel(ruleId: Int, sessionKey: String, level: Int, nowMillis: Long) {
        teaserStateDao.upsert(
            SoloTeaserState(
                ruleId = ruleId,
                sessionKey = sessionKey,
                unlockCount = level,
                updatedAt = nowMillis
            )
        )
    }

    /**
     * Server-side identity for a rule. Rule ids are per-device row ids, so this
     * is scoped by the account the request is authenticated as — the server
     * never needs to know what the rule is called or which apps it names.
     */
    private fun ruleKeyFor(rule: OpenBlockRule) = "rule-${rule.id}"
}
