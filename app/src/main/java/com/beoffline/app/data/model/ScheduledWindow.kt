package com.beoffline.app.data.model

/**
 * ScheduledWindow — the shared recurring time-window shape used by both
 * restriction rule types ([BlockRule] for internet blocking and
 * [OpenBlockRule] for open blocking).
 *
 * The two engines are independent features with independent rules, but they
 * share one window-evaluation implementation (see RuleScheduler) so
 * "am I inside the restricted window right now" behaves identically for both,
 * including midnight-crossing windows.
 */
interface ScheduledWindow {
    /** Hour of day the window STARTS (24h format). Null for non-scheduled rules. */
    val startHour: Int?
    val startMinute: Int?

    /** Hour of day the window ENDS. Null for non-scheduled rules. */
    val endHour: Int?
    val endMinute: Int?

    /** Which days of week the window applies. 1=Mon, 7=Sun. Null/empty = every day. */
    val activeDays: List<Int>?
}
