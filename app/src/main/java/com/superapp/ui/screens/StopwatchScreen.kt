package com.superapp.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════
//  ENUMS
// ═══════════════════════════════════════════════════════════════

private enum class TimerMode { STOPWATCH, COUNTDOWN, INTERVAL_TIMER }

private enum class AlertType { VIBRATION, ALERT_TEXT, FLASH_SCREEN }

// ═══════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(navController: NavController) {
    // ── Mode ──────────────────────────────────────────────
    var timerMode by remember { mutableStateOf(TimerMode.STOPWATCH) }

    // ── Stopwatch state ───────────────────────────────────
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var laps by remember { mutableStateOf(listOf<Long>()) }
    var lastLapTime by remember { mutableLongStateOf(0L) }

    // ── Countdown state ───────────────────────────────────
    var countdownConfigMs by remember { mutableLongStateOf(5 * 60 * 1000L) } // default 5 min
    var countdownRemainingMs by remember { mutableLongStateOf(5 * 60 * 1000L) }
    var isCountdownFinished by remember { mutableStateOf(false) }
    var alertType by remember { mutableStateOf(AlertType.VIBRATION) }

    // ── Interval Timer state ──────────────────────────────
    var intervalWorkMs by remember { mutableLongStateOf(30_000L) }
    var intervalRestMs by remember { mutableLongStateOf(10_000L) }
    var intervalRounds by remember { mutableStateOf(8) }
    var intervalCurrentRound by remember { mutableStateOf(1) }
    var intervalPhaseRemainingMs by remember { mutableLongStateOf(30_000L) }
    var intervalIsWork by remember { mutableStateOf(true) }
    var intervalPreStart by remember { mutableStateOf<Int?>(null) }
    var isIntervalRunning by remember { mutableStateOf(false) }
    var isIntervalCompleted by remember { mutableStateOf(false) }

    // ── Screen flash ──────────────────────────────────────
    var flashTrigger by remember { mutableStateOf(0L) }
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(flashTrigger) {
        if (flashTrigger > 0L) {
            flashAlpha.snapTo(0.55f)
            flashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        }
    }

    val context = LocalContext.current

    // ── Best / worst lap calculation ──────────────────────
    val bestLap = remember(laps) { if (laps.size < 2) null else laps.minOrNull() }
    val worstLap = remember(laps) { if (laps.size < 2) null else laps.maxOrNull() }

    // ── Stopwatch stats ───────────────────────────────────
    val showStats = timerMode == TimerMode.STOPWATCH && !isRunning && elapsedMs > 0
    val avgLap = remember(laps, elapsedMs) {
        if (laps.isNotEmpty()) elapsedMs / laps.size else 0L
    }
    val totalLapTime = remember(laps) { laps.sum() }

    // ── Timers ────────────────────────────────────────────

    // Stopwatch ticker
    LaunchedEffect(isRunning, timerMode) {
        if (isRunning && timerMode == TimerMode.STOPWATCH) {
            val startTime = System.currentTimeMillis() - elapsedMs
            while (isRunning) {
                delay(10)
                if (timerMode != TimerMode.STOPWATCH) break
                elapsedMs = System.currentTimeMillis() - startTime
            }
        }
    }

    // Countdown ticker
    LaunchedEffect(isRunning, timerMode) {
        if (isRunning && timerMode == TimerMode.COUNTDOWN && countdownRemainingMs > 0) {
            val initialRemaining = countdownRemainingMs
            val startTime = System.currentTimeMillis()
            while (true) {
                delay(10)
                if (!isRunning || timerMode != TimerMode.COUNTDOWN) break
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (initialRemaining - elapsed).coerceAtLeast(0L)
                countdownRemainingMs = remaining
                if (remaining <= 0) {
                    countdownRemainingMs = 0L
                    isCountdownFinished = true
                    isRunning = false
                    triggerAlert(context, alertType, { flashTrigger = System.nanoTime() })
                    break
                }
            }
        }
    }

    // Interval Timer ticker (pre-start + phase loop)
    LaunchedEffect(isIntervalRunning, timerMode, intervalPreStart) {
        if (timerMode != TimerMode.INTERVAL_TIMER || !isIntervalRunning) return@LaunchedEffect

        // ── Pre‑start countdown (3, 2, 1, GO!) ──
        if (intervalPreStart != null) {
            var count = intervalPreStart ?: 3
            while (count > 0 && isIntervalRunning) {
                delay(1000)
                if (!isIntervalRunning) return@LaunchedEffect
                count--
                intervalPreStart = if (count > 0) count else null // null = GO!
            }
            if (!isIntervalRunning) return@LaunchedEffect

            // Begin first work phase
            intervalCurrentRound = 1
            intervalIsWork = true
            intervalPhaseRemainingMs = intervalWorkMs
            isIntervalCompleted = false
            return@LaunchedEffect // re‑launch because intervalPreStart changed
        }

        // ── Phase loop (WORK ↔ REST) ──
        while (isIntervalRunning && !isIntervalCompleted) {
            val remainingAtStart = intervalPhaseRemainingMs
            val phaseStartTime = System.currentTimeMillis()

            while (isIntervalRunning) {
                val elapsed = System.currentTimeMillis() - phaseStartTime
                val remaining = (remainingAtStart - elapsed).coerceAtLeast(0L)
                intervalPhaseRemainingMs = remaining
                if (remaining <= 0) break
                delay(10)
            }
            if (!isIntervalRunning) break

            // Phase ended – alert
            triggerAlert(context, alertType, { flashTrigger = System.nanoTime() })

            if (intervalIsWork) {
                // Work → Rest (or finish)
                if (intervalCurrentRound >= intervalRounds) {
                    isIntervalCompleted = true
                    isIntervalRunning = false
                    break
                }
                intervalIsWork = false
                intervalPhaseRemainingMs = intervalRestMs
            } else {
                // Rest → next round Work
                intervalIsWork = true
                intervalCurrentRound++
                intervalPhaseRemainingMs = intervalWorkMs
            }
        }
    }

    // ── Format helpers ────────────────────────────────────
    fun formatTime(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms % 60000) / 1000
        val centis = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, centis)
    }

    fun formatCountdown(ms: Long): String {
        val totalSecs = ms / 1000
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    // ── Actions ───────────────────────────────────────────
    fun toggleStartStop() {
        when (timerMode) {
            TimerMode.STOPWATCH -> {
                isRunning = !isRunning
            }
            TimerMode.COUNTDOWN -> {
                when {
                    isCountdownFinished -> {
                        isCountdownFinished = false
                        countdownRemainingMs = countdownConfigMs
                        isRunning = true
                    }
                    countdownRemainingMs > 0 -> {
                        isRunning = !isRunning
                    }
                }
            }
            TimerMode.INTERVAL_TIMER -> {
                // Handled by interval-specific controls
            }
        }
    }

    fun reset() {
        when (timerMode) {
            TimerMode.STOPWATCH -> {
                isRunning = false
                elapsedMs = 0L
                laps = emptyList()
                lastLapTime = 0L
            }
            TimerMode.COUNTDOWN -> {
                isRunning = false
                isCountdownFinished = false
                countdownRemainingMs = countdownConfigMs
            }
            TimerMode.INTERVAL_TIMER -> {
                isIntervalRunning = false
                isIntervalCompleted = false
                intervalPreStart = null
                intervalCurrentRound = 1
                intervalIsWork = true
                intervalPhaseRemainingMs = intervalWorkMs
            }
        }
    }

    fun addLap() {
        val lapDuration = elapsedMs - lastLapTime
        laps = laps + lapDuration
        lastLapTime = elapsedMs
    }

    fun switchMode() {
        // Stop any running timers
        isRunning = false
        isIntervalRunning = false
        isIntervalCompleted = false
        intervalPreStart = null

        timerMode = when (timerMode) {
            TimerMode.STOPWATCH -> TimerMode.COUNTDOWN
            TimerMode.COUNTDOWN -> TimerMode.INTERVAL_TIMER
            TimerMode.INTERVAL_TIMER -> TimerMode.STOPWATCH
        }

        // Reset inactive modes
        when (timerMode) {
            TimerMode.STOPWATCH -> {
                countdownRemainingMs = countdownConfigMs
                isCountdownFinished = false
                intervalCurrentRound = 1
                intervalIsWork = true
                intervalPhaseRemainingMs = intervalWorkMs
            }
            TimerMode.COUNTDOWN -> {
                elapsedMs = 0L
                laps = emptyList()
                lastLapTime = 0L
                intervalCurrentRound = 1
                intervalIsWork = true
                intervalPhaseRemainingMs = intervalWorkMs
            }
            TimerMode.INTERVAL_TIMER -> {
                elapsedMs = 0L
                laps = emptyList()
                lastLapTime = 0L
                countdownRemainingMs = countdownConfigMs
                isCountdownFinished = false
            }
        }
    }

    // ── Animated background ───────────────────────────────
    val containerBg by animateColorAsState(
        targetValue = when {
            timerMode == TimerMode.COUNTDOWN && isCountdownFinished ->
                Color(0xFFFF6B6B).copy(alpha = 0.15f)
            timerMode == TimerMode.INTERVAL_TIMER && isIntervalCompleted ->
                Color(0xFF4CAF50).copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.background
        },
        animationSpec = tween(600),
        label = "bg"
    )

    // ── UI ────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (timerMode) {
                                TimerMode.STOPWATCH -> "Bấm Giờ"
                                TimerMode.COUNTDOWN -> "Đếm Ngược"
                                TimerMode.INTERVAL_TIMER -> "Interval Timer"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                        }
                    },
                    actions = {
                        IconButton(onClick = ::switchMode) {
                            Icon(
                                when (timerMode) {
                                    TimerMode.STOPWATCH -> Icons.Default.HourglassEmpty
                                    TimerMode.COUNTDOWN -> Icons.Default.Repeat
                                    TimerMode.INTERVAL_TIMER -> Icons.Default.Timer
                                },
                                contentDescription = when (timerMode) {
                                    TimerMode.STOPWATCH -> "Sang đếm ngược"
                                    TimerMode.COUNTDOWN -> "Sang interval"
                                    TimerMode.INTERVAL_TIMER -> "Sang bấm giờ"
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = containerBg
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                AnimatedContent(
                    targetState = timerMode,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                    label = "mode-switch"
                ) { mode ->
                    when (mode) {
                        TimerMode.STOPWATCH -> {
                            // ═══════════ STOPWATCH ═══════════
                            StopwatchModeContent(
                                elapsedMs = elapsedMs,
                                isRunning = isRunning,
                                laps = laps,
                                bestLap = bestLap,
                                worstLap = worstLap,
                                showStats = showStats,
                                avgLap = avgLap,
                                totalLapTime = totalLapTime,
                                formatTime = ::formatTime,
                                onLapOrReset = { if (isRunning) addLap() else reset() },
                                onStartStop = ::toggleStartStop
                            )
                        }
                        TimerMode.COUNTDOWN -> {
                            // ═══════════ COUNTDOWN ═══════════
                            CountdownModeContent(
                                configMs = countdownConfigMs,
                                remainingMs = countdownRemainingMs,
                                isRunning = isRunning,
                                isFinished = isCountdownFinished,
                                alertType = alertType,
                                formatCountdown = ::formatCountdown,
                                onSetConfig = { newConfig ->
                                    countdownConfigMs = newConfig
                                    if (!isRunning && !isCountdownFinished) {
                                        countdownRemainingMs = newConfig
                                    }
                                },
                                onSetAlertType = { alertType = it },
                                onStartStop = ::toggleStartStop,
                                onReset = ::reset
                            )
                        }
                        TimerMode.INTERVAL_TIMER -> {
                            // ═══════════ INTERVAL TIMER ═══════════
                            IntervalTimerModeContent(
                                workMs = intervalWorkMs,
                                restMs = intervalRestMs,
                                rounds = intervalRounds,
                                currentRound = intervalCurrentRound,
                                phaseRemainingMs = intervalPhaseRemainingMs,
                                isWorkPhase = intervalIsWork,
                                preStartCountdown = intervalPreStart,
                                isRunning = isIntervalRunning,
                                isCompleted = isIntervalCompleted,
                                formatTime = ::formatTime,
                                formatCountdown = ::formatCountdown,
                                onSetWork = { intervalWorkMs = it },
                                onSetRest = { intervalRestMs = it },
                                onSetRounds = { intervalRounds = it },
                                onStart = {
                                    isIntervalRunning = true
                                    isIntervalCompleted = false
                                    intervalPreStart = 3
                                    intervalCurrentRound = 1
                                    intervalIsWork = true
                                    intervalPhaseRemainingMs = intervalWorkMs
                                },
                                onPause = { isIntervalRunning = false },
                                onResume = { isIntervalRunning = true },
                                onReset = ::reset
                            )
                        }
                    }
                }
            }
        }

        // ── Screen flash overlay ──
        if (flashAlpha.value > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  STOPWATCH CONTENT
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StopwatchModeContent(
    elapsedMs: Long,
    isRunning: Boolean,
    laps: List<Long>,
    bestLap: Long?,
    worstLap: Long?,
    showStats: Boolean,
    avgLap: Long,
    totalLapTime: Long,
    formatTime: (Long) -> String,
    onLapOrReset: () -> Unit,
    onStartStop: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Circular display ──
        val scaleAnim by animateFloatAsState(
            targetValue = if (isRunning) 1f else 0.95f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
            label = "scale"
        )

        Card(
            modifier = Modifier
                .size(250.dp)
                .scale(scaleAnim),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatTime(elapsedMs),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            isRunning -> "ĐANG CHẠY"
                            elapsedMs > 0 -> "ĐÃ DỪNG"
                            else -> "SẴN SÀNG"
                        },
                        fontSize = 12.sp,
                        color = if (isRunning) Color(0xFFFFD93D)
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Controls ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onLapOrReset,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                enabled = isRunning || elapsedMs > 0
            ) {
                Icon(
                    if (isRunning) Icons.Default.Flag else Icons.Default.Replay,
                    contentDescription = if (isRunning) "Vòng" else "Đặt lại",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            FilledIconButton(
                onClick = onStartStop,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRunning) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
                )
            ) {
                Icon(
                    if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Dừng" else "Bắt đầu",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Symmetry spacer
            Spacer(modifier = Modifier.size(56.dp))
        }

        // ── Statistics panel (shown when stopped) ──
        if (showStats) {
            Spacer(modifier = Modifier.height(20.dp))
            StopwatchStats(
                elapsedMs = elapsedMs,
                laps = laps,
                bestLap = bestLap,
                worstLap = worstLap,
                avgLap = avgLap,
                totalLapTime = totalLapTime,
                formatTime = formatTime
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Lap list ──
        if (laps.isNotEmpty()) {
            LapList(
                laps = laps,
                bestLap = bestLap,
                worstLap = worstLap,
                formatTime = formatTime
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
//  STOPWATCH STATS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StopwatchStats(
    elapsedMs: Long,
    laps: List<Long>,
    bestLap: Long?,
    worstLap: Long?,
    avgLap: Long,
    totalLapTime: Long,
    formatTime: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "📊 Thống kê",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            StatRow("Tổng thời gian", formatTime(elapsedMs))
            StatRow("Số vòng (laps)", "${laps.size}")
            if (laps.isNotEmpty()) {
                StatRow("Trung bình mỗi vòng", formatTime(avgLap))
                StatRow("Tổng thời gian laps", formatTime(totalLapTime))
            }
            if (bestLap != null) {
                StatRow("Nhanh nhất", formatTime(bestLap), valueColor = Color(0xFF4CAF50))
            }
            if (worstLap != null) {
                StatRow("Chậm nhất", formatTime(worstLap), valueColor = Color(0xFFE53935))
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  LAP LIST  (with best / worst indicators)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun LapList(
    laps: List<Long>,
    bestLap: Long?,
    worstLap: Long?,
    formatTime: (Long) -> String
) {
    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Vòng", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("Thời gian", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
    Spacer(modifier = Modifier.height(4.dp))

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(laps.reversed()) { idx, lapMs ->
            val lapNum = laps.size - idx
            val isBest = lapMs == bestLap && bestLap != worstLap
            val isWorst = lapMs == worstLap && bestLap != worstLap
            val lapColor = when {
                isBest -> Color(0xFF4CAF50)
                isWorst -> Color(0xFFE53935)
                else -> MaterialTheme.colorScheme.onBackground
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Label + indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Vòng $lapNum",
                        fontSize = 14.sp,
                        fontWeight = if (isBest || isWorst) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    when {
                        isBest -> {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Tốt nhất",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                "Best",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        isWorst -> {
                            Text(
                                "Worst",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935)
                            )
                        }
                    }
                }

                // Time
                Text(
                    formatTime(lapMs),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isBest || isWorst) FontWeight.SemiBold else FontWeight.Normal,
                    color = lapColor
                )
            }
            if (idx < laps.size - 1) {
                Divider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  COUNTDOWN CONTENT
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CountdownModeContent(
    configMs: Long,
    remainingMs: Long,
    isRunning: Boolean,
    isFinished: Boolean,
    alertType: AlertType,
    formatCountdown: (Long) -> String,
    onSetConfig: (Long) -> Unit,
    onSetAlertType: (AlertType) -> Unit,
    onStartStop: () -> Unit,
    onReset: () -> Unit
) {
    val isConfiguring = !isRunning && !isFinished && remainingMs == configMs

    AnimatedContent(
        targetState = when {
            isFinished -> 2
            isConfiguring -> 0
            else -> 1
        },
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
        label = "countdown-state"
    ) { state ->
        when (state) {
            0 -> CountdownConfig(
                configMs = configMs,
                alertType = alertType,
                formatCountdown = formatCountdown,
                onSetConfig = onSetConfig,
                onSetAlertType = onSetAlertType,
                onStart = onStartStop
            )
            1 -> CountdownActive(
                remainingMs = remainingMs,
                configMs = configMs,
                isRunning = isRunning,
                formatCountdown = formatCountdown,
                onStartStop = onStartStop,
                onReset = onReset
            )
            2 -> CountdownFinished(
                alertType = alertType,
                onReset = onReset
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  COUNTDOWN — CONFIGURATION (extended presets + alert picker)
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownConfig(
    configMs: Long,
    alertType: AlertType,
    formatCountdown: (Long) -> String,
    onSetConfig: (Long) -> Unit,
    onSetAlertType: (AlertType) -> Unit,
    onStart: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── Extended presets (scrollable chips) ──
        Text(
            text = "Chọn thời gian",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))

        val presets = listOf(
            15_000L to "15s",
            30_000L to "30s",
            45_000L to "45s",
            60_000L to "1ph",
            120_000L to "2ph",
            180_000L to "3ph",
            300_000L to "5ph",
            600_000L to "10ph",
            900_000L to "15ph",
            1_800_000L to "30ph"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presets) { (ms, label) ->
                FilterChip(
                    selected = configMs == ms,
                    onClick = { onSetConfig(ms) },
                    label = { Text(label, fontWeight = FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Time circle ──
        Card(
            modifier = Modifier.size(250.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatCountdown(configMs),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CHỈNH THỜI GIAN",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Fine-tune ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FineTuneButton("-1ph", enabled = configMs >= 120_000) {
                onSetConfig((configMs - 60_000).coerceAtLeast(15_000L))
            }
            FineTuneButton("-10s", enabled = configMs >= 25_000) {
                onSetConfig((configMs - 10_000).coerceAtLeast(15_000L))
            }
            FineTuneButton("+10s", enabled = configMs < 99 * 60 * 1000L - 10_000) {
                onSetConfig((configMs + 10_000).coerceAtMost(99 * 60 * 1000L))
            }
            FineTuneButton("+1ph", enabled = configMs < 99 * 60 * 1000L - 60_000) {
                onSetConfig((configMs + 60_000).coerceAtMost(99 * 60 * 1000L))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Alert type selector ──
        Text(
            text = "Âm thanh báo",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AlertType.entries.forEach { type ->
                FilterChip(
                    selected = alertType == type,
                    onClick = { onSetAlertType(type) },
                    label = {
                        Text(
                            when (type) {
                                AlertType.VIBRATION -> "📳 Rung"
                                AlertType.ALERT_TEXT -> "🔔 Báo động"
                                AlertType.FLASH_SCREEN -> "💡 Flash"
                            },
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Start ──
        FilledIconButton(
            onClick = onStart,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF4ECDC4)
            )
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Bắt đầu",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
//  COUNTDOWN — RUNNING / PAUSED
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CountdownActive(
    remainingMs: Long,
    configMs: Long,
    isRunning: Boolean,
    formatCountdown: (Long) -> String,
    onStartStop: () -> Unit,
    onReset: () -> Unit
) {
    val progress = remainingMs.toFloat() / configMs.toFloat().coerceAtLeast(1f)

    val displayColor = when {
        remainingMs < 10_000 -> Color(0xFFE53935)
        remainingMs < 30_000 -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.onBackground
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── Time circle ──
        Card(
            modifier = Modifier.size(250.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatCountdown(remainingMs),
                        fontSize = 44.sp,
                        fontWeight = if (remainingMs < 10_000) FontWeight.Bold else FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        color = displayColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .width(160.dp)
                            .height(4.dp),
                        color = when {
                            remainingMs < 10_000 -> Color(0xFFE53935)
                            remainingMs < 30_000 -> Color(0xFFFF9800)
                            else -> Color(0xFF4ECDC4)
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRunning) "ĐANG ĐẾM" else "ĐÃ DỪNG",
                        fontSize = 12.sp,
                        color = if (isRunning) Color(0xFFFFD93D)
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Controls ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stop / reset
            FilledIconButton(
                onClick = onReset,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Dừng",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Play / Pause
            FilledIconButton(
                onClick = onStartStop,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRunning) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
                )
            ) {
                Icon(
                    if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Tạm dừng" else "Tiếp tục",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Symmetry spacer
            Spacer(modifier = Modifier.size(56.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Back to config
        TextButton(onClick = onReset) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Cài đặt lại")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  COUNTDOWN — FINISHED  (with alert type)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CountdownFinished(
    alertType: AlertType,
    onReset: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "finished-pulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        Card(
            modifier = Modifier.size(250.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFF6B6B).copy(alpha = 0.2f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "00:00",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFF6B6B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Visual alert indicator
                    if (alertType == AlertType.ALERT_TEXT) {
                        Text(
                            text = "🔔🔔🔔",
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "⏰  HẾT GIỜ!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B6B),
            modifier = Modifier.scale(pulseScale)
        )

        if (alertType == AlertType.ALERT_TEXT) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "BÁO ĐỘNG",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B).copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        FilledIconButton(
            onClick = onReset,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF4ECDC4)
            )
        ) {
            Icon(
                Icons.Default.Replay,
                contentDescription = "Đặt lại",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Nhấn để đặt lại",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  INTERVAL TIMER CONTENT  (dispatches to sub-states)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun IntervalTimerModeContent(
    workMs: Long,
    restMs: Long,
    rounds: Int,
    currentRound: Int,
    phaseRemainingMs: Long,
    isWorkPhase: Boolean,
    preStartCountdown: Int?,
    isRunning: Boolean,
    isCompleted: Boolean,
    formatTime: (Long) -> String,
    formatCountdown: (Long) -> String,
    onSetWork: (Long) -> Unit,
    onSetRest: (Long) -> Unit,
    onSetRounds: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit
) {
    val state = when {
        isCompleted -> 3
        isRunning && preStartCountdown != null -> 0 // pre-start
        isRunning && preStartCountdown == null -> 2 // active
        !isRunning && preStartCountdown == null && currentRound == 1 && !isCompleted -> 1 // config
        !isRunning && !isCompleted -> 2 // paused during active
        else -> 1
    }

    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(tween(250)) togetherWith fadeOut(tween(200))
        },
        label = "interval-state"
    ) { s ->
        when (s) {
            0 -> IntervalPreStart(
                count = preStartCountdown ?: 3,
                onSkip = { /* pre-start will finish naturally */ }
            )
            1 -> IntervalTimerConfig(
                workMs = workMs,
                restMs = restMs,
                rounds = rounds,
                formatTime = formatTime,
                onSetWork = onSetWork,
                onSetRest = onSetRest,
                onSetRounds = onSetRounds,
                onStart = onStart
            )
            2 -> IntervalActive(
                currentRound = currentRound,
                rounds = rounds,
                phaseRemainingMs = phaseRemainingMs,
                workMs = workMs,
                restMs = restMs,
                isWorkPhase = isWorkPhase,
                isRunning = isRunning,
                formatTime = formatTime,
                formatCountdown = formatCountdown,
                onPause = onPause,
                onResume = onResume,
                onReset = onReset
            )
            3 -> IntervalCompleted(onReset = onReset)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  INTERVAL — PRE-START COUNTDOWN (3, 2, 1, GO!)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun IntervalPreStart(
    count: Int,
    onSkip: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "prestart-pulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.size(280.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        fadeIn(tween(200)) + slideInVertically { -it / 4 } togetherWith
                            fadeOut(tween(150)) + slideOutVertically { it / 4 }
                    },
                    label = "prestart-number"
                ) { c ->
                    Text(
                        text = if (c > 0) "$c" else "GO!",
                        fontSize = if (c > 0) 72.sp else 56.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            c > 0 -> MaterialTheme.colorScheme.primary
                            else -> Color(0xFF4ECDC4)
                        },
                        modifier = Modifier.scale(if (c > 0) pulseScale else 1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (count > 0) "Chuẩn bị..." else "Bắt đầu!",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onSkip) {
            Text("Bỏ qua")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  INTERVAL — CONFIGURATION
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalTimerConfig(
    workMs: Long,
    restMs: Long,
    rounds: Int,
    formatTime: (Long) -> String,
    onSetWork: (Long) -> Unit,
    onSetRest: (Long) -> Unit,
    onSetRounds: (Int) -> Unit,
    onStart: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // ── Work time ──
        Text(
            text = "🏋️  Thời gian tập (Work)",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))

        val workPresets = listOf(
            15_000L to "15s",
            30_000L to "30s",
            45_000L to "45s",
            60_000L to "1ph",
            120_000L to "2ph"
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(workPresets) { (ms, label) ->
                FilterChip(
                    selected = workMs == ms,
                    onClick = { onSetWork(ms) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4ECDC4).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFF4ECDC4)
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FineTuneButton("-10s", enabled = workMs >= 15_000 + 10_000) {
                onSetWork((workMs - 10_000).coerceAtLeast(5_000L))
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4ECDC4).copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = formatTime(workMs),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                )
            }
            FineTuneButton("+10s", enabled = workMs < 10 * 60 * 1000L - 10_000) {
                onSetWork((workMs + 10_000).coerceAtMost(10 * 60 * 1000L))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Rest time ──
        Text(
            text = "😮‍💨  Thời gian nghỉ (Rest)",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))

        val restPresets = listOf(
            5_000L to "5s",
            10_000L to "10s",
            15_000L to "15s",
            30_000L to "30s",
            60_000L to "1ph"
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(restPresets) { (ms, label) ->
                FilterChip(
                    selected = restMs == ms,
                    onClick = { onSetRest(ms) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF9800).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFFFF9800)
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FineTuneButton("-5s", enabled = restMs >= 10_000) {
                onSetRest((restMs - 5_000).coerceAtLeast(3_000L))
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF9800).copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = formatTime(restMs),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFF9800)
                )
            }
            FineTuneButton("+5s", enabled = restMs < 5 * 60 * 1000L - 5_000) {
                onSetRest((restMs + 5_000).coerceAtMost(5 * 60 * 1000L))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Rounds ──
        Text(
            text = "🔄  Số vòng (Rounds)",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { onSetRounds((rounds - 1).coerceAtLeast(1)) },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                enabled = rounds > 1
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Giảm vòng",
                    tint = MaterialTheme.colorScheme.onSurface)
            }

            Card(
                modifier = Modifier.width(80.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "$rounds",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            FilledIconButton(
                onClick = { onSetRounds((rounds + 1).coerceAtMost(99)) },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                enabled = rounds < 99
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tăng vòng",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Summary ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tổng thời gian", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    Text(
                        formatTime(workMs * rounds + restMs * (rounds - 1)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Số vòng", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    Text(
                        "$rounds",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Start ──
        FilledIconButton(
            onClick = onStart,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF4ECDC4)
            )
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Bắt đầu",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
//  INTERVAL — ACTIVE  (Work / Rest phase)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun IntervalActive(
    currentRound: Int,
    rounds: Int,
    phaseRemainingMs: Long,
    workMs: Long,
    restMs: Long,
    isWorkPhase: Boolean,
    isRunning: Boolean,
    formatTime: (Long) -> String,
    formatCountdown: (Long) -> String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit
) {
    val phaseColor = if (isWorkPhase) Color(0xFF4ECDC4) else Color(0xFFFF9800)
    val phaseLabel = if (isWorkPhase) "TẬP" else "NGHỈ"
    val phaseLabelShort = if (isWorkPhase) "Work" else "Rest"
    val phaseTotalMs = if (isWorkPhase) workMs else restMs
    val progress = if (phaseTotalMs > 0) phaseRemainingMs.toFloat() / phaseTotalMs.toFloat() else 0f

    val scaleAnim by animateFloatAsState(
        targetValue = if (isRunning) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── Round indicator ──
        Text(
            text = "Round $currentRound / $rounds",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "⏱  $phaseLabelShort",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = phaseColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Phase circle ──
        Card(
            modifier = Modifier
                .size(250.dp)
                .scale(scaleAnim),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = phaseColor.copy(alpha = 0.12f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatCountdown(phaseRemainingMs),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        color = phaseColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .width(160.dp)
                            .height(4.dp),
                        color = phaseColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRunning) "ĐANG $phaseLabel" else "ĐÃ DỪNG",
                        fontSize = 12.sp,
                        color = if (isRunning) Color(0xFFFFD93D)
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Controls ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset / stop
            FilledIconButton(
                onClick = onReset,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Dừng",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Pause / Resume
            FilledIconButton(
                onClick = if (isRunning) onPause else onResume,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRunning) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
                )
            ) {
                Icon(
                    if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Tạm dừng" else "Tiếp tục",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Symmetry spacer
            Spacer(modifier = Modifier.size(56.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Phase summary chips ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isWorkPhase) Color(0xFF4ECDC4).copy(alpha = 0.25f)
                    else Color(0xFF4ECDC4).copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Tập: ${formatCountdown(workMs)}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = if (isWorkPhase) FontWeight.Bold else FontWeight.Normal,
                    color = Color(0xFF4ECDC4)
                )
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (!isWorkPhase) Color(0xFFFF9800).copy(alpha = 0.25f)
                    else Color(0xFFFF9800).copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Nghỉ: ${formatCountdown(restMs)}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = if (!isWorkPhase) FontWeight.Bold else FontWeight.Normal,
                    color = Color(0xFFFF9800)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onReset) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Cài đặt lại")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  INTERVAL — COMPLETED
// ═══════════════════════════════════════════════════════════════

@Composable
private fun IntervalCompleted(onReset: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "completed-pulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        Card(
            modifier = Modifier.size(250.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "✅",
                    fontSize = 64.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "🎉  Hoàn thành!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50),
            modifier = Modifier.scale(pulseScale)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tất cả các rounds đã kết thúc",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(40.dp))

        FilledIconButton(
            onClick = onReset,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF4ECDC4)
            )
        ) {
            Icon(
                Icons.Default.Replay,
                contentDescription = "Đặt lại",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Nhấn để đặt lại",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  FINE-TUNE BUTTON
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FineTuneButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ═══════════════════════════════════════════════════════════════
//  ALERT HELPER  (vibration + sound + flash)
// ═══════════════════════════════════════════════════════════════

private fun triggerAlert(
    context: Context,
    alertType: AlertType,
    onFlash: () -> Unit
) {
    // Always vibrate
    triggerVibration(context)

    when (alertType) {
        AlertType.ALERT_TEXT -> {
            // Text indicator is shown in the UI; vibration already triggered
        }
        AlertType.FLASH_SCREEN -> {
            onFlash()
        }
        AlertType.VIBRATION -> {
            // vibration only
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  VIBRATION HELPER
// ═══════════════════════════════════════════════════════════════

private fun triggerVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator != null && vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(600)
        }
    }
}
