package com.beoffline.app.data.model

import android.graphics.drawable.Drawable

/**
 * AppInfo — lightweight data class for the installed app picker.
 * Populated by PackageManager at runtime; not stored in the DB.
 * The [icon] field holds the real launcher icon — null-safe for diffing.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null
)
