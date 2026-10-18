package com.beoffline.app.support

import android.os.Build
import com.beoffline.app.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IssueReporter @Inject constructor(
    private val crashReporting: CrashReportingManager
) {

    /**
     * Sends the report to Crashlytics. Returns false, having sent nothing, when
     * crash reporting is switched off — the title and detail someone types here
     * go to Google like everything else does, so the Settings switch has to
     * govern this path too, not just crash stacks.
     */
    fun submitIssue(
        title: String,
        details: String,
        isVpnRunning: Boolean,
        activeRulesCount: Int,
        totalRulesCount: Int
    ): Boolean {
        if (!crashReporting.isEnabled()) return false

        val crashlytics = FirebaseCrashlytics.getInstance()

        crashlytics.log("User submitted an issue report from the dashboard")
        crashlytics.log("Issue title: ${title.take(120)}")
        details.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line -> crashlytics.log("Issue details: ${line.take(240)}") }

        crashlytics.setCustomKey("issue_report", true)
        crashlytics.setCustomKey("issue_title", title.take(120))
        crashlytics.setCustomKey("issue_details", details.take(1000))
        crashlytics.setCustomKey("app_version_name", BuildConfig.VERSION_NAME)
        crashlytics.setCustomKey("app_version_code", BuildConfig.VERSION_CODE.toString())
        crashlytics.setCustomKey("android_version", Build.VERSION.RELEASE ?: "unknown")
        crashlytics.setCustomKey("manufacturer", Build.MANUFACTURER ?: "unknown")
        crashlytics.setCustomKey("model", Build.MODEL ?: "unknown")
        crashlytics.setCustomKey("vpn_running", isVpnRunning)
        crashlytics.setCustomKey("active_rules_count", activeRulesCount)
        crashlytics.setCustomKey("total_rules_count", totalRulesCount)

        crashlytics.recordException(UserReportedIssueException(title.take(80)))
        return true
    }
}

private class UserReportedIssueException(title: String) :
    RuntimeException("User report: $title")
