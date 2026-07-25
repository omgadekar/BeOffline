package com.beoffline.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Nocturne palette ──────────────────────────────────────────────────────────
// The names are kept from the first BeOffline palette so every screen picks the
// new ground up at once; the values are the ones the redesign is drawn against.
// Nothing here is a fill-first accent — the accent is a line and a glow.

/** Screen ground. */
val Brand900 = Color(0xFF161826)
/** Cards and rows sitting on the ground. */
val Brand800 = Color(0xFF232532)
/** One step up from a card — pressed states, inert tiles. */
val Brand700 = Color(0xFF292B31)
/** Hairlines and outlines that should read as structure, not decoration. */
val Brand600 = Color(0xFF3F424D)

/** Darker than the ground — the block overlay and challenge surface. */
val BrandVoid = Color(0xFF0F1019)

// Accent: a muted violet. Used as an outline, a glow, or 10–25% wash — rarely a fill.
val AccentPrimary   = Color(0xFF9184D9)
val AccentSecondary = Color(0xFFB5ABFC)
/** Accent-tinted outline: quieter than AccentPrimary, still clearly "ours". */
val AccentGlow      = Color(0xFF5D5294)
/** Text and icons sitting on an accent wash. */
val AccentBright    = Color(0xFFD2CEFD)
/** Filled accent surface — own chat bubbles, the live-session card's outline. */
val AccentSurface   = Color(0xFF423A6A)
/** Dimmer accent surface — avatars, group tiles. */
val AccentSurfaceDim = Color(0xFF2B2741)

// Status. The redesign has no celebratory green: "active" is simply the accent,
// and the only saturated colour left is the one that warns.
val StatusActive   = Color(0xFFB5ABFC)
val StatusInactive = Color(0xFF595D6C)
val StatusDanger   = Color(0xFFE08D74)

// Text ramp, brightest to faintest.
val TextPrimary   = Color(0xFFE9E9ED)
val TextMuted     = Color(0xFFCFD3E5)
val TextSecondary = Color(0xFF9397AB)
val TextTertiary  = Color(0xFF75798C)
val TextDisabled  = Color(0xFF595D6C)

// Gradient stops kept for the few surfaces that still wash a background.
val GradientStart = Color(0xFF9184D9)
val GradientEnd   = Color(0xFF5D5294)
