package com.beoffline.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SoloTeaserState — per-rule escalation state for the solo unlock teaser.
 *
 * [sessionKey] identifies the focus session the counter belongs to:
 *   SCHEDULED → the current window occurrence's start (epoch millis as text)
 *   TIMER     → timerStartedAt
 *   PERMANENT → the local calendar day (resets daily)
 * When the stored key differs from the freshly computed one, the counter is
 * stale and treated as 0 — this is the "escalation resets per focus session"
 * behavior. Purely local; mode 1 needs no backend.
 */
@Entity(tableName = "solo_teaser_state")
data class SoloTeaserState(
    @PrimaryKey
    val ruleId: Int,

    /** Which focus session [unlockCount] counts for. */
    val sessionKey: String,

    /** Successful unlocks so far this session — drives difficulty. */
    val unlockCount: Int,

    val updatedAt: Long = System.currentTimeMillis()
)
