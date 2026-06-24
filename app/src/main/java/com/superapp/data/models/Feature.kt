package com.superapp.data.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Feature(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val route: String
)

object Features {
    val list = listOf(
        Feature(
            id = "calculator",
            title = "Máy Tính",
            description = "Tính toán nhanh với giao diện thân thiện",
            icon = Icons.Default.Calculate,
            colors = listOf(Color(0xFF6C63FF), Color(0xFF3F51B5)),
            route = "calculator"
        ),
        Feature(
            id = "converter",
            title = "Đổi Đơn Vị",
            description = "Chuyển đổi đơn vị đo lường dễ dàng",
            icon = Icons.Default.SwapHoriz,
            colors = listOf(Color(0xFFFFA07A), Color(0xFFFF6347)),
            route = "converter"
        ),
        Feature(
            id = "qr",
            title = "Mã QR",
            description = "Tạo mã QR nhanh chóng từ văn bản",
            icon = Icons.Default.QrCode,
            colors = listOf(Color(0xFF7C4DFF), Color(0xFF448AFF)),
            route = "qr"
        ),
        Feature(
            id = "stopwatch",
            title = "Bấm Giờ",
            description = "Đo thời gian với đồng hồ bấm giờ chính xác",
            icon = Icons.Default.Timer,
            colors = listOf(Color(0xFFFFD93D), Color(0xFFFF8C00)),
            route = "stopwatch"
        ),
        Feature(
            id = "flashlight",
            title = "Đèn Pin",
            description = "Biến điện thoại thành đèn pin tiện lợi",
            icon = Icons.Default.FlashlightOn,
            colors = listOf(Color(0xFF00BCD4), Color(0xFF0097A7)),
            route = "flashlight"
        ),
        Feature(
            id = "wifi",
            title = "WiFi Analyzer",
            description = "Phân tích mạng WiFi, cường độ tín hiệu, nhiễu kênh",
            icon = Icons.Default.Wifi,
            colors = listOf(Color(0xFF1A237E), Color(0xFF283593)),
            route = "wifi_analyzer"
        )
    )
}
