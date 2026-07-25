package com.beoffline.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary            = AccentPrimary,
    onPrimary          = TextPrimary,
    primaryContainer   = AccentSurface,
    onPrimaryContainer = AccentBright,
    secondary          = AccentSecondary,
    background         = Brand900,
    surface            = Brand800,
    surfaceVariant     = Brand700,
    onBackground       = TextPrimary,
    onSurface          = TextPrimary,
    onSurfaceVariant   = TextSecondary,
    outline            = Brand600,
    outlineVariant     = AccentGlow,
    error              = StatusDanger,
)

@Composable
fun BeOfflineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content
    )
}
