package com.beoffline.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentGlow
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.AccentSurface
import com.beoffline.app.ui.theme.BoAppIcon
import com.beoffline.app.ui.theme.BoCard
import com.beoffline.app.ui.theme.BoEyebrow
import com.beoffline.app.ui.theme.BoFadedDivider
import com.beoffline.app.ui.theme.BoShape
import com.beoffline.app.ui.theme.BoToggle
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand900
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * Home — "how much of my phone is offline right now", answered before anything
 * else. The old dashboard opened with a status widget; here the status *is* the
 * page, and the rule list is what sits underneath it.
 */
@Composable
fun HomeScreen(
    onRequestVpn: (List<String>) -> Unit,
    onSeeAllRules: () -> Unit,
    onOpenAppLock: () -> Unit,
    onCreateRule: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Every app named by an active rule, de-duplicated, with its real launcher
    // icon. Rules overlap constantly, so a plain flatMap would show Instagram
    // three times.
    val blockedApps: List<AppInfo> = remember(uiState.activeRules, uiState.appInfosByRule) {
        uiState.activeRules
            .flatMap { uiState.appInfosByRule[it.id].orEmpty() }
            .distinctBy { it.packageName }
    }
    val idleApps: List<AppInfo> = remember(uiState.rules, uiState.appInfosByRule, blockedApps) {
        val blockedPackages = blockedApps.mapTo(HashSet()) { it.packageName }
        uiState.rules
            .flatMap { uiState.appInfosByRule[it.id].orEmpty() }
            .distinctBy { it.packageName }
            .filterNot { it.packageName in blockedPackages }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand900)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoEyebrow("BeOffline", modifier = Modifier.weight(1f))
            if (uiState.activeRules.isNotEmpty()) {
                Text(
                    text = "Stop all",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(BoShape.Control)
                        .clickable(role = Role.Button) { viewModel.stopAll() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        PresenceHeadline(blockedCount = blockedApps.size)

        if (blockedApps.isNotEmpty() || idleApps.isNotEmpty()) {
            AppPresenceStrip(blocked = blockedApps, idle = idleApps)
        }

        BoFadedDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            val session = uiState.activeRules.firstOrNull {
                it.ruleType == RuleType.TIMER && it.timerStartedAt != null && it.timerDurationMinutes != null
            }
            if (session != null) {
                item(key = "session") {
                    SessionCard(
                        rule = session,
                        onEndEarly = { viewModel.deactivateRule(session) }
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }

            item(key = "rules-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    BoEyebrow("Your rules", modifier = Modifier.weight(1f))
                    if (uiState.rules.isNotEmpty()) {
                        Text(
                            text = "See all ${uiState.rules.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            modifier = Modifier
                                .clip(BoShape.Control)
                                .clickable(role = Role.Button, onClick = onSeeAllRules)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (uiState.rules.isEmpty()) {
                item(key = "empty") {
                    BoCard(modifier = Modifier.fillMaxWidth(), onClick = onCreateRule) {
                        Text(
                            "Make your first rule",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            "Pick a few apps and they stop reaching the internet — on a schedule, a timer, or until you say otherwise.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            items(uiState.rules.take(3), key = { it.id }) { rule ->
                CompactRuleRow(
                    rule = rule,
                    onToggle = { active ->
                        if (active) viewModel.activateRule(rule, onRequestVpn)
                        else viewModel.deactivateRule(rule)
                    }
                )
            }

            item(key = "applock") {
                Spacer(Modifier.height(9.dp))
                BoCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BoShape.SmallCard,
                    padding = 15.dp,
                    onClick = onOpenAppLock
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App Lock", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "Stop selected apps from opening at all",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The opening statement. A count, then the sentence that count belongs to —
 * written so the two read as one line rather than a stat and a caption.
 */
@Composable
private fun PresenceHeadline(blockedCount: Int) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // A soft accent bloom bleeding off the top-right corner. It is the only
        // decorative element on the screen.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 0.dp)
                .size(200.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentPrimary.copy(alpha = 0.20f), Color.Transparent),
                        radius = 260f
                    )
                )
        )
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BreathingDot(active = blockedCount > 0)
                Spacer(Modifier.width(9.dp))
                BoEyebrow(
                    text = if (blockedCount > 0) "Offline now" else "Everything online",
                    color = if (blockedCount > 0) AccentSecondary else TextTertiary
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (blockedCount > 0) {
                    "$blockedCount app${if (blockedCount == 1) "" else "s"}"
                } else {
                    "Nothing"
                },
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary
            )
            Text(
                text = if (blockedCount > 0) {
                    "can't reach the internet. The rest of your phone is untouched."
                } else {
                    "is being held back. Flip a rule on, or make one."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.padding(top = 9.dp, end = 40.dp)
            )
        }
    }
}

/** The live indicator: a lit core with a slow halo behind it. */
@Composable
private fun BreathingDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "breathe")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "halo"
    )
    Box(modifier = Modifier.size(19.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = alpha))
            )
        }
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) AccentSecondary else Brand600)
        )
    }
}

/**
 * Every app any rule names, blocked ones first. Blocked apps carry an accent
 * ring and a small offline badge; the rest are dimmed back into the ground.
 */
@Composable
private fun AppPresenceStrip(blocked: List<AppInfo>, idle: List<AppInfo>) {
    val blockedPackages = remember(blocked) { blocked.mapTo(HashSet()) { it.packageName } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        (blocked + idle).take(6).forEach { app ->
            val isBlocked = app.packageName in blockedPackages
            Box(modifier = Modifier.size(46.dp)) {
                BoAppIcon(
                    drawable = app.icon,
                    appName = app.appName,
                    size = 46.dp,
                    shape = RoundedCornerShape(12.dp),
                    dimmed = !isBlocked
                )
                if (isBlocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, AccentPrimary, RoundedCornerShape(12.dp))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Brand900)
                            .border(1.dp, AccentGlow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.WifiOff,
                            contentDescription = "Offline",
                            tint = AccentSecondary,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The one card that gets an accent outline: a timer actually counting down. */
@Composable
private fun SessionCard(rule: BlockRule, onEndEarly: () -> Unit) {
    val startedAt = rule.timerStartedAt ?: return
    val totalMillis = (rule.timerDurationMinutes ?: return) * 60_000L
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rule.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val remaining = (startedAt + totalMillis - now).coerceAtLeast(0L)
    val fraction = (remaining.toFloat() / totalMillis).coerceIn(0f, 1f)
    val minutes = remaining / 60_000
    val seconds = (remaining / 1_000) % 60

    BoCard(modifier = Modifier.fillMaxWidth(), accented = true, shape = BoShape.SmallCard) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(60.dp)) {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = Brand600,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = AccentPrimary,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentBright
                )
            }
            Spacer(Modifier.width(15.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    text = "$minutes:${seconds.toString().padStart(2, '0')} left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "End early",
                style = MaterialTheme.typography.labelLarge,
                color = AccentBright,
                modifier = Modifier
                    .clip(BoShape.Control)
                    .border(1.dp, AccentGlow, BoShape.Control)
                    .clickable(role = Role.Button, onClick = onEndEarly)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun CompactRuleRow(rule: BlockRule, onToggle: (Boolean) -> Unit) {
    BoCard(modifier = Modifier.fillMaxWidth(), shape = BoShape.SmallCard, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = rule.ruleType.homeIcon(),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = rule.homeMeta(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(10.dp))
            BoToggle(checked = rule.isActive, onCheckedChange = onToggle)
        }
    }
}

internal fun RuleType.homeIcon(): ImageVector = when (this) {
    RuleType.TIMER -> Icons.Outlined.HourglassEmpty
    RuleType.SCHEDULED -> Icons.Outlined.Schedule
    RuleType.PERMANENT -> Icons.Outlined.Block
}

internal fun BlockRule.homeMeta(): String {
    val apps = "${blockedPackages.size} app" + if (blockedPackages.size == 1) "" else "s"
    return when (ruleType) {
        RuleType.TIMER -> "$apps · timer · ${timerDurationMinutes ?: 0} min"
        RuleType.SCHEDULED -> "$apps · ${formatWindow()} · ${daysLabel()}"
        RuleType.PERMANENT -> "$apps · always on"
    }
}

private fun BlockRule.formatWindow(): String {
    val start = formatClock(startHour, startMinute)
    val end = formatClock(endHour, endMinute)
    return if (start == null || end == null) "scheduled" else "$start – $end"
}

private fun formatClock(hour: Int?, minute: Int?): String? {
    if (hour == null || minute == null) return null
    val suffix = if (hour < 12) "AM" else "PM"
    val display = when {
        hour % 12 == 0 -> 12
        else -> hour % 12
    }
    return "$display:${minute.toString().padStart(2, '0')} $suffix"
}

private fun BlockRule.daysLabel(): String {
    val days = activeDays.orEmpty()
    return when {
        days.isEmpty() || days.size == 7 -> "daily"
        days.sorted() == listOf(1, 2, 3, 4, 5) -> "Mon–Fri"
        days.sorted() == listOf(6, 7) -> "weekends"
        else -> "${days.size} days"
    }
}
