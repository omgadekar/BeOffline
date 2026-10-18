package com.beoffline.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Paper is a light scheme. There is no dark variant yet, and the app should not
// follow the system setting until one exists: a half-built dark mode reads as a
// bug. See project.md for the palette and its contrast ratios.
private val PaperColorScheme = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = OnAccent,
    primaryContainer = AccentSurface,
    onPrimaryContainer = AccentBright,
    secondary = AccentSecondary,
    onSecondary = OnAccent,
    background = Brand900,
    onBackground = TextPrimary,
    surface = Brand800,
    onSurface = TextPrimary,
    surfaceVariant = Brand700,
    onSurfaceVariant = TextSecondary,
    outline = Brand600,
    outlineVariant = Brand500,
    error = StatusDanger,
    onError = OnAccent,
    errorContainer = StatusDangerSurface,
    onErrorContainer = StatusDanger
)

@Composable
fun BeOfflineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColorScheme,
        typography = Typography,
        content = content
    )
}
