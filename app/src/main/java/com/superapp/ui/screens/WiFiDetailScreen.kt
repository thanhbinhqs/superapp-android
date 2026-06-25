package com.superapp.ui.screens

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiDetailScreen(navController: NavController) {
    val context = LocalContext.current
    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }

    var refreshTick by remember { mutableStateOf(0) }

    // Refresh periodically
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2000)
            refreshTick++
        }
    }

    val connectionInfo = remember(refreshTick) {
        try {
            wifiManager.connectionInfo?.toConnectionInfo()
        } catch (_: Exception) {
            null
        }
    }

    val deviceMac = remember { getDeviceMacAddress() ?: "N/A" }
    val ipAddress = remember(refreshTick) { getDeviceIpAddress() }
    val gateway = remember(refreshTick) { getGatewayAddress() }
    val dnsServers = remember(refreshTick) { getDnsServers() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi Tiết Kết Nối", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            val info = connectionInfo
            val isConnected = info != null && info.ssid != "<unknown ssid>"

            Spacer(modifier = Modifier.height(8.dp))

            if (!isConnected) {
                // Not connected
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Chưa kết nối WiFi",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Kết nối WiFi để xem thông tin chi tiết",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                val info = connectionInfo!!
                val channel = frequencyToChannel(info.frequency)
                val (qualityLabel, qualityColor) = getSignalQuality(info.rssi)
                val normalizedSignal = ((info.rssi + 100f) / 70f).coerceIn(0f, 1f)

                // ── Signal status card ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Đang kết nối",
                            fontSize = 13.sp,
                            color = Color(0xFF00C853),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info.ssid,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Signal gauge (large)
                        val gaugeColor = qualityColor
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val radius = size.minDimension / 2f - 8f
                                val sweep = 240f
                                val startAngle = 150f

                                // Background arc
                                drawArc(
                                    color = Color(0xFF333333),
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                                    style = Stroke(width = 12f, cap = StrokeCap.Round)
                                )

                                // Signal arc
                                val signalSweep = sweep * normalizedSignal
                                drawArc(
                                    color = gaugeColor,
                                    startAngle = startAngle,
                                    sweepAngle = signalSweep,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                                    style = Stroke(width = 12f, cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${info.rssi}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = gaugeColor
                                )
                                Text(
                                    text = "dBm",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = qualityLabel,
                            fontSize = 16.sp,
                            color = gaugeColor,
                            fontWeight = FontWeight.Medium
                        )

                        // Signal bars indicator
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            for (i in 0..3) {
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height((10 + i * 8).dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (i < rssiToLevel(info.rssi)) gaugeColor else Color(0xFF333333))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Connection details card ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Thông số kết nối",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        InfoRow("SSID", info.ssid, Icons.Default.NetworkWifi)
                        InfoRow("BSSID", info.bssid, Icons.Default.Fingerprint)
                        InfoRow("Địa chỉ IP", ipAddress, Icons.Default.DeviceHub)
                        InfoRow("Gateway", gateway, Icons.Default.Router)
                        InfoRow("DNS", dnsServers.joinToString(", "), Icons.Default.Dns)
                        InfoRow("MAC thiết bị", deviceMac, Icons.Default.Phonelink)
                        InfoRow("Cường độ", "${info.rssi} dBm ($qualityLabel)", Icons.Default.SignalCellularAlt)
                        InfoRow("Tốc độ liên kết", "${info.linkSpeed} Mbps", Icons.Default.Speed)
                        InfoRow("Tần số", "${info.frequency} MHz", Icons.Default.Wifi)
                        InfoRow("Kênh", "CH $channel", Icons.Default.Tune)
                        InfoRow("Băng tần", "${getBandFromFrequency(info.frequency)} GHz", Icons.Default.Wifi)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── WiFi Info card ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Thông tin mạng",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val securityType = "<unknown>"
                        val (wifiGen, wifiGenColor) = getWifiGeneration("", info.frequency)
                        val manufacturer = getManufacturer(info.bssid)

                        InfoRow("Công nghệ", wifiGen, Icons.Default.SettingsEthernet)
                        InfoRow("Bảo mật", securityType, Icons.Default.Security)
                        InfoRow("IP Gateway", gateway, Icons.Default.Router)
                        if (manufacturer != null) {
                            InfoRow("Hãng sản xuất", manufacturer, Icons.Default.HomeRepairService)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Signal history (placeholder)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ShowChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Biểu đồ tín hiệu",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2
        )
    }
}
