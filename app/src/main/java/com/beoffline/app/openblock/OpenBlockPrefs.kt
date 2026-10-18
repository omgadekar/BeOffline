package com.beoffline.app.openblock

/**
 * SharedPreferences contract for the open-block feature — one place for the
 * file/key names shared by the UI and the challenge engine.
 */
object OpenBlockPrefs {
    const val FILE = "open_block_settings"
    const val KEY_DISCLOSURE_ACCEPTED = "disclosure_accepted"

    /** How long a solved challenge unlocks the app for. */
    const val KEY_TEASER_ALLOWANCE_MINUTES = "teaser_allowance_minutes"
    const val DEFAULT_TEASER_ALLOWANCE_MINUTES = 5

    /**
     * Comma-separated [ChallengeKind] names the user left switched on. Absent
     * means "all of them" — the default is the widest rotation, because a
     * single kind is the one you can get good at.
     */
    const val KEY_ENABLED_CHALLENGE_KINDS = "enabled_challenge_kinds"

    fun parseKinds(stored: String?): Set<ChallengeKind> {
        if (stored.isNullOrBlank()) return ChallengeKind.entries.toSet()
        val parsed = stored.split(',')
            .mapNotNull { name -> ChallengeKind.entries.firstOrNull { it.name == name.trim() } }
            .toSet()
        // Never let a stale or hand-edited value leave the user with no
        // challenge at all — that would be an unlock with no friction.
        return parsed.ifEmpty { ChallengeKind.entries.toSet() }
    }

    fun storeKinds(kinds: Set<ChallengeKind>): String =
        kinds.ifEmpty { ChallengeKind.entries.toSet() }.joinToString(",") { it.name }
}
