package com.superapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class FlashlightMode(val label: String) {
    FLASHLIGHT("Đèn Pin"),
    STROBE("Strobe"),
    SCREEN_LIGHT("Đèn Màn Hình"),
    SOS("S.O.S"),
    POLICE("Police"),
    COLOR_LIGHT("Đèn Màu"),
    MORSE("Morse")
}

enum class StrobeFrequency(val label: String, val hz: Int, val delayMs: Long) {
    HZ_1("1 Hz", 1, 500L),
    HZ_3("3 Hz", 3, 166L),
    HZ_5("5 Hz", 5, 100L),
    HZ_10("10 Hz", 10, 50L)
}

enum class PoliceSpeed(val label: String, val delayMs: Long) {
    FAST("Nhanh", 100L),
    MEDIUM("TB", 200L),
    SLOW("Chậm", 400L)
}

enum class LightColor(val label: String, val displayColor: Color) {
    WHITE("Trắng", Color.White),
    RED("Đỏ", Color(0xFFF44336)),
    BLUE("Xanh dương", Color(0xFF2196F3)),
    GREEN("Xanh lá", Color(0xFF4CAF50)),
    YELLOW("Vàng", Color(0xFFFFEB3B)),
    PURPLE("Tím", Color(0xFF9C27B0)),
    PINK("Hồng", Color(0xFFE91E63)),
    ORANGE("Cam", Color(0xFFFF9800))
}

enum class MorseOutputMode(val label: String, val icon: String) {
    FLASHLIGHT("Đèn Flash", "📸"),
    SCREEN("Màn Hình", "🖥️")
}

// ── Morse code mapping ──
// Pattern: dot (150ms on), dash (450ms on), gap between signals (150ms off),
//          gap between chars (450ms off), gap between words (1050ms off)
private val morseCodeMap: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
        '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----.",
        ' ' to " " // space = word gap
)

/** Convert a message string to a sequence of on/off durations in milliseconds. */
private fun messageToMorseTimings(message: String): List<Pair<Boolean, Long>> {
    val timings = mutableListOf<Pair<Boolean, Long>>()
    val upper = message.uppercase()

    for ((idx, char) in upper.withIndex()) {
        val pattern = morseCodeMap[char] ?: continue

        if (char == ' ') {
            // Word gap: 1050ms off (7 units)
            timings.add(false to 1050L)
            continue
        }

        for ((_, symbol) in pattern.withIndex()) {
            if (symbol == '.') {
                timings.add(true to 150L)   // dot on
                timings.add(false to 150L)  // gap between signals
            } else if (symbol == '-') {
                timings.add(true to 450L)   // dash on
                timings.add(false to 150L)  // gap between signals
            }
        }

        // Char gap: remove the trailing inter-signal gap and add 450ms (3 units)
        // Actually simpler: remove last false (150ms) and add char gap (450ms)
        // But let's just append char gap and rely on the trailing inter-signal gap being harmless.
        // Better: if not last char and next isn't space, add char gap
        if (idx < upper.length - 1 && upper[idx + 1] != ' ') {
            // We already added a 150ms gap after the last dot/dash.
            // To get a char gap of 450ms total, we need total off = 450ms.
            // We have 150ms already, so add 300ms more.
            timings.add(false to 300L)
        }
    }

    // Remove trailing off if present (clean finish)
    if (timings.isNotEmpty() && !timings.last().first) {
        timings.removeAt(timings.size - 1)
    }

    return timings
}

/**
 * Force all camera flash units off.
 */
private fun ensureFlashOff(context: Context) {
    try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in cm.cameraIdList) {
            try { cm.setTorchMode(id, false) } catch (_: Exception) {}
        }
    } catch (_: Exception) {}
}

/**
 * Turn flashlight ON. (toggleFlashlight with currentlyOn=false -> !false = true)
 */
private fun flashOn(context: Context) {
    toggleFlashlight(context, false)
}

/**
 * Turn flashlight OFF. (toggleFlashlight with currentlyOn=true -> !true = false)
 */
private fun flashOff(context: Context) {
    toggleFlashlight(context, true)
}

/** Convert a Morse pattern string (e.g. ".-") to human-readable dots and dashes text. */
private fun morsePatternToText(pattern: String): String {
    return pattern.map { if (it == '.') "." else if (it == '-') "—" else " " }.joinToString("")
}

/** Convert a message to its Morse code text representation. */
private fun messageToMorseText(message: String): String {
    val upper = message.uppercase().trim()
    if (upper.isEmpty()) return ""
    return upper.map { ch ->
        morseCodeMap[ch]?.let { morsePatternToText(it) } ?: ""
    }.joinToString(" ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashlightScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(FlashlightMode.FLASHLIGHT) }
    var isOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(true) }
    var selectedFrequency by remember { mutableStateOf(StrobeFrequency.HZ_3) }
    var screenBrightness by remember { mutableStateOf(1f) }
    var selectedLightColor by remember { mutableStateOf(LightColor.WHITE) }
    var selectedPoliceSpeed by remember { mutableStateOf(PoliceSpeed.MEDIUM) }
    var currentPoliceColor by remember { mutableStateOf(Color.Red) }

    // ── Morse state ──
    var morseMessage by remember { mutableStateOf("") }
    var morseOutputMode by remember { mutableStateOf(MorseOutputMode.FLASHLIGHT) }
    var sending by remember { mutableStateOf(false) }

    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // ── Check flash availability ──
    LaunchedEffect(Unit) {
        hasFlash = try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cameraId != null
        } catch (_: Exception) { false }
    }

    // ── Permission launcher ──
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // ── Auto-request camera permission on screen entry ──
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestOrCheckPermission(): Boolean {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return false
        }
        return true
    }

    // ── Toggle flashlight on/off ──
    fun toggleFlash() {
        if (!requestOrCheckPermission()) return
        if (!hasFlash) return
        try {
            toggleFlashlight(context, isOn)
            isOn = !isOn
        } catch (_: Exception) {
            hasPermission = false
        }
    }

    fun turnOffEverything() {
        ensureFlashOff(context)
        isOn = false
    }

    // ── Strobe LaunchedEffect ──
    LaunchedEffect(selectedMode, isOn, selectedFrequency) {
        if (selectedMode == FlashlightMode.STROBE && isOn && hasFlash && hasPermission) {
            val delayMs = selectedFrequency.delayMs
            while (true) {
                if (!isOn) { ensureFlashOff(context); break }
                try {
                    flashOn(context)
                    delay(delayMs)
                    flashOff(context)
                    delay(delayMs)
                } catch (_: Exception) { break }
            }
        }
    }

    // ── SOS LaunchedEffect ──
    fun sosDelayMs(unit: Int) = (unit * 200).toLong()

    LaunchedEffect(selectedMode, isOn) {
        if (selectedMode == FlashlightMode.SOS && isOn && hasFlash && hasPermission) {
            val pattern = listOf(
                true to 1,  false to 1,
                true to 1,  false to 1,
                true to 1,  false to 1,
                false to 1,
                true to 3,  false to 1,
                true to 3,  false to 1,
                true to 3,  false to 1,
                false to 1,
                true to 1,  false to 1,
                true to 1,  false to 1,
                true to 1,  false to 1,
                false to 3
            )
            while (true) {
                if (!isOn) { ensureFlashOff(context); break }
                for ((flashOn, units) in pattern) {
                    if (!isOn) { ensureFlashOff(context); break }
                    try {
                        if (flashOn) flashOn(context) else flashOff(context)
                        delay(sosDelayMs(units))
                    } catch (_: Exception) { break }
                }
            }
        }
    }

    // ── Police Light LaunchedEffect ──
    LaunchedEffect(selectedMode, isOn, selectedPoliceSpeed) {
        if (selectedMode == FlashlightMode.POLICE && isOn) {
            var isRed = true
            while (true) {
                if (!isOn) { ensureFlashOff(context); break }
                try {
                    currentPoliceColor = if (isRed) Color(0xFFD32F2F) else Color(0xFF1565C0)
                    // Toggle flash in sync if available
                    if (hasFlash && hasPermission) {
                        if (isRed) flashOn(context) else flashOff(context)
                    }
                    delay(selectedPoliceSpeed.delayMs)
                    isRed = !isRed
                } catch (_: Exception) { break }
            }
        }
    }

    // ── Morse LaunchedEffect ──
    LaunchedEffect(selectedMode, sending) {
        if (selectedMode == FlashlightMode.MORSE && sending) {
            if (morseOutputMode == MorseOutputMode.FLASHLIGHT && !hasPermission) {
                sending = false
                return@LaunchedEffect
            }

            val timings = messageToMorseTimings(morseMessage)
            if (timings.isEmpty()) {
                sending = false
                return@LaunchedEffect
            }

            for ((isFlashOn, durationMs) in timings) {
                if (!sending || !isActive) break
                try {
                    when (morseOutputMode) {
                        MorseOutputMode.FLASHLIGHT -> {
                            if (isFlashOn) flashOn(context) else flashOff(context)
                        }
                        MorseOutputMode.SCREEN -> {
                            // Screen mode: just set isOn for UI brightness
                            isOn = isFlashOn
                        }
                    }
                    delay(durationMs)
                } catch (_: Exception) { break }
            }

            // Ensure everything is off when done
            ensureFlashOff(context)
            sending = false
            if (morseOutputMode == MorseOutputMode.SCREEN) {
                isOn = false
            }
        }
    }

    // ── Cleanup on dispose ──
    DisposableEffect(Unit) {
        onDispose {
            ensureFlashOff(context)
            try {
                val w = context.getActivityWindow()
                if (w != null) {
                    val lp = w.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    w.attributes = lp
                }
            } catch (_: Exception) {}
        }
    }

    // ── Color/theme variables ──
    val isColorLight = selectedMode == FlashlightMode.COLOR_LIGHT
    val isPolice = selectedMode == FlashlightMode.POLICE
    val isStrobe = selectedMode == FlashlightMode.STROBE
    val isSOS = selectedMode == FlashlightMode.SOS
    val isScreenLight = selectedMode == FlashlightMode.SCREEN_LIGHT
    val isMorse = selectedMode == FlashlightMode.MORSE

    // For Morse screen mode, use isOn as the brightness indicator
    val isMorseScreenOn = isMorse && morseOutputMode == MorseOutputMode.SCREEN && sending

    val accentColor = when {
        !isOn && !isMorseScreenOn -> MaterialTheme.colorScheme.surfaceVariant
        isStrobe -> Color(0xFFD32F2F)
        isSOS -> Color(0xFFFF6F00)
        isPolice -> currentPoliceColor
        isColorLight -> selectedLightColor.displayColor
        isScreenLight -> Color(0xFFFFFFFF)
        isMorse && sending -> Color(0xFF00C853)
        isMorse -> Color(0xFF00ACC1)
        else -> Color(0xFFFFEB3B)
    }

    val bgColor = when {
        !isOn && !isMorseScreenOn -> MaterialTheme.colorScheme.background
        isStrobe -> Color(0xFFB71C1C)
        isSOS -> Color(0xFFE65100)
        isPolice -> currentPoliceColor.copy(alpha = 0.85f)
        isColorLight -> selectedLightColor.displayColor
        isScreenLight -> Color(0xFFF5F5F5)
        isMorse && isMorseScreenOn -> if (isOn) Color.White else Color.Black
        isMorse -> MaterialTheme.colorScheme.background
        else -> Color(0xFF00ACC1)
    }

    val topBarColor = when {
        !isOn && !isMorseScreenOn -> MaterialTheme.colorScheme.surface
        isStrobe -> Color(0xFFC62828)
        isSOS -> Color(0xFFEF6C00)
        isPolice -> currentPoliceColor.copy(alpha = 0.7f)
        isColorLight -> selectedLightColor.displayColor.copy(alpha = 0.7f)
        isScreenLight -> Color(0xFFBDBDBD)
        isMorse -> Color(0xFF00897B)
        else -> Color(0xFF00838F)
    }

    val isOnBrightBg = isOn && (isScreenLight || (isColorLight && (selectedLightColor == LightColor.WHITE || selectedLightColor == LightColor.YELLOW)))
    val textColor = if (isOn && !isOnBrightBg) Color.White
    else MaterialTheme.colorScheme.onBackground

    // ── Screen / Color Light brightness ──
    val window = remember { context.getActivityWindow() }
    LaunchedEffect(selectedMode, isOn, screenBrightness, selectedLightColor, morseOutputMode, sending, isMorseScreenOn) {
        if (window != null) {
            val lp = window.attributes
            when {
                // Screen Light: user-controlled brightness
                selectedMode == FlashlightMode.SCREEN_LIGHT && isOn -> {
                    lp.screenBrightness = screenBrightness.coerceIn(0.01f, 1f)
                    window.attributes = lp
                }
                // Color Light: full brightness to show color vibrantly
                selectedMode == FlashlightMode.COLOR_LIGHT && isOn -> {
                    lp.screenBrightness = 1f
                    window.attributes = lp
                }
                // Police: full brightness for screen flash effect
                selectedMode == FlashlightMode.POLICE && isOn -> {
                    lp.screenBrightness = 1f
                    window.attributes = lp
                }
                // Morse screen mode: flash brightness
                isMorseScreenOn -> {
                    lp.screenBrightness = if (isOn) 1f else 0.01f
                    window.attributes = lp
                }
                else -> {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            }
        }
    }

    // ── UI ──
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đèn Pin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        sending = false
                        turnOffEverything()
                        try {
                            if (window != null) {
                                val lp = window.attributes
                                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                window.attributes = lp
                            }
                        } catch (_: Exception) {}
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ── Mode selection chips ──
                @Suppress("UNUSED_EXPRESSION")
                val modeRow = @Composable {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FlashlightMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = {
                                    if (isOn) turnOffEverything()
                                    sending = false
                                    selectedMode = mode
                                },
                                label = {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (mode) {
                                        FlashlightMode.STROBE -> Color(0xFFD32F2F)
                                        FlashlightMode.SOS -> Color(0xFFFF6F00)
                                        FlashlightMode.SCREEN_LIGHT -> Color(0xFF9E9E9E)
                                        FlashlightMode.POLICE -> Color(0xFF5C6BC0)
                                        FlashlightMode.COLOR_LIGHT -> Color(0xFF7B1FA2)
                                        FlashlightMode.FLASHLIGHT -> Color(0xFFF9A825)
                                        FlashlightMode.MORSE -> Color(0xFF00ACC1)
                                    },
                                    selectedLabelColor = when (mode) {
                                        FlashlightMode.SCREEN_LIGHT, FlashlightMode.FLASHLIGHT -> Color.Black
                                        else -> Color.White
                                    }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                modeRow()

                Spacer(modifier = Modifier.height(12.dp))

                // ── Strobe frequency chips ──
                if (selectedMode == FlashlightMode.STROBE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StrobeFrequency.entries.forEach { freq ->
                            AssistChip(
                                onClick = { selectedFrequency = freq },
                                label = {
                                    Text(text = freq.label, fontSize = 11.sp)
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selectedFrequency == freq)
                                        Color(0xFFD32F2F).copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (selectedFrequency == freq) Color.White
                                    else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ── Police speed chips ──
                if (selectedMode == FlashlightMode.POLICE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PoliceSpeed.entries.forEach { speed ->
                            AssistChip(
                                onClick = { selectedPoliceSpeed = speed },
                                label = {
                                    Text(text = speed.label, fontSize = 11.sp)
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selectedPoliceSpeed == speed)
                                        Color(0xFF5C6BC0).copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (selectedPoliceSpeed == speed) Color.White
                                    else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ── Color Light color picker ──
                if (selectedMode == FlashlightMode.COLOR_LIGHT) {
                    Text(
                        text = "Chọn màu:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isOn && (selectedLightColor == LightColor.BLUE || selectedLightColor == LightColor.RED || selectedLightColor == LightColor.PURPLE || selectedLightColor == LightColor.GREEN || selectedLightColor == LightColor.PINK || selectedLightColor == LightColor.ORANGE))
                            Color.White else textColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LightColor.entries.forEach { lightColor ->
                            val isSelected = selectedLightColor == lightColor
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(lightColor.displayColor)
                                    .then(
                                        if (isSelected) Modifier.border(
                                            width = 3.dp,
                                            color = if (lightColor == LightColor.WHITE || lightColor == LightColor.YELLOW)
                                                Color.Black else Color.White,
                                            shape = CircleShape
                                        ) else Modifier.border(
                                            width = 1.dp,
                                            color = Color.Gray.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                    )
                                    .clickable {
                                        if (isOn) {
                                            // When changing color while on, just update the selected color
                                            // The LaunchedEffect for brightness will re-run
                                        }
                                        selectedLightColor = lightColor
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (lightColor == LightColor.WHITE || lightColor == LightColor.YELLOW)
                                            Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ── Morse Mode UI ──
                if (selectedMode == FlashlightMode.MORSE) {
                    // Text input
                    OutlinedTextField(
                        value = morseMessage,
                        onValueChange = { if (it.length <= 50 && !sending) morseMessage = it },
                        label = { Text("Nhập thông điệp (tối đa 50 ký tự)") },
                        placeholder = { Text("VD: SOS, HELLO...") },
                        enabled = !sending,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (sending) Color.Gray else Color(0xFF00ACC1),
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                            cursorColor = Color(0xFF00ACC1)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { /* no-op, user uses Send button */ }
                        )
                    )

                    // Morse code preview
                    val morsePreviewText = messageToMorseText(morseMessage)
                    if (morsePreviewText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = morsePreviewText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (sending) Color(0xFF00C853) else textColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Output mode chips ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MorseOutputMode.entries.forEach { outputMode ->
                            val isSelected = morseOutputMode == outputMode
                            AssistChip(
                                onClick = { if (!sending) morseOutputMode = outputMode },
                                label = {
                                    Text(
                                        text = "${outputMode.icon} ${outputMode.label}",
                                        fontSize = 12.sp
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isSelected)
                                        Color(0xFF00ACC1).copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (isSelected) Color.White
                                    else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f),
                                enabled = !sending
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Send / Stop buttons ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Send button
                        Button(
                            onClick = {
                                if (!sending && morseMessage.isNotBlank()) {
                                    // Ensure we start clean
                                    ensureFlashOff(context)
                                    isOn = false
                                    if (morseOutputMode == MorseOutputMode.FLASHLIGHT) {
                                        if (!requestOrCheckPermission()) return@Button
                                        if (!hasFlash) return@Button
                                    }
                                    sending = true
                                }
                            },
                            enabled = !sending && morseMessage.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00C853)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gửi", fontSize = 16.sp)
                        }

                        // Stop button
                        Button(
                            onClick = {
                                sending = false
                                ensureFlashOff(context)
                                isOn = false
                            },
                            enabled = sending,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Dừng", fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Screen Light brightness slider ──
                if (selectedMode == FlashlightMode.SCREEN_LIGHT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BrightnessLow,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Slider(
                            value = screenBrightness,
                            onValueChange = { screenBrightness = it },
                            valueRange = 0.05f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Icon(
                            Icons.Default.BrightnessHigh,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ── Permission / Flash unavailable ──
                val needsCamera = selectedMode == FlashlightMode.FLASHLIGHT ||
                        selectedMode == FlashlightMode.STROBE ||
                        selectedMode == FlashlightMode.SOS ||
                        selectedMode == FlashlightMode.POLICE ||
                        (selectedMode == FlashlightMode.MORSE && morseOutputMode == MorseOutputMode.FLASHLIGHT)

                if (needsCamera && (!hasPermission || !hasFlash)) {
                    if (!hasFlash) {
                        Text(
                            "Thiết bị không hỗ trợ đèn flash",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                    } else {
                        Card(
                            modifier = Modifier.padding(24.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Cần cấp quyền Camera")
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }) {
                                    Text("Cấp quyền")
                                }
                            }
                        }
                    }
                } else {
                    // ── Main control button ──
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .clickable {
                                when (selectedMode) {
                                    FlashlightMode.FLASHLIGHT -> toggleFlash()
                                    FlashlightMode.STROBE -> {
                                        if (!requestOrCheckPermission()) return@clickable
                                        if (!hasFlash) return@clickable
                                        if (isOn) ensureFlashOff(context)
                                        isOn = !isOn
                                    }
                                    FlashlightMode.SCREEN_LIGHT -> {
                                        isOn = !isOn
                                        if (!isOn) ensureFlashOff(context)
                                    }
                                    FlashlightMode.SOS -> {
                                        if (!requestOrCheckPermission()) return@clickable
                                        if (!hasFlash) return@clickable
                                        if (isOn) ensureFlashOff(context)
                                        isOn = !isOn
                                    }
                                    FlashlightMode.POLICE -> {
                                        if (!requestOrCheckPermission()) return@clickable
                                        if (!hasFlash) return@clickable
                                        if (isOn) {
                                            ensureFlashOff(context)
                                            isOn = false
                                        } else {
                                            isOn = true
                                        }
                                    }
                                    FlashlightMode.COLOR_LIGHT -> {
                                        isOn = !isOn
                                        if (!isOn) ensureFlashOff(context)
                                    }
                                    FlashlightMode.MORSE -> {
                                        // Center button in Morse mode: no action, use Send/Stop below
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !isOn && !isMorseScreenOn -> MaterialTheme.colorScheme.surface
                                        isStrobe -> Color(0xFFE53935)
                                        isSOS -> Color(0xFFFF8F00)
                                        isPolice -> currentPoliceColor.copy(alpha = 0.6f)
                                        isColorLight -> selectedLightColor.displayColor.copy(alpha = 0.8f)
                                        isScreenLight -> Color(0xFFFFFFFF)
                                        isMorse && sending -> Color(0xFF00C853)
                                        isMorse -> Color(0xFF80CBC4)
                                        else -> Color(0xFFFFF176)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (selectedMode) {
                                FlashlightMode.STROBE -> {
                                    Icon(
                                        imageVector = Icons.Default.FlashOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(90.dp),
                                        tint = if (isOn) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                                FlashlightMode.SOS -> {
                                    Text(
                                        text = "S.O.S",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOn) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                FlashlightMode.POLICE -> {
                                    if (isOn) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFD32F2F))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1565C0))
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.LocalPolice,
                                            contentDescription = null,
                                            modifier = Modifier.size(90.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                                FlashlightMode.COLOR_LIGHT -> {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        modifier = Modifier.size(90.dp),
                                        tint = if (isOn) selectedLightColor.displayColor
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                                FlashlightMode.SCREEN_LIGHT -> {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = null,
                                        modifier = Modifier.size(90.dp),
                                        tint = if (isOn) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                                FlashlightMode.FLASHLIGHT -> {
                                    Icon(
                                        imageVector = if (isOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(90.dp),
                                        tint = if (isOn) Color(0xFFF57F17) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                                FlashlightMode.MORSE -> {
                                    if (sending) {
                                        Text(
                                            text = "ĐANG GỬI...",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.WifiTethering,
                                            contentDescription = null,
                                            modifier = Modifier.size(90.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Status text ──
                    val statusText = when {
                        !isOn && !isMorseScreenOn -> "TẮT"
                        isStrobe -> "STROBE"
                        isSOS -> "S.O.S"
                        isPolice -> "POLICE"
                        isColorLight -> selectedLightColor.label.uppercase()
                        isScreenLight -> "ĐÈN MÀN HÌNH"
                        isMorse && sending -> "MORSE · ĐANG GỬI"
                        isMorse -> "MORSE"
                        else -> "ĐÃ BẬT"
                    }
                    Text(
                        text = statusText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if ((isOn && !isOnBrightBg) || isMorseScreenOn) Color.White
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Hint text ──
                    val hintText = when {
                        !isOn && !isMorseScreenOn -> "Chạm để bật"
                        isStrobe -> "${selectedFrequency.label} · Chạm để tắt"
                        isSOS -> "Phát tín hiệu cấp cứu · Chạm để tắt"
                        isPolice -> "${selectedPoliceSpeed.label} · Chạm để tắt"
                        isColorLight -> "Chạm để tắt"
                        isScreenLight -> "Chạm để tắt"
                        isMorse && sending -> "Đang phát Morse · Nhấn Dừng để kết thúc"
                        isMorse -> "Nhập thông điệp và nhấn Gửi"
                        else -> "Chạm để tắt"
                    }
                    Text(
                        text = hintText,
                        fontSize = 14.sp,
                        color = if ((isOn && !isOnBrightBg) || isMorseScreenOn) Color.White.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── SOS animated morse text ──
                    if (selectedMode == FlashlightMode.SOS && isOn) {
                        val infiniteTransition = rememberInfiniteTransition(label = "sos")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "sosAlpha"
                        )
                        Text(
                            text = "··· −−− ···",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = alpha),
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ── Police animated icon ──
                    if (selectedMode == FlashlightMode.POLICE && isOn) {
                        val infiniteTransition = rememberInfiniteTransition(label = "police")
                        val policeAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "policeAlpha"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.LocalPolice,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = policeAlpha),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🚔",
                                fontSize = 24.sp,
                                color = Color.White.copy(alpha = policeAlpha)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ── Color Light preview strip ──
                    if (selectedMode == FlashlightMode.COLOR_LIGHT && !isOn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            LightColor.entries.forEach { lc ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(lc.displayColor)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ── Morse animated indicator ──
                    if (selectedMode == FlashlightMode.MORSE && sending) {
                        val infiniteTransition = rememberInfiniteTransition(label = "morseAnim")
                        val morseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 400),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "morseAlpha"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = morseAlpha),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "📡",
                                fontSize = 24.sp,
                                color = Color.White.copy(alpha = morseAlpha)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ── Bottom info card ──
                    val infoText = when (selectedMode) {
                        FlashlightMode.STROBE -> "Đèn nhấp nháy tần số cao"
                        FlashlightMode.SOS -> "Tín hiệu cấp cứu quốc tế"
                        FlashlightMode.SCREEN_LIGHT -> "Điều chỉnh độ sáng màn hình"
                        FlashlightMode.POLICE -> "Đèn hiệu cảnh sát · nhấp nháy đỏ-xanh"
                        FlashlightMode.COLOR_LIGHT -> "Màn hình màu · chọn màu từ 8 màu"
                        FlashlightMode.FLASHLIGHT -> "Đèn pin sử dụng nhiều pin"
                        FlashlightMode.MORSE -> "Phát tín hiệu Morse qua đèn flash hoặc màn hình"
                    }
                    Card(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if ((isOn && !isOnBrightBg) || isMorseScreenOn) Color.White.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if ((isOn && !isOnBrightBg) || isMorseScreenOn) Color.White.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                infoText,
                                fontSize = 12.sp,
                                color = if ((isOn && !isOnBrightBg) || isMorseScreenOn) Color.White.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                // Bottom spacer
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ── Core toggle — preserved private function ──
private fun toggleFlashlight(context: Context, currentlyOn: Boolean) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        try {
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } catch (e: CameraAccessException) { false }
    } ?: return
    cameraManager.setTorchMode(cameraId, !currentlyOn)
}

// ── Helper to get Activity Window for screen brightness ──
private fun Context.getActivityWindow(): android.view.Window? {
    var ctxt: Context = this
    while (true) {
        when (ctxt) {
            is android.app.Activity -> return ctxt.window
            is android.content.ContextWrapper -> ctxt = ctxt.baseContext
            else -> return null
        }
    }
}
