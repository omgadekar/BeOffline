package com.beoffline.app.util

/**
 * People are shown by first name. A Google account's display name is usually
 * the person's full legal name, which is both more than you need to know who
 * approved your unlock and more than fits in a notification line.
 *
 * This is a display rule only — the full name stays in the cache and in the
 * member list, where telling two people apart actually matters.
 */
fun String?.firstName(fallback: String = "Your partner"): String {
    val trimmed = this?.trim().orEmpty()
    if (trimmed.isEmpty()) return fallback
    return trimmed.substringBefore(' ').ifEmpty { fallback }
}

/**
 * First names for a set of people, keeping enough of the full name to tell
 * two Priyas apart. Anyone whose first name is unique in [fullNames] is shown
 * by first name alone; the rest keep a surname initial ("Priya S.").
 */
fun disambiguatedFirstNames(fullNames: List<String>): Map<String, String> {
    val byFirst = fullNames.groupBy { it.firstName(fallback = it) }
    return fullNames.associateWith { full ->
        val first = full.firstName(fallback = full)
        val sharers = byFirst[first].orEmpty()
        if (sharers.size <= 1) {
            first
        } else {
            val rest = full.trim().substringAfter(' ', "").trim()
            if (rest.isEmpty()) first else "$first ${rest.first().uppercaseChar()}."
        }
    }
}
