package com.beoffline.app.openblock

/**
 * SharedPreferences contract for the open-block feature — one place for the
 * file/key names shared by the UI and the teaser engine.
 */
object OpenBlockPrefs {
    const val FILE = "open_block_settings"
    const val KEY_DISCLOSURE_ACCEPTED = "disclosure_accepted"

    /** How long a solved teaser unlocks the app for. */
    const val KEY_TEASER_ALLOWANCE_MINUTES = "teaser_allowance_minutes"
    const val DEFAULT_TEASER_ALLOWANCE_MINUTES = 5
}
