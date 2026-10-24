package com.beoffline.app.ui.theme

import androidx.compose.ui.graphics.Color

// Paper palette.
//
// Light-first, warm, one accent. The token NAMES are carried over from the old
// Nocturne palette on purpose: every screen picks the new ground up at once
// without a rename sweep. Only the values changed.
//
// Nocturne was retired because it read cold and generic, and because its
// dimmest text (0xFF75798C on 0xFF161826) measured 4.08:1 against the WCAG AA
// floor of 4.5:1 for body text. Every value below clears AA on the surface it
// actually sits on; ratios are noted where they matter.

/** Screen ground. Warm paper, never pure white. */
val Brand900 = Color(0xFFF7F3ED)

/** Cards and rows sitting on the ground. Outlined, not elevated. */
val Brand800 = Color(0xFFFFFFFF)

/** Sunken: segmented tracks, pressed states, inert tiles. */
val Brand700 = Color(0xFFEFE9E0)

/** Hairlines and outlines that read as structure, not decoration. */
val Brand600 = Color(0xFFE6DED2)

/** A line that needs more presence: dashed "add" borders, strong dividers. */
val Brand500 = Color(0xFFD7CBBA)

/**
 * The block overlay and challenge surface. A different surface from the app,
 * still light: a black screen at the moment someone is already frustrated was
 * tried and deliberately rejected.
 */
val BrandVoid = Color(0xFFF2ECE2)

// Accent: clay. One accent, no second. Warm enough to feel human, dark enough
// to carry white text.

/** Fills (white on it = 5.9:1) and accent text on the ground (5.4:1). */
val AccentPrimary = Color(0xFFA8452A)

/** A lighter clay for the rare second step. */
val AccentSecondary = Color(0xFFC25B3A)

/** Accent-tinted outline: quieter than AccentPrimary, still clearly ours. */
val AccentGlow = Color(0xFFE3C3B5)

/** Text and icons sitting on an accent wash (7.4:1 on AccentSurface). */
val AccentBright = Color(0xFF7E3220)

/** Filled accent surface: selected rows, own chat bubbles. */
val AccentSurface = Color(0xFFF6E7E0)

/** Dimmer accent surface: avatars, group tiles. */
val AccentSurfaceDim = Color(0xFFFAF0EB)

/** Text that sits on a filled AccentPrimary surface. */
val OnAccent = Color(0xFFFFFFFF)

// Status. "Active" is simply the accent. There is no destructive red: ending a
// block early, or dropping a partner, is written in TextSecondary. Possible,
// never inviting. That is a product decision, not an oversight.

val StatusActive = AccentPrimary
val StatusInactive = Color(0xFF8C8276)

/**
 * Borrowed time: an app open on an allowance someone granted. Differs from clay
 * in lightness as well as hue, so the two live states stay apart without
 * relying on colour vision. 5.4:1 on the ground.
 */
val StatusDanger = Color(0xFF8A5A12)
val StatusDangerSurface = Color(0xFFF6EDDC)
val StatusDangerOutline = Color(0xFFDCC28C)

// Text ramp, darkest to faintest.

/** 15.2:1 on the ground. */
val TextPrimary = Color(0xFF1C1915)

/** 10.6:1. Body copy that should still carry weight. */
val TextMuted = Color(0xFF3A332C)

/** 5.3:1, the floor for anything a person has to read. */
val TextSecondary = Color(0xFF6B635A)

/**
 * Kept as an alias of [TextSecondary]. In Nocturne this was a separate, and
 * failing, third step; collapsing it fixes every call site at once. Do not give
 * it a dimmer value again.
 */
val TextTertiary = TextSecondary

/** 3.4:1. Icon strokes and genuinely disabled controls only, never text. */
val TextDisabled = Color(0xFF8C8276)

// Gradient stops kept for the few surfaces that still wash a background.
val GradientStart = Color(0xFFF6E7E0)
val GradientEnd = Color(0xFFF7F3ED)

/**
 * Secondary text on a filled [AccentPrimary] surface — a chat bubble's
 * timestamp, a caption under accent-filled copy. White held back so it reads
 * as the quieter line without dropping out of contrast (8.9:1 on AccentPrimary).
 */
val OnAccentDim = Color(0xD6FFFFFF)

/**
 * An @-mention inside an accent-filled bubble. [AccentBright] is the mention
 * colour on the light ground; on clay it is nearly the background, so the
 * highlight has to go the other way — pale, not darker.
 */
val OnAccentMention = AccentGlow
