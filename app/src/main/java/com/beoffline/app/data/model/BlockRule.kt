package com.beoffline.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.beoffline.app.data.local.Converters

/**
 * BlockRule — represents a user-configured "offline rule" for one or more apps.
 *
 * A rule can be either:
 *   - PERMANENT: Block immediately and stay blocked until manually turned off.
 *   - SCHEDULED: Block apps only during a specific time window (e.g., 9 AM–12 PM, Mon-Fri).
 *   - TIMER:     Block for a fixed duration from now (e.g., "focus for 45 minutes").
 */
@Entity(tableName = "block_rules")
@TypeConverters(Converters::class)
data class BlockRule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** Human-readable name for the rule, e.g., "Morning Focus" */
    val name: String,

    /** List of Android package names to block. e.g., ["com.whatsapp", "com.instagram.android"] */
    val blockedPackages: List<String>,

    /** The type of this rule */
    val ruleType: RuleType,

    /** Is this rule currently enforced (VPN active for it) */
    val isActive: Boolean = false,

    // ── Scheduled rule fields ──────────────────────────────────────────────
    /** Hour of day to START blocking (24h format). Null for non-scheduled rules. */
    val startHour: Int? = null,
    val startMinute: Int? = null,

    /** Hour of day to STOP blocking. Null for non-scheduled rules. */
    val endHour: Int? = null,
    val endMinute: Int? = null,

    /** Which days of week the schedule applies. 1=Mon, 7=Sun. */
    val activeDays: List<Int>? = null,

    // ── Timer rule fields ──────────────────────────────────────────────────
    /** Duration in minutes for TIMER type rules. */
    val timerDurationMinutes: Int? = null,

    /** Epoch millis when the timer started (for countdown display). */
    val timerStartedAt: Long? = null,

    /** Epoch millis when this rule was created */
    val createdAt: Long = System.currentTimeMillis()
)

enum class RuleType {
    PERMANENT,   // Block indefinitely until user manually stops
    SCHEDULED,   // Block on a recurring time schedule
    TIMER        // Block for a fixed duration
}
