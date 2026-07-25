package com.beoffline.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// The redesign's shared vocabulary. Every screen is assembled from these, so a
// change here is a change everywhere — which is the point.
// ─────────────────────────────────────────────────────────────────────────────

/** Radii used across the app: 8 for controls, 12–14 for cards. */
object BoShape {
    val Control: Shape = RoundedCornerShape(8.dp)
    val Card: Shape = RoundedCornerShape(14.dp)
    val SmallCard: Shape = RoundedCornerShape(12.dp)
    val Icon: Shape = RoundedCornerShape(11.dp)
    val Pill: Shape = RoundedCornerShape(999.dp)
}

/**
 * A card. Outlined rather than elevated — this UI separates surfaces with a
 * hairline, not a shadow. [accented] switches the outline to the accent for
 * the one card on a screen that's actually asking for attention.
 */
@Composable
fun BoCard(
    modifier: Modifier = Modifier,
    accented: Boolean = false,
    shape: Shape = BoShape.Card,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .clip(shape)
        .background(Brand800, shape)
        .border(1.dp, if (accented) AccentSurface else Color.White.copy(alpha = 0.07f), shape)
    Column(
        modifier = (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(padding),
        content = content
    )
}

/** The small uppercase label that opens a section or a card. */
@Composable
fun BoEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextTertiary
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
    )
}

/**
 * The hairline that separates a screen's header from its scrolling body — it
 * fades out at both ends so it never collides with the screen edge.
 */
@Composable
fun BoFadedDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.14f to Color.White.copy(alpha = 0.14f),
                    0.86f to Color.White.copy(alpha = 0.14f),
                    1f to Color.Transparent
                )
            )
    )
}

/** Plain hairline for rows inside a card. */
@Composable
fun BoHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.07f))
    )
}

/**
 * The pill switch. Deliberately hand-rolled instead of Material's Switch: this
 * one is an outline when off and an accent wash when on, and it never fills.
 */
@Composable
fun BoToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val knobOffset by animateDpAsState(if (checked) 19.dp else 2.dp, label = "knob")
    Box(
        modifier = modifier
            .size(width = 40.dp, height = 23.dp)
            .clip(BoShape.Pill)
            .background(if (checked) AccentPrimary.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (checked) AccentPrimary else Brand600, BoShape.Pill)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset, y = 2.dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !enabled -> TextDisabled
                        checked -> AccentBright
                        else -> StatusInactive
                    }
                )
        )
    }
}

/**
 * The app's primary action: an accent outline over a faint accent wash. There
 * is no solid-fill button in this design — a filled button would be the
 * loudest thing on any screen it appeared on.
 */
@Composable
fun BoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val tint = if (enabled) AccentBright else TextDisabled
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) AccentPrimary.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (enabled) AccentPrimary else Brand600, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/** The quieter sibling — a bare outline, no wash. Used for "Deny", "Cancel". */
@Composable
fun BoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Brand600, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
    }
}

/** Compact header action — "New rule", "New lock". */
@Composable
fun BoHeaderAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(BoShape.Control)
            .border(1.dp, AccentPrimary, BoShape.Control)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = AccentBright, modifier = Modifier.size(14.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = AccentBright)
    }
}

/** A selectable chip — durations, grant lengths, challenge kinds. */
@Composable
fun BoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = BoShape.Control
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) AccentPrimary.copy(alpha = 0.20f) else Color.Transparent)
            .border(1.dp, if (selected) AccentPrimary else Brand600, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AccentBright else TextSecondary
        )
    }
}

/** Screen title block used at the top of every tab. */
@Composable
fun BoScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    action: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (action != null) action()
    }
}

/** Centred empty state — an outline icon, a line, and a reason. */
@Composable
fun BoEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Brand600, modifier = Modifier.size(34.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextDisabled,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

// ── Real app icons ───────────────────────────────────────────────────────────
// The prototype shipped stand-in PNGs for Instagram/WhatsApp/etc. Those are
// deliberately not in the app: every icon below is the launcher icon the
// PackageManager hands us for the package actually installed on this phone.

private fun Drawable.toIconBitmap(): ImageBitmap? {
    (this as? BitmapDrawable)?.bitmap?.let { return it.asImageBitmap() }
    val width = intrinsicWidth.takeIf { it > 0 } ?: 48
    val height = intrinsicHeight.takeIf { it > 0 } ?: 48
    return try {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

/**
 * An installed app's launcher icon, with a monogram fallback for the moment
 * before the PackageManager lookup lands (or for a package that has since been
 * uninstalled).
 */
@Composable
fun BoAppIcon(
    drawable: Drawable?,
    appName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = BoShape.Icon,
    dimmed: Boolean = false
) {
    val bitmap = remember(drawable) { drawable?.toIconBitmap() }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (bitmap == null) Brand700 else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = appName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        if (dimmed) {
            Box(modifier = Modifier.fillMaxSize().background(Brand900.copy(alpha = 0.62f)))
        }
    }
}

/** A round monogram avatar — chat, partner rows, group members. */
@Composable
fun BoAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color = AccentSurfaceDim,
    outlined: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (outlined) Modifier.border(1.dp, AccentGlow, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.labelLarge,
            color = AccentBright
        )
    }
}

/** Progress ladder for the unlock challenge — one dash per step. */
@Composable
fun BoStepTrack(
    total: Int,
    done: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total.coerceAtLeast(1)) { index ->
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < done) AccentPrimary else Brand600)
            )
        }
    }
}
