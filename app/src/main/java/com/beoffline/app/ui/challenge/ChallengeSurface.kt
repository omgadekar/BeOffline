package com.beoffline.app.ui.challenge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beoffline.app.openblock.ChallengeEngine
import com.beoffline.app.openblock.ChallengeKind
import com.beoffline.app.ui.theme.AccentBright
import com.beoffline.app.ui.theme.AccentPrimary
import com.beoffline.app.ui.theme.AccentSecondary
import com.beoffline.app.ui.theme.BoStepTrack
import com.beoffline.app.ui.theme.Brand600
import com.beoffline.app.ui.theme.Brand700
import com.beoffline.app.ui.theme.Brand800
import com.beoffline.app.ui.theme.BrandVoid
import com.beoffline.app.ui.theme.TextDisabled
import com.beoffline.app.ui.theme.TextMuted
import com.beoffline.app.ui.theme.TextPrimary
import com.beoffline.app.ui.theme.TextSecondary
import com.beoffline.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * The unlock challenge, start to finish: the unskippable wait, whichever kind
 * this level serves, and the flat confirmation at the end.
 *
 * Shared by the block overlay and the Settings preview so what you rehearse is
 * exactly what you'll face. The tone is fixed and deliberate — no praise on
 * success, no scolding on failure, and a mistake silently starts the set over.
 */
@Composable
fun ChallengeSurface(
    appName: String,
    level: Int,
    kind: ChallengeKind,
    rewardMinutes: Int,
    onSolved: (ChallengeKind) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** True while a stage needs the soft keyboard — the overlay window listens. */
    onNeedsKeyboard: (Boolean) -> Unit = {},
    /** Preview only: lets the user flip through kinds to see each one. */
    onSwitchKind: ((ChallengeKind) -> Unit)? = null
) {
    val difficulty = remember(level) { ChallengeEngine.difficultyFor(level) }
    var challenge by remember(kind, level) {
        mutableStateOf(ChallengeEngine.generate(kind, difficulty))
    }
    var waitingSeconds by remember(kind, level) { mutableIntStateOf(difficulty.preWaitSeconds) }
    var progress by remember(kind, level) { mutableIntStateOf(0) }
    var wrong by remember(kind, level) { mutableStateOf(false) }
    var granted by remember(kind, level) { mutableStateOf(false) }
    // Bumped on every restart. Stages that need to replay something (the
    // pattern flash) key off this rather than off the generated data, which
    // can legitimately come back identical.
    var attempt by remember(kind, level) { mutableIntStateOf(0) }

    LaunchedEffect(kind, level) {
        while (waitingSeconds > 0) {
            delay(1_000)
            waitingSeconds -= 1
        }
    }

    val needsKeyboard = kind == ChallengeKind.Retype && waitingSeconds == 0 && !granted
    DisposableEffect(needsKeyboard) {
        onNeedsKeyboard(needsKeyboard)
        onDispose { onNeedsKeyboard(false) }
    }

    /** Any mistake, in any kind, throws the whole set away. */
    fun restart() {
        challenge = ChallengeEngine.generate(kind, difficulty)
        progress = 0
        wrong = true
        attempt += 1
    }

    fun advance() {
        wrong = false
        if (progress + 1 >= challenge.steps) {
            granted = true
            onSolved(kind)
        } else {
            progress += 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BrandVoid)
            // This surface is genuinely full-bleed in both its homes — the
            // accessibility overlay window and a full-width Dialog — so unlike
            // the tabs it has no parent holding the bars off it.
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A wash of accent at the top edge, so the surface reads as ours rather
        // than as a system error screen.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(AccentPrimary.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )

        Spacer(Modifier.height(40.dp))
        Text(
            text = if (granted) "UNLOCKED" else "${appName.uppercase()} IS LOCKED",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        Spacer(Modifier.height(22.dp))
        BoStepTrack(total = challenge.steps, done = progress)

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            when {
                granted -> GrantedStage(appName = appName, rewardMinutes = rewardMinutes)
                waitingSeconds > 0 -> WaitStage(secondsLeft = waitingSeconds, level = level)
                else -> when (val current = challenge) {
                    is ChallengeEngine.Challenge.Arithmetic -> ArithmeticStage(
                        problem = current.problems[progress],
                        progressLabel = "${progress + 1} of ${current.steps}",
                        wrong = wrong,
                        onCorrect = ::advance,
                        onWrong = ::restart
                    )
                    is ChallengeEngine.Challenge.Retype -> RetypeStage(
                        sentence = current.sentences[progress],
                        progressLabel = "${progress + 1} of ${current.steps}",
                        wrong = wrong,
                        onCorrect = ::advance,
                        onWrong = ::restart
                    )
                    is ChallengeEngine.Challenge.Pattern -> PatternStage(
                        sequence = current.sequence,
                        stepMillis = current.stepMillis,
                        progress = progress,
                        wrong = wrong,
                        attemptKey = attempt,
                        onCorrect = ::advance,
                        onWrong = ::restart
                    )
                    is ChallengeEngine.Challenge.Hold -> HoldStage(
                        seconds = current.seconds,
                        onCompleted = ::advance
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(bottom = 30.dp)
        ) {
            Text(
                text = kind.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextDisabled,
                textAlign = TextAlign.Center
            )
            if (onSwitchKind != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChallengeKind.entries.forEach { option ->
                        val on = option == kind
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) AccentBright else TextTertiary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (on) AccentPrimary.copy(alpha = 0.20f) else Color.Transparent
                                )
                                .border(1.dp, if (on) AccentPrimary else Brand600, CircleShape)
                                .clickable(role = Role.RadioButton) { onSwitchKind(option) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Text(
                text = "Never mind",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDisabled,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = onCancel)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun WaitStage(secondsLeft: Int, level: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 110.dp)
    ) {
        Text(
            text = "${secondsLeft}s",
            style = MaterialTheme.typography.displayLarge,
            color = TextPrimary
        )
        Text(
            text = "This wait can't be skipped. Unlock #${level + 1} of this session.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp).widthIn(max = 250.dp)
        )
    }
}

@Composable
private fun GrantedStage(appName: String, rewardMinutes: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 150.dp)
    ) {
        // Flat on purpose: no tick, no colour, nothing that reads as a reward.
        Text(
            text = "Unlocked for $rewardMinutes minute${if (rewardMinutes == 1) "" else "s"}.",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Open $appName again to use it. The next unlock this session is harder.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 250.dp)
        )
    }
}

@Composable
private fun ArithmeticStage(
    problem: ChallengeEngine.Problem,
    progressLabel: String,
    wrong: Boolean,
    onCorrect: () -> Unit,
    onWrong: () -> Unit
) {
    var input by remember(problem) { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 40.dp)
    ) {
        Text(progressLabel, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(
            text = problem.text,
            style = MaterialTheme.typography.displayLarge,
            color = TextPrimary,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            text = input.ifEmpty { " " },
            style = MaterialTheme.typography.headlineMedium,
            color = AccentSecondary,
            modifier = Modifier.padding(top = 16.dp).height(34.dp)
        )
        Text(
            text = if (wrong) "Wrong. Starting the set over." else " ",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.height(18.dp)
        )
        Spacer(Modifier.height(14.dp))
        Numpad(
            onDigit = { digit -> if (input.length < 6) input += digit },
            onBackspace = { input = input.dropLast(1) },
            onSubmit = {
                if (input.isNotEmpty()) {
                    if (input.toIntOrNull() == problem.answer) onCorrect() else onWrong()
                    input = ""
                }
            }
        )
    }
}

@Composable
private fun Numpad(onDigit: (String) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "OK")
    )
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { label ->
                    Box(
                        modifier = Modifier
                            .width(88.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brand800)
                            .border(1.dp, Brand600, RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) {
                                when (label) {
                                    "⌫" -> onBackspace()
                                    "OK" -> onSubmit()
                                    else -> onDigit(label)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (label == "OK") AccentBright else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetypeStage(
    sentence: String,
    progressLabel: String,
    wrong: Boolean,
    onCorrect: () -> Unit,
    onWrong: () -> Unit
) {
    var input by remember(sentence) { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 50.dp)
    ) {
        Text(progressLabel, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(
            text = sentence,
            style = MaterialTheme.typography.titleLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = "Type it exactly. Punctuation counts.",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            modifier = Modifier.padding(top = 10.dp)
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Start typing", color = TextDisabled) },
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = Brand800,
                unfocusedContainerColor = Brand800,
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = Brand600,
                cursorColor = AccentPrimary
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 26.dp)
        )
        Text(
            text = if (wrong) "Not an exact match. Starting the set over." else " ",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 12.dp).height(18.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AccentPrimary.copy(alpha = 0.14f))
                .border(1.dp, AccentPrimary, RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) {
                    if (input.trim() == sentence) onCorrect() else onWrong()
                    input = ""
                }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Check", style = MaterialTheme.typography.bodyLarge, color = AccentBright)
        }
    }
}

@Composable
private fun PatternStage(
    sequence: List<Int>,
    stepMillis: Long,
    progress: Int,
    wrong: Boolean,
    attemptKey: Int,
    onCorrect: () -> Unit,
    onWrong: () -> Unit
) {
    // The sequence is PLAYED BACK, one tile at a time, in order — lighting all
    // of them together shows you the set but never the order, which is the only
    // thing you're being asked to remember.
    //
    // It plays once per attempt. Replaying it after a mistake is what makes a
    // long pattern expensive rather than impossible; replaying it per tap would
    // make it free.
    var litTile by remember(attemptKey) { mutableIntStateOf(-1) }
    var playing by remember(attemptKey) { mutableStateOf(true) }
    var shownSoFar by remember(attemptKey) { mutableIntStateOf(0) }

    LaunchedEffect(attemptKey) {
        playing = true
        litTile = -1
        shownSoFar = 0
        // A beat before the first flash, so it isn't already over by the time
        // you've looked up from the "watch" line.
        delay(500)
        sequence.forEachIndexed { step, tile ->
            shownSoFar = step + 1
            litTile = tile
            delay(stepMillis)
            // The gap matters as much as the flash: without it, the same tile
            // twice in a row would look like one long flash.
            litTile = -1
            delay(stepMillis / 2)
        }
        playing = false
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 44.dp)
    ) {
        Text(
            text = if (playing) "$shownSoFar of ${sequence.size}" else "${progress + 1} of ${sequence.size}",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Text(
            text = if (playing) "Watch the order" else "Now repeat it",
            style = MaterialTheme.typography.titleLarge,
            color = TextMuted,
            modifier = Modifier.padding(top = 16.dp)
        )
        Spacer(Modifier.height(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (0..2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0..2).forEach { column ->
                        val index = row * 3 + column
                        val lit = litTile == index
                        key(index) {
                            Box(
                                modifier = Modifier
                                    .size(86.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (lit) AccentPrimary.copy(alpha = 0.34f) else Brand800
                                    )
                                    .border(
                                        1.dp,
                                        if (lit) AccentPrimary else Brand600,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = !playing, role = Role.Button) {
                                        if (sequence[progress] == index) onCorrect() else onWrong()
                                    }
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = when {
                wrong && !playing -> "Wrong tile. Watch it again."
                playing -> "Don't tap yet."
                else -> " "
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 18.dp).height(18.dp)
        )
    }
}

@Composable
private fun HoldStage(seconds: Int, onCompleted: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }

    LaunchedEffect(holding) {
        if (!holding) {
            elapsed = 0
            return@LaunchedEffect
        }
        while (elapsed < seconds) {
            delay(1_000)
            elapsed += 1
        }
        holding = false
        onCompleted()
    }

    val fraction = (elapsed.toFloat() / seconds).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 70.dp)
    ) {
        Text(
            text = "Hold the circle for $seconds seconds. Let go and it resets.",
            style = MaterialTheme.typography.titleLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 250.dp)
        )
        Spacer(Modifier.height(46.dp))
        Box(
            modifier = Modifier
                .size(200.dp)
                .pointerInput(seconds) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            tryAwaitRelease()
                            // Releasing early is the whole point: nothing banks.
                            holding = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Brand700,
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
            Box(
                modifier = Modifier
                    .size(156.dp)
                    .clip(CircleShape)
                    .background(Brand800)
                    .border(1.dp, Brand600, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (holding) "${seconds - elapsed}s" else "Hold",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextSecondary
                )
            }
        }
        Text(
            text = if (holding) "Keep holding." else "Press and hold.",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            modifier = Modifier.padding(top = 26.dp)
        )
    }
}
