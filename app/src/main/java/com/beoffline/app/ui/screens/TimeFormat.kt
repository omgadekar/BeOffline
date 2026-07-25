package com.beoffline.app.ui.screens

/**
 * "1d 3h", "23h 45m", "12m" — compact remaining time until [untilMillis],
 * relative to [nowMillis]. Shared by every cooldown countdown (partner removal,
 * group-member removal, App Lock disable).
 */
fun formatCooldownRemaining(untilMillis: Long, nowMillis: Long): String {
    val remaining = untilMillis - nowMillis
    if (remaining <= 0) return "under a minute"
    val totalMinutes = remaining / 60_000
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
