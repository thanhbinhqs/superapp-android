package com.superapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(navController: NavController) {
    var display by remember { mutableStateOf("0") }
    var resultText by remember { mutableStateOf("") }
    var operation by remember { mutableStateOf("") }
    var firstValue by remember { mutableDoubleStateOf(0.0) }
    var isNewInput by remember { mutableStateOf(true) }

    // === Memory state ===
    var memory by remember { mutableDoubleStateOf(0.0) }
    var hasMemory by remember { mutableStateOf(false) }

    // === History state ===
    val history = remember { mutableStateListOf<String>() }
    var historyExpanded by remember { mutableStateOf(false) }

    data class Btn(val label: String, val bg: Color, val fg: Color = Color.White, val span: Int = 1)

    val rows = listOf(
        listOf(Btn("C", Color(0xFF9E9E9E), Color.Black), Btn("±", Color(0xFF9E9E9E), Color.Black), Btn("%", Color(0xFF9E9E9E), Color.Black), Btn("÷", Color(0xFFFF9800))),
        listOf(Btn("7", Color(0xFF424242)), Btn("8", Color(0xFF424242)), Btn("9", Color(0xFF424242)), Btn("×", Color(0xFFFF9800))),
        listOf(Btn("4", Color(0xFF424242)), Btn("5", Color(0xFF424242)), Btn("6", Color(0xFF424242)), Btn("−", Color(0xFFFF9800))),
        listOf(Btn("1", Color(0xFF424242)), Btn("2", Color(0xFF424242)), Btn("3", Color(0xFF424242)), Btn("+", Color(0xFFFF9800))),
        listOf(Btn("0", Color(0xFF424242), span = 2), Btn(".", Color(0xFF424242)), Btn("=", Color(0xFFFF9800)))
    )

    // Memory button row (added above the main buttons)
    data class MemBtn(val label: String, val bg: Color = Color(0xFF616161), val fg: Color = Color.White, val span: Int = 1)
    val memoryBtns = listOf(
        MemBtn("MC"), MemBtn("MR"), MemBtn("M+"), MemBtn("M−")
    )

    fun sanitize(s: String) = s.replace(",", "").replace("−", "-")

    fun formatNum(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "Lỗi"
        return if (d == d.toLong().toDouble())
            DecimalFormat("#,###").format(d.toLong())
        else
            DecimalFormat("#,###.##").format(d)
    }

    fun compute(a: Double, op: String, b: Double): Double {
        return when (op) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> if (b != 0.0) a / b else Double.NaN
            else -> b
        }
    }

    fun press(label: String) {
        when (label) {
            "C" -> {
                display = "0"; resultText = ""; operation = ""
                firstValue = 0.0; isNewInput = true
            }
            "±" -> {
                val v = sanitize(display).toDoubleOrNull() ?: return
                display = formatNum(-v)
            }
            "%" -> {
                val v = sanitize(display).toDoubleOrNull() ?: return
                display = formatNum(v / 100.0)
            }
            // Memory operations
            "MC" -> { memory = 0.0; hasMemory = false }
            "MR" -> {
                if (hasMemory) {
                    display = formatNum(memory); isNewInput = true
                }
            }
            "M+" -> {
                val v = sanitize(display).toDoubleOrNull() ?: return
                memory += v; hasMemory = true
            }
            "M−" -> {
                val v = sanitize(display).toDoubleOrNull() ?: return
                memory -= v; hasMemory = true
            }
            // Arithmetic operators
            in setOf("+", "−", "×", "÷") -> {
                val cur = sanitize(display).toDoubleOrNull() ?: 0.0
                if (!isNewInput && operation.isNotEmpty()) {
                    val r = compute(firstValue, operation, cur)
                    display = formatNum(r)
                    firstValue = r
                } else {
                    firstValue = cur
                }
                operation = label
                isNewInput = true
            }
            "=" -> {
                if (operation.isEmpty()) return
                val cur = sanitize(display).toDoubleOrNull() ?: return
                val r = compute(firstValue, operation, cur)
                val entry = "${formatNum(firstValue)} $operation ${formatNum(cur)} = ${formatNum(r)}"
                history.add(0, entry)
                resultText = "${formatNum(firstValue)} $operation ${formatNum(cur)} ="
                display = formatNum(r)
                firstValue = r
                operation = ""
                isNewInput = true
            }
            "." -> {
                if (isNewInput || display == "Lỗi") { display = "0."; isNewInput = false }
                else if ("." !in display) display += "."
            }
            else -> {
                if (isNewInput || display == "0" || display == "Lỗi") {
                    display = label; isNewInput = false
                } else {
                    display += label
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Máy Tính", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { historyExpanded = !historyExpanded }) {
                            Icon(
                                imageVector = if (historyExpanded) Icons.Default.ArrowDropUp
                                              else Icons.Default.ArrowDropDown,
                                contentDescription = if (historyExpanded) "Đóng lịch sử"
                                                     else "Mở lịch sử"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ======== HISTORY DROPDOWN PANEL ========
            AnimatedVisibility(
                visible = historyExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                if (history.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    ) {
                        Column {
                            // Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lịch sử tính toán",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = {
                                        history.clear()
                                        historyExpanded = false
                                    }
                                ) {
                                    Text(
                                        text = "Xóa tất cả",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Divider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                            // History items
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    horizontal = 20.dp,
                                    vertical = 6.dp
                                )
                            ) {
                                items(history) { entry ->
                                    Text(
                                        text = entry,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp),
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ======== DISPLAY AREA ========
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                // Expression preview: shows "firstValue operation" while building expression
                if (operation.isNotEmpty()) {
                    Text(
                        text = "${formatNum(firstValue)} $operation",
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Result line (shows the completed expression)
                if (resultText.isNotEmpty()) {
                    Text(
                        text = resultText,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Main display
                Text(
                    text = display,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ======== BUTTONS AREA ========
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ---- Memory row ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    memoryBtns.forEach { btn ->
                        Button(
                            onClick = { press(btn.label) },
                            modifier = Modifier
                                .weight(btn.span.toFloat())
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = btn.bg,
                                contentColor = btn.fg.copy(
                                    alpha = if (btn.label in setOf("MC", "MR") && !hasMemory) 0.35f
                                            else 1f
                                )
                            ),
                            enabled = when (btn.label) {
                                "MC", "MR" -> hasMemory
                                else -> true
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = btn.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ---- Main button rows (unchanged layout) ----
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { btn ->
                            Button(
                                onClick = { press(btn.label) },
                                modifier = Modifier
                                    .weight(if (btn.span > 1) btn.span.toFloat() else 1f)
                                    .aspectRatio(if (btn.span > 1) 2.3f else 1f),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = btn.bg,
                                    contentColor = btn.fg
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = btn.label,
                                    fontSize = if (btn.label == "0") 22.sp else 26.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
