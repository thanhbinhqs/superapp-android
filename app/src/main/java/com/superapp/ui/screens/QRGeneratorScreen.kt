package com.superapp.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QRGeneratorScreen(navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ── Core state ──────────────────────────────────────────────────────────
    var inputText by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    // ── Enhanced options ────────────────────────────────────────────────────
    var selectedDataType by remember { mutableStateOf(QRDataType.TEXT) }
    var selectedErrorLevel by remember { mutableStateOf(ErrorCorrectionLevel.M) }
    var selectedSize by remember { mutableStateOf(QRSize.SIZE_512) }
    var foregroundColor by remember { mutableStateOf(Color.Black) }
    var backgroundColor by remember { mutableStateOf(Color.White) }

    // WiFi sub-fields
    var wifiSSID by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var wifiEncryption by remember { mutableStateOf("WPA") }

    // ── Preset colors ───────────────────────────────────────────────────────
    val colorPresets = listOf(
        Color.Black, Color(0xFF1A237E), Color(0xFFB71C1C), Color(0xFF1B5E20),
        Color(0xFFE65100), Color(0xFF4A148C), Color(0xFF004D40), Color(0xFF263238)
    )
    val bgPresets = listOf(
        Color.White, Color(0xFFFFF8E1), Color(0xFFE3F2FD), Color(0xFFE8F5E9),
        Color(0xFFFCE4EC), Color(0xFFF3E5F5), Color(0xFFE0F7FA)
    )

    // ── Helpers ─────────────────────────────────────────────────────────────
    fun buildQRContent(): String = when (selectedDataType) {
        QRDataType.TEXT  -> inputText
        QRDataType.URL   -> {
            val t = inputText.trim()
            if (t.isNotEmpty() && !t.startsWith("http://") && !t.startsWith("https://"))
                "https://$t" else t
        }
        QRDataType.PHONE -> "tel:${inputText.trim()}"
        QRDataType.EMAIL -> "mailto:${inputText.trim()}"
        QRDataType.WIFI  -> {
            val enc = when (wifiEncryption.uppercase()) {
                "WPA" -> "WPA"
                "WEP" -> "WEP"
                else  -> "nopass"
            }
            "WIFI:S:${wifiSSID.trim()};T:${enc};P:${wifiPassword.trim()};;"
        }
    }

    fun getPlaceholder(): String = when (selectedDataType) {
        QRDataType.TEXT  -> "Nhập văn bản..."
        QRDataType.URL   -> "https://example.com"
        QRDataType.PHONE -> "+84123456789"
        QRDataType.EMAIL -> "email@example.com"
        QRDataType.WIFI  -> "Tên WiFi (SSID)"
    }

    fun getDataTypeIcon(): @Composable () -> Unit = when (selectedDataType) {
        QRDataType.TEXT  -> { { Icon(Icons.Default.TextFields, contentDescription = null) } }
        QRDataType.URL   -> { { Icon(Icons.Default.Link, contentDescription = null) } }
        QRDataType.PHONE -> { { Icon(Icons.Default.Phone, contentDescription = null) } }
        QRDataType.EMAIL -> { { Icon(Icons.Default.Email, contentDescription = null) } }
        QRDataType.WIFI  -> { { Icon(Icons.Default.Wifi, contentDescription = null) } }
    }

    fun generateQR() {
        val content = buildQRContent()
        if (content.isBlank() || (selectedDataType == QRDataType.WIFI && wifiSSID.isBlank())) {
            errorMessage = when (selectedDataType) {
                QRDataType.WIFI -> "Vui lòng nhập tên WiFi"
                else            -> "Vui lòng nhập nội dung"
            }
            qrBitmap = null
            return
        }
        errorMessage = ""
        try {
            val writer = QRCodeWriter()
            val hints = mapOf(EncodeHintType.ERROR_CORRECTION to selectedErrorLevel)
            val size = selectedSize.px
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val w = bitMatrix.width
            val h = bitMatrix.height
            val fg = foregroundColor.toArgb()
            val bg = backgroundColor.toArgb()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            for (x in 0 until w) {
                for (y in 0 until h) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) fg else bg)
                }
            }
            qrBitmap = bmp
        } catch (e: Exception) {
            errorMessage = "Lỗi: ${e.message}"
            qrBitmap = null
        }
    }

    fun saveQRToDevice(bitmap: Bitmap) {
        try {
            val filename = "QR_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SuperApp")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Toast.makeText(context, "Đã lưu: $filename", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Không thể tạo file", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Fallback for older API
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(dir, "SuperApp")
                if (!appDir.exists()) appDir.mkdirs()
                val file = File(appDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Toast.makeText(context, "Đã lưu: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi lưu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── UI ──────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mã QR", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
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
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── 1. Data Type Selector ──────────────────────────────────────────
            Text(
                "Loại dữ liệu",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QRDataType.values()) { type ->
                    val isSelected = selectedDataType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedDataType = type },
                        label = { Text(type.label, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (type) {
                                    QRDataType.TEXT  -> Icons.Default.TextFields
                                    QRDataType.URL   -> Icons.Default.Link
                                    QRDataType.PHONE -> Icons.Default.Phone
                                    QRDataType.EMAIL -> Icons.Default.Email
                                    QRDataType.WIFI  -> Icons.Default.Wifi
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF7C4DFF).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF7C4DFF)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 2. Input field(s) ─────────────────────────────────────────────
            if (selectedDataType == QRDataType.WIFI) {
                // WiFi: SSID
                OutlinedTextField(
                    value = wifiSSID,
                    onValueChange = { wifiSSID = it; errorMessage = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên WiFi (SSID)") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF7C4DFF),
                        cursorColor = Color(0xFF7C4DFF)
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                // WiFi: Password
                OutlinedTextField(
                    value = wifiPassword,
                    onValueChange = { wifiPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mật khẩu") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF7C4DFF),
                        cursorColor = Color(0xFF7C4DFF)
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                // WiFi: Encryption
                Text("Bảo mật", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("WPA", "WEP", "None").forEach { enc ->
                        val isEnc = wifiEncryption == enc
                        AssistChip(
                            onClick = { wifiEncryption = enc },
                            label = { Text(enc, fontSize = 13.sp) },
                            leadingIcon = {
                                if (isEnc) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it; errorMessage = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(selectedDataType.label) },
                    placeholder = { Text(getPlaceholder()) },
                    leadingIcon = getDataTypeIcon(),
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = when (selectedDataType) {
                            QRDataType.PHONE -> KeyboardType.Phone
                            QRDataType.EMAIL -> KeyboardType.Email
                            QRDataType.URL   -> KeyboardType.Uri
                            else             -> KeyboardType.Text
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF7C4DFF),
                        cursorColor = Color(0xFF7C4DFF)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 3. Error Correction Level ──────────────────────────────────────
            Text(
                "Mức sửa lỗi",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    listOf(
                        ErrorCorrectionLevel.L to "L (7%)",
                        ErrorCorrectionLevel.M to "M (15%)",
                        ErrorCorrectionLevel.Q to "Q (25%)",
                        ErrorCorrectionLevel.H to "H (30%)"
                    )
                ) { (level, label) ->
                    val isSelected = selectedErrorLevel == level
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedErrorLevel = level },
                        label = { Text(label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF7C4DFF).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF7C4DFF)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 4. QR Size ─────────────────────────────────────────────────────
            Text(
                "Kích thước",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QRSize.values()) { size ->
                    val isSelected = selectedSize == size
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSize = size },
                        label = { Text(size.label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF7C4DFF).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF7C4DFF)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 5. Color Selectors ─────────────────────────────────────────────
            Text(
                "Màu sắc",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Foreground
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mã QR:", fontSize = 13.sp, modifier = Modifier.width(64.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(colorPresets) { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (foregroundColor == c) 2.dp else 0.dp,
                                    color = if (foregroundColor == c) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { foregroundColor = c }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Background
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nền:", fontSize = 13.sp, modifier = Modifier.width(64.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(bgPresets) { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (backgroundColor == c) 2.dp else 0.dp,
                                    color = if (backgroundColor == c) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { backgroundColor = c }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 6. Generate Button ─────────────────────────────────────────────
            Button(
                onClick = { generateQR() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C4DFF)
                )
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tạo Mã QR", fontSize = 16.sp)
            }

            // ── Error ──────────────────────────────────────────────────────────
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 7. QR Code Display ─────────────────────────────────────────────
            if (qrBitmap != null) {
                Card(
                    modifier = Modifier
                        .size(300.dp)
                        .padding(8.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = backgroundColor
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(260.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content preview
                Text(
                    text = buildQRContent(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── 8. Save Button ──────────────────────────────────────────────
                OutlinedButton(
                    onClick = { qrBitmap?.let { saveQRToDevice(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF7C4DFF))
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lưu ảnh QR", color = Color(0xFF7C4DFF), fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

            } else if (!errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Mã QR sẽ hiển thị ở đây",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(200.dp))
            } else {
                Spacer(modifier = Modifier.height(200.dp))
            }
        }
    }
}

// ── Supporting enums / data classes ─────────────────────────────────────────

enum class QRDataType(val label: String) {
    TEXT("Văn bản"),
    URL("URL"),
    PHONE("Số điện thoại"),
    EMAIL("Email"),
    WIFI("WiFi")
}

enum class QRSize(val label: String, val px: Int) {
    SIZE_256("256×256", 256),
    SIZE_512("512×512", 512),
    SIZE_1024("1024×1024", 1024)
}
