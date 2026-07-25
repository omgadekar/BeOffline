package com.beoffline.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Nocturne never goes heavier than Medium — weight is not how this UI shouts,
// and at these sizes Bold only muddies the dark ground. Display sizes track
// tight; the small caps-y labels track wide.
val Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 42.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp,
        color      = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
        color      = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        color      = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        color      = TextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 17.sp,
        lineHeight = 23.sp,
        color      = TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 15.sp,
        lineHeight = 21.sp,
        color      = TextPrimary
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 19.sp,
        color      = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 15.sp,
        lineHeight = 22.sp,
        color      = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 13.5f.sp,
        lineHeight = 20.sp,
        color      = TextSecondary
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 18.sp,
        color      = TextTertiary
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 13.sp,
        lineHeight = 18.sp,
        color      = TextPrimary
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        color      = TextSecondary
    ),
    // The uppercase eyebrow that opens every section.
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 10.5f.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
        color      = TextTertiary
    )
)
