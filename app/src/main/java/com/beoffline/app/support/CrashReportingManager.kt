package com.beoffline.app.support

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one switch for everything that leaves the phone for Firebase Crashlytics:
 * crash stacks, and the text someone types into "Report an issue".
 *
 * **Default: on.** Crashes here are not cosmetic — a crash in the tunnel or the
 * foreground watcher means a block silently stopped working, and that is the
 * one thing this app has to get right. The answer to the privacy problem the
 * audit found is that it is now *disclosed* (privacy page, and the Settings row
 * itself) and *reversible in one tap*, not that it is off until someone goes
 * looking for it.
 *
 * Turning it off is honoured in three places at once: Crashlytics stops
 * collecting, the pending reports it has already queued on disk are deleted,
 * and [IssueReporter] refuses to send.
 */
@Singleton
class CrashReportingManager @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val PREFS_NAME = "crash_reporting"
        private const val KEY_ENABLED = "enabled"
        const val DEFAULT_ENABLED = true
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var enabledCache: Boolean = preferences.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun isEnabled(): Boolean = enabledCache

    fun setEnabled(enabled: Boolean) {
        enabledCache = enabled
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        apply()
        // Switching off should also drop what is already waiting to go, not just
        // stop the next one.
        if (!enabled) runCatching { FirebaseCrashlytics.getInstance().deleteUnsentReports() }
    }

    /**
     * Pushes the stored choice into Crashlytics. Called once from
     * [com.beoffline.app.BeOfflineApp] so the setting is in force before
     * anything can crash, and again on every change.
     */
    fun apply() {
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabledCache)
        }
    }
}
