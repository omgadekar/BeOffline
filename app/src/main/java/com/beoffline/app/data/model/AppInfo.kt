package com.beoffline.app.data.model

/**
 * AppInfo — lightweight data class for the installed app picker.
 * Populated by PackageManager at runtime; not stored in the DB.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false
)
