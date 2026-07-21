package com.beoffline.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.beoffline.app.data.local.Converters

/**
 * OpenBlockRule — represents a user-configured "can't open" rule for one or more apps.
 *
 * This is the OPEN-BLOCKING engine's rule type: during an active rule, the
 * selected apps cannot be *opened* at all (foreground detection + block screen),
 * as opposed to [BlockRule] which only cuts their *internet* via the VPN.
 *
 * The two features are deliberately independent — separate tables, separate
 * rules, separate UI — and must never conflict. They share only the
 * [ScheduledWindow] evaluation logic and the [RuleType] vocabulary:
 *   - PERMANENT: Block opening immediately until manually turned off.
 *   - SCHEDULED: Block opening only during a time window (e.g., 10 PM–7 AM).
 *   - TIMER:     Block opening for a fixed duration from now.
 */
@Entity(tableName = "open_block_rules")
@TypeConverters(Converters::class)
data class OpenBlockRule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** Human-readable name for the rule, e.g., "Deep Work" */
    val name: String,

    /** List of Android package names that cannot be opened while the rule is active. */
    val blockedPackages: List<String>,

    /** The type of this rule */
    val ruleType: RuleType,

    /** Is this rule currently enforced (open-block engine active for it) */
    val isActive: Boolean = false,

    // ── Scheduled rule fields (ScheduledWindow) ────────────────────────────
    /** Hour of day to START blocking (24h format). Null for non-scheduled rules. */
    override val startHour: Int? = null,
    override val startMinute: Int? = null,

    /** Hour of day to STOP blocking. Null for non-scheduled rules. */
    override val endHour: Int? = null,
    override val endMinute: Int? = null,

    /** Which days of week the schedule applies. 1=Mon, 7=Sun. */
    override val activeDays: List<Int>? = null,

    // ── Timer rule fields ──────────────────────────────────────────────────
    /** Duration in minutes for TIMER type rules. */
    val timerDurationMinutes: Int? = null,

    /** Epoch millis when the timer started (for countdown display). */
    val timerStartedAt: Long? = null,

    /**
     * Pending-disable cooldown (accountability). When set, the user has asked to
     * turn this lock off but it keeps enforcing until this instant, at which
     * point it finalizes to inactive. Null = not being turned off. Enforcement
     * treats a rule as OFF once [disableEffectiveAt] is in the past, so the
     * block lifts on time with no alarm needed (same model as allowances).
     */
    val disableEffectiveAt: Long? = null,

    /** Epoch millis when this rule was created */
    val createdAt: Long = System.currentTimeMillis()
) : ScheduledWindow
