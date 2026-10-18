package com.beoffline.app.ui.screens

import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.model.ScheduledWindow
import com.beoffline.app.scheduler.RuleScheduler

/**
 * How hard a block is. This is the ONLY choice the user makes about the two
 * engines, and they never meet the word "engine", "VPN" or "accessibility":
 *
 *   Offline -> the app opens, but nothing loads  (BlockRule, VpnService)
 *   Locked  -> the app will not open at all      (OpenBlockRule, a11y service)
 */
enum class Strictness { Offline, Locked }

/** What a row is doing right now. Drives colour, not wording. */
enum class AppStatus {
    /** Blocking, now. */
    BlockedNow,

    /** Open on an allowance someone granted, or the user earned. */
    BorrowedTime,

    /** Set up, but its window has not come round yet. */
    Later,

    /** Switched off by the user. Not blocking anything. */
    Paused
}

/**
 * One app row, said out loud.
 *
 * The whole point of this screen is that a person can read it without being
 * taught anything: every row states what the phone is doing in a sentence, not
 * a status word plus a legend. [sentence] is that sentence; [trailing] is the
 * live countdown, when there is one, so the sentence itself never has to tick.
 */
data class AppBlockState(
    val status: AppStatus,
    val sentence: String,
    val trailing: String? = null
)

/**
 * Turns a rule plus the clock into the sentence a row shows.
 *
 * Pure on purpose: no Context, no Compose, and "now" is always passed in, so
 * every phrasing below is covered by a plain JVM test.
 */
object AppStateCopy {

    fun describe(
        strictness: Strictness,
        ruleType: RuleType,
        window: ScheduledWindow,
        isActive: Boolean,
        timerDurationMinutes: Int? = null,
        timerStartedAt: Long? = null,
        allowanceUntil: Long? = null,
        allowanceGrantedBy: String? = null,
        now: Long = System.currentTimeMillis()
    ): AppBlockState {
        // An allowance outranks everything: whatever the rule says, the app is
        // open right now and the person should be told how long for.
        if (allowanceUntil != null && allowanceUntil > now) {
            val mins = minutesUntil(allowanceUntil, now)
            val who = allowanceGrantedBy?.let { "$it said yes" } ?: "you earned it"
            return AppBlockState(
                status = AppStatus.BorrowedTime,
                sentence = "Open for $mins more ${plural(mins, "minute")} - $who",
                trailing = clockRemaining(allowanceUntil, now)
            )
        }

        if (!isActive) return paused()

        val verb = when (strictness) {
            Strictness.Locked -> "Will not open"
            Strictness.Offline -> "Nothing loads"
        }

        return when (ruleType) {
            RuleType.PERMANENT -> AppBlockState(
                status = AppStatus.BlockedNow,
                sentence = "$verb until you turn it off"
            )

            RuleType.TIMER -> {
                val endsAt = timerEndsAt(timerDurationMinutes, timerStartedAt)
                if (endsAt == null || endsAt <= now) {
                    paused()
                } else {
                    AppBlockState(
                        status = AppStatus.BlockedNow,
                        sentence = "$verb for another ${spokenDuration(endsAt, now)}",
                        trailing = clockRemaining(endsAt, now)
                    )
                }
            }

            RuleType.SCHEDULED -> {
                if (RuleScheduler.isWithinScheduledWindow(window, now)) {
                    AppBlockState(
                        status = AppStatus.BlockedNow,
                        sentence = "$verb until ${clock(window.endHour, window.endMinute)}"
                    )
                } else {
                    AppBlockState(
                        status = AppStatus.Later,
                        sentence = "$verb - ${days(window.activeDays)}, " +
                            "${clock(window.startHour, window.startMinute)} to " +
                            clock(window.endHour, window.endMinute)
                    )
                }
            }
        }
    }

    /** The running summary shown under the creator, before anything is saved. */
    fun summarise(appNames: List<String>, strictness: Strictness, whenPhrase: String): String {
        val subject = when {
            appNames.isEmpty() -> "Nothing"
            appNames.size == 1 -> appNames.first()
            appNames.size == 2 -> "${appNames[0]} and ${appNames[1]}"
            else -> "${appNames[0]}, ${appNames[1]} and ${appNames.size - 2} more"
        }
        val verb = when (strictness) {
            Strictness.Locked -> "will not open"
            Strictness.Offline -> "will open but stay empty"
        }
        return "$subject $verb $whenPhrase."
    }

    // Formatting ────────────────────────────────────────────────────────────

    /** 24h fields in, 12h clock out. Null fields read as a blank time. */
    fun clock(hour: Int?, minute: Int?): String {
        if (hour == null || minute == null) return "--:--"
        val suffix = if (hour < 12) "AM" else "PM"
        val shown = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$shown:${pad(minute)} $suffix"
    }

    /**
     * "Every day", "Mon-Fri", "Weekends", or the list. 1 = Monday, 7 = Sunday,
     * matching [ScheduledWindow.activeDays].
     */
    fun days(activeDays: List<Int>?): String {
        val set = activeDays?.toSortedSet()
        if (set.isNullOrEmpty() || set.size == 7) return "Every day"
        if (set == sortedSetOf(1, 2, 3, 4, 5)) return "Mon-Fri"
        if (set == sortedSetOf(6, 7)) return "Weekends"
        return set.joinToString(", ") { DAY_NAMES[it] ?: "?" }
    }

    /** "47 min" / "1h 12m" — how long is left, said the way a person would. */
    fun spokenDuration(untilMillis: Long, nowMillis: Long): String {
        val minutes = minutesUntil(untilMillis, nowMillis)
        if (minutes < 60) return "$minutes min"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0L) "${hours}h" else "${hours}h ${rest}m"
    }

    /** "4:02" — the ticking part, kept out of the sentence. */
    fun clockRemaining(untilMillis: Long, nowMillis: Long): String {
        val totalSeconds = (untilMillis - nowMillis).coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "$hours:${pad(minutes)}:${pad(seconds)}"
        } else {
            "$minutes:${pad(seconds)}"
        }
    }

    private fun paused() = AppBlockState(
        status = AppStatus.Paused,
        sentence = "Paused - not blocking right now"
    )

    /** Rounds UP, so "1 more minute" never shows while there is still time on it. */
    private fun minutesUntil(untilMillis: Long, nowMillis: Long): Long {
        val remaining = (untilMillis - nowMillis).coerceAtLeast(0L)
        return (remaining + 59_999) / 60_000
    }

    private fun timerEndsAt(durationMinutes: Int?, startedAt: Long?): Long? {
        if (durationMinutes == null || startedAt == null) return null
        return startedAt + durationMinutes * 60_000L
    }

    private fun plural(count: Long, word: String) = if (count == 1L) word else word + "s"

    private fun pad(value: Int): String = value.toString().padStart(2, '0')

    private fun pad(value: Long): String = value.toString().padStart(2, '0')

    private val DAY_NAMES = mapOf(
        1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
        5 to "Fri", 6 to "Sat", 7 to "Sun"
    )
}
