package com.superapp.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(navController: NavController) {
    // ── Mode ──────────────────────────────────────────────
    var isStopwatchMode by remember { mutableStateOf(true) }

    // ── Stopwatch state ───────────────────────────────────
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var laps by remember { mutableStateOf(listOf<Long>()) }
    var lastLapTime by remember { mutableLongStateOf(0L) }

    // ── Countdown state ───────────────────────────────────
    var countdownConfigMs by remember { mutableLongStateOf(5 * 60 * 1000L) } // default 5 min
    var countdownRemainingMs by remember { mutableLongStateOf(5 * 60 * 1000L) }
    var isCountdownFinished by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // ── Best / worst lap calculation ──────────────────────
    val bestLap = remember(laps) { if (laps.size < 2) null else laps.minOrNull() }
    val worstLap = remember(laps) { if (laps.size < 2) null else laps.maxOrNull() }

    // ── Timers ────────────────────────────────────────────
    // Stopwatch ticker
    LaunchedEffect(isRunning, isStopwatchMode) {
        if (isRunning && isStopwatchMode) {
            val startTime = System.currentTimeMillis() - elapsedMs
            while (isRunning) {
                delay(10)
                if (!isStopwatchMode) break
                elapsedMs = System.currentTimeMillis() - startTime
            }
        }
    }

    // Countdown ticker
    LaunchedEffect(isRunning, isStopwatchMode) {
        if (isRunning && !isStopwatchMode && countdownRemainingMs > 0) {
            val initialRemaining = countdownRemainingMs
            val startTime = System.currentTimeMillis()
            while (true) {
                delay(10)
                if (!isRunning || isStopwatchMode) break
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (initialRemaining - elapsed).coerceAtLeast(0L)
                countdownRemainingMs = remaining
                if (remaining <= 0) {
                    countdownRemainingMs = 0L
                    isCountdownFinished = true
                    isRunning = false
                    triggerVibration(context)
                    break
                }
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
        if (isStopwatchMode) {
            isRunning = !isRunning
        } else {
            when {
                isCountdownFinished -> {
                    // Restart countdown from config
                    isCountdownFinished = false
                    countdownRemainingMs = countdownConfigMs
                    isRunning = true
                }
                countdownRemainingMs > 0 -> {
                    isRunning = !isRunning
                }
            }
        }
    }

    fun reset() {
        if (isStopwatchMode) {
            isRunning = false
            elapsedMs = 0L
            laps = emptyList()
            lastLapTime = 0L
        } else {
            isRunning = false
            isCountdownFinished = false
            countdownRemainingMs = countdownConfigMs
        }
    }

    fun addLap() {
        val lapDuration = elapsedMs - lastLapTime
        laps = laps + lapDuration
        lastLapTime = elapsedMs
    }

    fun switchMode() {
        if (isRunning) isRunning = false
        isStopwatchMode = !isStopwatchMode
        // Reset inactive mode's state
        if (isStopwatchMode) {
            countdownRemainingMs = countdownConfigMs
            isCountdownFinished = false
        } else {
            elapsedMs = 0L
            laps = emptyList()
            lastLapTime = 0L
        }
    }

    // ── Animated background ───────────────────────────────
    val containerBg by animateColorAsState(
        targetValue = when {
            !isStopwatchMode && isCountdownFinished ->
                Color(0xFFFF6B6B).copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.background
        },
        animationSpec = tween(600),
        label = "bg"
    )

    // ── UI ────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isStopwatchMode) "Bấm Giờ" else "Đếm Ngược",
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
                            if (isStopwatchMode) Icons.Default.HourglassEmpty
                            else Icons.Default.Timer,
                            contentDescription = if (isStopwatchMode) "Sang đếm ngược"
                            else "Sang bấm giờ"
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
                targetState = isStopwatchMode,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "mode-switch"
            ) { stopwatch ->
                if (stopwatch) {
                    // ═══════════ STOPWATCH ═══════════
                    StopwatchModeContent(
                        elapsedMs = elapsedMs,
                        isRunning = isRunning,
                        laps = laps,
                        bestLap = bestLap,
                        worstLap = worstLap,
                        formatTime = ::formatTime,
                        onLapOrReset = { if (isRunning) addLap() else reset() },
                        onStartStop = ::toggleStartStop
                    )
                } else {
                    // ═══════════ COUNTDOWN ═══════════
                    CountdownModeContent(
                        configMs = countdownConfigMs,
                        remainingMs = countdownRemainingMs,
                        isRunning = isRunning,
                        isFinished = isCountdownFinished,
                        formatCountdown = ::formatCountdown,
                        onSetConfig = { newConfig ->
                            countdownConfigMs = newConfig
                            if (!isRunning && !isCountdownFinished) {
                                countdownRemainingMs = newConfig
                            }
                        },
                        onStartStop = ::toggleStartStop,
                        onReset = ::reset
                    )
                }
            }
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
    formatTime: (Long) -> String,
    onLapOrReset: () -> Unit,
    onStartStop: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

        Spacer(modifier = Modifier.height(24.dp))

        // ── Lap list ──
        if (laps.isNotEmpty()) {
            LapList(
                laps = laps,
                bestLap = bestLap,
                worstLap = worstLap,
                formatTime = formatTime
            )
        }
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
//  COUNTDOWN CONTENT (dispatches to sub-states)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CountdownModeContent(
    configMs: Long,
    remainingMs: Long,
    isRunning: Boolean,
    isFinished: Boolean,
    formatCountdown: (Long) -> String,
    onSetConfig: (Long) -> Unit,
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
                formatCountdown = formatCountdown,
                onSetConfig = onSetConfig,
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
            2 -> CountdownFinished(onReset = onReset)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  COUNTDOWN — CONFIGURATION
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownConfig(
    configMs: Long,
    formatCountdown: (Long) -> String,
    onSetConfig: (Long) -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── Presets ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                60_000L to "1ph",
                180_000L to "3ph",
                300_000L to "5ph",
                600_000L to "10ph"
            ).forEach { (ms, label) ->
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

        Spacer(modifier = Modifier.height(28.dp))

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
                onSetConfig((configMs - 60_000).coerceAtLeast(60_000L))
            }
            FineTuneButton("-10s", enabled = configMs >= 20_000) {
                onSetConfig((configMs - 10_000).coerceAtLeast(10_000L))
            }
            FineTuneButton("+10s", enabled = configMs < 99 * 60 * 1000L - 10_000) {
                onSetConfig((configMs + 10_000).coerceAtMost(99 * 60 * 1000L))
            }
            FineTuneButton("+1ph", enabled = configMs < 99 * 60 * 1000L - 60_000) {
                onSetConfig((configMs + 60_000).coerceAtMost(99 * 60 * 1000L))
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
    }
}

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
//  COUNTDOWN — FINISHED
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CountdownFinished(onReset: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "finished-pulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
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
                Text(
                    text = "00:00",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFF6B6B)
                )
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
