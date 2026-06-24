package com.superapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Science
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
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(navController: NavController) {
    var display by remember { mutableStateOf("0") }
    var resultText by remember { mutableStateOf("") }
    var rawExpression by remember { mutableStateOf("") }
    var operation by remember { mutableStateOf("") }
    var firstValue by remember { mutableDoubleStateOf(0.0) }
    var isNewInput by remember { mutableStateOf(true) }
    var isScientificMode by remember { mutableStateOf(false) }

    // === Memory state ===
    var memory by remember { mutableDoubleStateOf(0.0) }
    var hasMemory by remember { mutableStateOf(false) }

    // === Parenthesis stack (simple nested evaluation support) ===
    data class ParenFrame(val value: Double, val op: String, val expr: String)
    val parenStack = remember { mutableStateListOf<ParenFrame>() }

    // === History state ===
    val history = remember { mutableStateListOf<String>() }
    var historyExpanded by remember { mutableStateOf(false) }

    // --- Button definitions ---

    data class Btn(val label: String, val bg: Color, val fg: Color = Color.White, val span: Int = 1)

    val rows = listOf(
        listOf(Btn("C", Color(0xFF9E9E9E), Color.Black), Btn("±", Color(0xFF9E9E9E), Color.Black), Btn("%", Color(0xFF9E9E9E), Color.Black), Btn("÷", Color(0xFFFF9800))),
        listOf(Btn("7", Color(0xFF424242)), Btn("8", Color(0xFF424242)), Btn("9", Color(0xFF424242)), Btn("×", Color(0xFFFF9800))),
        listOf(Btn("4", Color(0xFF424242)), Btn("5", Color(0xFF424242)), Btn("6", Color(0xFF424242)), Btn("−", Color(0xFFFF9800))),
        listOf(Btn("1", Color(0xFF424242)), Btn("2", Color(0xFF424242)), Btn("3", Color(0xFF424242)), Btn("+", Color(0xFFFF9800))),
        listOf(Btn("0", Color(0xFF424242), span = 2), Btn(".", Color(0xFF424242)), Btn("=", Color(0xFFFF9800)))
    )

    // Memory row
    data class MemBtn(val label: String, val bg: Color = Color(0xFF616161), val fg: Color = Color.White, val span: Int = 1)
    val memoryBtns = listOf(
        MemBtn("MC"), MemBtn("MR"), MemBtn("M+"), MemBtn("M−")
    )

    // Scientific mode rows (#616161 background)
    data class SciBtn(val label: String, val bg: Color = Color(0xFF616161), val fg: Color = Color.White, val span: Int = 1)
    val scientificRows = listOf(
        listOf(SciBtn("sin"), SciBtn("cos"), SciBtn("tan"), SciBtn("("), SciBtn(")")),
        listOf(SciBtn("log"), SciBtn("ln"), SciBtn("√"), SciBtn("x²"), SciBtn("x³")),
        listOf(SciBtn("xⁿ"), SciBtn("π"), SciBtn("e"), SciBtn("%"), SciBtn("!"))
    )

    // --- Helper functions ---

    fun sanitize(s: String) = s.replace(",", "").replace("−", "-")

    fun formatNum(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "Lỗi"
        // Format small/large numbers with scientific notation
        if (d != 0.0 && (abs(d) < 1e-8 || abs(d) >= 1e12)) {
            val s = String.format("%.6e", d)
            // Clean up trailing zeros after decimal
            val parts = s.split("e")
            val mantissa = parts[0].trimEnd('0').trimEnd('.')
            return "${mantissa}e${parts[1]}"
        }
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
            "^" -> a.pow(b)
            else -> b
        }
    }

    fun factorial(n: Double): Double {
        if (n < 0 || n != floor(n)) return Double.NaN
        val m = n.toInt()
        var result = 1.0
        for (i in 2..m) result *= i
        return result
    }

    fun applyScientificFunc(func: String, value: Double): Double {
        return when (func) {
            "sin"  -> sin(value * PI / 180.0)   // degree mode
            "cos"  -> cos(value * PI / 180.0)
            "tan"  -> tan(value * PI / 180.0)
            "log"  -> log10(value)
            "ln"   -> ln(value)
            "√"    -> sqrt(value)
            "x²"   -> value * value
            "x³"   -> value * value * value
            "!"    -> factorial(value)
            else   -> value
        }
    }

    fun evaluateCurrent(): Double? {
        val cur = sanitize(display).toDoubleOrNull() ?: return null
        if (operation.isEmpty()) return cur
        return compute(firstValue, operation, cur)
    }

    fun press(label: String) {
        when (label) {
            // ---- Clear ----
            "C" -> {
                display = "0"; resultText = ""; rawExpression = ""
                operation = ""; firstValue = 0.0; isNewInput = true
                parenStack.clear()
            }

            // ---- Negate ----
            "±" -> {
                val v = sanitize(display).toDoubleOrNull() ?: return
                display = formatNum(-v)
            }

            // ---- Memory ----
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

            // ---- Scientific unary functions ----
            in setOf("sin", "cos", "tan", "log", "ln", "√", "x²", "x³", "!") -> {
                val v = sanitize(display).toDoubleOrNull()
                if (v == null || v.isNaN()) { display = "Lỗi"; return }
                val funcDisplay = when (label) {
                    "sin", "cos", "tan", "log", "ln", "√" -> "$label(${formatNum(v)})"
                    "x²" -> "sqr(${formatNum(v)})"
                    "x³" -> "cube(${formatNum(v)})"
                    "!" -> "${formatNum(v)}!"
                    else -> "$label(${formatNum(v)})"
                }
                rawExpression = funcDisplay
                val r = applyScientificFunc(label, v)
                if (r.isNaN() || r.isInfinite()) {
                    display = "Lỗi"
                    resultText = "$funcDisplay = Lỗi"
                } else {
                    val entry = "$funcDisplay = ${formatNum(r)}"
                    history.add(0, entry)
                    resultText = entry
                    display = formatNum(r)
                    rawExpression = funcDisplay
                }
                isNewInput = true
            }

            // ---- Constants ----
            "π" -> {
                display = formatNum(PI)
                rawExpression = "π"
                isNewInput = false
            }
            "e" -> {
                display = formatNum(E)
                rawExpression = "e"
                isNewInput = false
            }

            // ---- Parentheses ----
            "(" -> {
                // Save current state onto stack and reset
                val cur = sanitize(display).toDoubleOrNull() ?: 0.0
                parenStack.add(ParenFrame(cur, operation, rawExpression))
                // Reset for the sub-expression
                firstValue = 0.0; operation = ""; isNewInput = true
                rawExpression += "("
            }
            ")" -> {
                if (parenStack.isEmpty()) return
                val cur = sanitize(display).toDoubleOrNull() ?: return
                // Evaluate the sub-expression if there's an operation
                var subResult = cur
                if (operation.isNotEmpty()) {
                    subResult = compute(firstValue, operation, cur)
                }
                // Restore parent context
                val frame = parenStack.removeLast()
                firstValue = frame.value
                operation = frame.op
                rawExpression = "${frame.expr}${formatNum(subResult)})"
                display = formatNum(subResult)
                isNewInput = true
            }

            // ---- Scientific binary operator (power) ----
            "xⁿ" -> {
                val cur = sanitize(display).toDoubleOrNull() ?: 0.0
                if (!isNewInput && operation.isNotEmpty()) {
                    val r = compute(firstValue, operation, cur)
                    display = formatNum(r)
                    firstValue = r
                } else {
                    firstValue = cur
                }
                operation = "^"
                rawExpression = "${formatNum(firstValue)} ^"
                isNewInput = true
            }

            // ---- Arithmetic operators ----
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
                rawExpression = "${formatNum(firstValue)} ${operation}"
                isNewInput = true
            }

            // ---- Equals ----
            "=" -> {
                if (operation.isEmpty()) {
                    // If there are pending parentheses, close them
                    if (parenStack.isNotEmpty()) {
                        press(")")
                    }
                    return
                }
                val cur = sanitize(display).toDoubleOrNull() ?: return
                val r = compute(firstValue, operation, cur)
                val expr = "${formatNum(firstValue)} $operation ${formatNum(cur)}"
                val entry = "$expr = ${formatNum(r)}"
                history.add(0, entry)
                rawExpression = expr
                resultText = entry
                display = formatNum(r)
                firstValue = r
                operation = ""
                isNewInput = true
            }

            // ---- Decimal point ----
            "." -> {
                if (isNewInput || display == "Lỗi") {
                    display = "0."
                    isNewInput = false
                } else if ("." !in display) {
                    display += "."
                }
            }

            // ---- Percent (same in both modes) ----
            "%" -> {
                val v = sanitize(display).toDoubleOrNull() ?: return
                display = formatNum(v / 100.0)
                rawExpression = "${formatNum(v)}%"
            }

            // ---- Digit input ----
            else -> {
                if (isNewInput || display == "0" || display == "Lỗi") {
                    display = label
                    isNewInput = false
                } else {
                    // Avoid overflow — max 16 digits
                    val clean = sanitize(display).replace(".", "").replace("-", "")
                    if (clean.length >= 16) return
                    display += label
                }
            }
        }
    }

    // ================================================================
    //                          UI LAYOUT
    // ================================================================

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isScientificMode) "Máy Tính Khoa Học" else "Máy Tính",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    // Toggle Scientific / Standard mode
                    IconButton(onClick = {
                        isScientificMode = !isScientificMode
                        // Reset calculator state when switching modes
                        display = "0"; resultText = ""; rawExpression = ""
                        operation = ""; firstValue = 0.0; isNewInput = true
                        parenStack.clear()
                    }) {
                        Icon(
                            imageVector = if (isScientificMode) Icons.Default.Calculate
                                          else Icons.Default.Science,
                            contentDescription = if (isScientificMode) "Chuyển sang Standard"
                                                  else "Chuyển sang Scientific"
                        )
                    }
                    // History toggle
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
                // Raw expression input (shows scientific expressions or built operation)
                if (rawExpression.isNotEmpty()) {
                    Text(
                        text = rawExpression,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Expression preview: shows "firstValue operation" while building
                if (operation.isNotEmpty() && rawExpression.isEmpty()) {
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

                // Result line (shows the completed expression with result)
                if (resultText.isNotEmpty()) {
                    Text(
                        text = resultText,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Main display
                Text(
                    text = display,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                // ---- Scientific buttons (only in scientific mode) ----
                if (isScientificMode) {
                    scientificRows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { btn ->
                                Button(
                                    onClick = { press(btn.label) },
                                    modifier = Modifier
                                        .weight(btn.span.toFloat())
                                        .height(44.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = btn.bg,
                                        contentColor = btn.fg
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = btn.label,
                                        fontSize = if (btn.label.length <= 2) 18.sp else 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // ---- Memory row (only in standard mode) ----
                if (!isScientificMode) {
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
