package com.superapp.ui.screens

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

enum class FlashlightMode(val label: String) {
    FLASHLIGHT("Đèn Pin"),
    STROBE("Strobe"),
    SCREEN_LIGHT("Đèn Màn Hình"),
    SOS("S.O.S")
}

enum class StrobeFrequency(val label: String, val hz: Int, val delayMs: Long) {
    HZ_1("1 Hz", 1, 500L),
    HZ_3("3 Hz", 3, 166L),
    HZ_5("5 Hz", 5, 100L),
    HZ_10("10 Hz", 10, 50L)
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
 * Turn flashlight ON. (toggleFlashlight with currentlyOn=false → !false = true)
 */
private fun flashOn(context: Context) {
    toggleFlashlight(context, false)
}

/**
 * Turn flashlight OFF. (toggleFlashlight with currentlyOn=true → !true = false)
 */
private fun flashOff(context: Context) {
    toggleFlashlight(context, true)
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
        isOn = false
        ensureFlashOff(context)
    }

    // ── Strobe LaunchedEffect ──
    LaunchedEffect(selectedMode, isOn, selectedFrequency) {
        if (selectedMode == FlashlightMode.STROBE && isOn && hasFlash && hasPermission) {
            val delayMs = selectedFrequency.delayMs
            while (true) {
                try {
                    flashOn(context)                       // ON
                    delay(delayMs)
                    flashOff(context)                      // OFF
                    delay(delayMs)
                } catch (_: Exception) { break }
            }
        }
    }

    // ── SOS LaunchedEffect ──
    // SOS pattern: 3 short (S), 3 long (O), 3 short (S) — standard international
    fun sosDelayMs(unit: Int) = (unit * 200).toLong()

    LaunchedEffect(selectedMode, isOn) {
        if (selectedMode == FlashlightMode.SOS && isOn && hasFlash && hasPermission) {
            // Pair: (flashOn, durationUnits)
            // flashOn = true  → turn torch ON  → toggleFlashlight(context, false)
            // flashOn = false → turn torch OFF → toggleFlashlight(context, true)
            val pattern = listOf(
                true to 1,  false to 1,  // S · short
                true to 1,  false to 1,
                true to 1,  false to 1,
                false to 1,               // letter gap
                true to 3,  false to 1,  // O − long
                true to 3,  false to 1,
                true to 3,  false to 1,
                false to 1,               // letter gap
                true to 1,  false to 1,  // S · short
                true to 1,  false to 1,
                true to 1,  false to 1,
                false to 3                // word gap
            )
            while (true) {
                for ((flashOn, units) in pattern) {
                    try {
                        if (flashOn) flashOn(context) else flashOff(context)
                        delay(sosDelayMs(units))
                    } catch (_: Exception) { break }
                }
            }
        }
    }

    // ── Cleanup on dispose ──
    DisposableEffect(Unit) {
        onDispose {
            ensureFlashOff(context)
            // Reset screen brightness
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

    // ── Color scheme per mode ──
    val accentColor = when {
        !isOn -> MaterialTheme.colorScheme.surfaceVariant
        selectedMode == FlashlightMode.STROBE -> Color(0xFFD32F2F)       // red
        selectedMode == FlashlightMode.SOS -> Color(0xFFFF6F00)          // amber
        selectedMode == FlashlightMode.SCREEN_LIGHT -> Color(0xFFFFFFFF) // white
        else -> Color(0xFFFFEB3B)                                        // yellow
    }

    val bgColor = when {
        !isOn -> MaterialTheme.colorScheme.background
        selectedMode == FlashlightMode.STROBE -> Color(0xFFB71C1C)
        selectedMode == FlashlightMode.SOS -> Color(0xFFE65100)
        selectedMode == FlashlightMode.SCREEN_LIGHT -> Color(0xFFF5F5F5)
        else -> Color(0xFF00ACC1)
    }

    val topBarColor = when {
        !isOn -> MaterialTheme.colorScheme.surface
        selectedMode == FlashlightMode.STROBE -> Color(0xFFC62828)
        selectedMode == FlashlightMode.SOS -> Color(0xFFEF6C00)
        selectedMode == FlashlightMode.SCREEN_LIGHT -> Color(0xFFBDBDBD)
        else -> Color(0xFF00838F)
    }

    val textColor = if (isOn && selectedMode != FlashlightMode.SCREEN_LIGHT) Color.White
    else MaterialTheme.colorScheme.onBackground

    // ── Screen Light brightness ──
    val window = remember { context.getActivityWindow() }
    LaunchedEffect(selectedMode, isOn, screenBrightness) {
        if (selectedMode == FlashlightMode.SCREEN_LIGHT && isOn && window != null) {
            val lp = window.attributes
            lp.screenBrightness = screenBrightness.coerceIn(0.01f, 1f)
            window.attributes = lp
        } else if (window != null) {
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    // ── UI ──
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đèn Pin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        turnOffEverything()
                        // Reset screen brightness
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlashlightMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = {
                                if (isOn) turnOffEverything()
                                selectedMode = mode
                            },
                            label = {
                                Text(
                                    text = mode.label,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (mode) {
                                    FlashlightMode.STROBE -> Color(0xFFD32F2F)
                                    FlashlightMode.SOS -> Color(0xFFFF6F00)
                                    FlashlightMode.SCREEN_LIGHT -> Color(0xFF9E9E9E)
                                    FlashlightMode.FLASHLIGHT -> Color(0xFFF9A825)
                                },
                                selectedLabelColor = when (mode) {
                                    FlashlightMode.SCREEN_LIGHT -> Color.Black
                                    else -> Color.White
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                if (!hasPermission || !hasFlash) {
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
                                        isOn = !isOn
                                        if (!isOn) ensureFlashOff(context)
                                    }
                                    FlashlightMode.SCREEN_LIGHT -> {
                                        isOn = !isOn
                                        if (!isOn) ensureFlashOff(context)
                                    }
                                    FlashlightMode.SOS -> {
                                        if (!requestOrCheckPermission()) return@clickable
                                        if (!hasFlash) return@clickable
                                        isOn = !isOn
                                        if (!isOn) ensureFlashOff(context)
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
                                        !isOn -> MaterialTheme.colorScheme.surface
                                        selectedMode == FlashlightMode.STROBE -> Color(0xFFE53935)
                                        selectedMode == FlashlightMode.SOS -> Color(0xFFFF8F00)
                                        selectedMode == FlashlightMode.SCREEN_LIGHT -> Color(0xFFFFFFFF)
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
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Status text ──
                    val statusText = when {
                        !isOn -> "TẮT"
                        selectedMode == FlashlightMode.STROBE -> "STROBE"
                        selectedMode == FlashlightMode.SOS -> "S.O.S"
                        selectedMode == FlashlightMode.SCREEN_LIGHT -> "ĐÈN MÀN HÌNH"
                        else -> "ĐÃ BẬT"
                    }
                    Text(
                        text = statusText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOn && selectedMode != FlashlightMode.SCREEN_LIGHT) Color.White
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Hint text ──
                    val hintText = when {
                        !isOn -> "Chạm để bật"
                        selectedMode == FlashlightMode.STROBE -> "${selectedFrequency.label} · Chạm để tắt"
                        selectedMode == FlashlightMode.SOS -> "Phát tín hiệu cấp cứu · Chạm để tắt"
                        selectedMode == FlashlightMode.SCREEN_LIGHT -> "Chạm để tắt"
                        else -> "Chạm để tắt"
                    }
                    Text(
                        text = hintText,
                        fontSize = 14.sp,
                        color = if (isOn && selectedMode != FlashlightMode.SCREEN_LIGHT) Color.White.copy(alpha = 0.8f)
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

                    // ── Bottom info card ──
                    val infoText = when (selectedMode) {
                        FlashlightMode.STROBE -> "Đèn nhấp nháy tần số cao"
                        FlashlightMode.SOS -> "Tín hiệu cấp cứu quốc tế"
                        FlashlightMode.SCREEN_LIGHT -> "Điều chỉnh độ sáng màn hình"
                        FlashlightMode.FLASHLIGHT -> "Đèn pin sử dụng nhiều pin"
                    }
                    Card(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOn && selectedMode != FlashlightMode.SCREEN_LIGHT) Color.White.copy(alpha = 0.15f)
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
                                tint = if (isOn && selectedMode != FlashlightMode.SCREEN_LIGHT) Color.White.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                infoText,
                                fontSize = 12.sp,
                                color = if (isOn && selectedMode != FlashlightMode.SCREEN_LIGHT) Color.White.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                // Bottom spacer to keep content scroll-friendly
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
