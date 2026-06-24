package com.superapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.math.*

// ── Data classes ──

data class WiFiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,           // in dBm (negative)
    val frequency: Int,       // in MHz
    val capabilities: String, // security info
    val isConnected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ConnectionInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val linkSpeed: Int,       // Mbps
    val frequency: Int,       // MHz
    val ipAddress: String
)

// ── Enums ──

enum class BandFilter(val label: String, val icon: String) {
    ALL("Tất cả", "📡"),
    GHZ_2_4("2.4GHz", "📶"),
    GHZ_5("5GHz", "📶"),
    GHZ_6("6GHz", "📶")
}

enum class SortMode(val label: String) {
    BY_SSID("Theo tên"),
    BY_SIGNAL("Theo tín hiệu")
}

enum class AnalyzerTab(val label: String) {
    LIST("Danh Sách"),
    CHANNEL_GRAPH("Biểu Đồ Kênh"),
    DETAILS("Chi Tiết")
}

// ── Helper functions ──

/**
 * Convert RSSI (dBm) to signal level 0-4 (0 = very weak, 4 = excellent)
 */
private fun rssiToLevel(rssi: Int): Int {
    return when {
        rssi >= -50 -> 4
        rssi >= -60 -> 3
        rssi >= -70 -> 2
        rssi >= -80 -> 1
        else -> 0
    }
}

/**
 * Get channel number from frequency in MHz
 */
private fun frequencyToChannel(freq: Int): Int {
    return when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        freq in 5955..7125 -> (freq - 5955) / 5 + 1
        else -> 0
    }
}

/**
 * Get frequency band group from frequency in MHz
 */
private fun getBandLabel(freq: Int): String {
    return when {
        freq < 2500 -> "2.4 GHz"
        freq < 6000 -> "5 GHz"
        else -> "6 GHz"
    }
}

/**
 * Get security type from capabilities string
 */
private fun getSecurityType(capabilities: String): String {
    return when {
        capabilities.contains("WPA3", ignoreCase = true) -> "WPA3"
        capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
        capabilities.contains("WPA", ignoreCase = true) -> "WPA"
        capabilities.contains("WEP", ignoreCase = true) -> "WEP"
        capabilities.contains("OWE", ignoreCase = true) -> "OWE"
        capabilities.contains("SAE", ignoreCase = true) -> "WPA3"
        else -> "Open"
    }
}

/**
 * Get signal quality label and color
 */
private fun getSignalQuality(rssi: Int): Pair<String, Color> {
    return when {
        rssi >= -50 -> "Tuyệt vời" to Color(0xFF00C853)
        rssi >= -60 -> "Tốt" to Color(0xFF76FF03)
        rssi >= -70 -> "Trung bình" to Color(0xFFFFD600)
        rssi >= -80 -> "Yếu" to Color(0xFFFF6D00)
        else -> "Rất yếu" to Color(0xFFD50000)
    }
}

/**
 * Get gradient colors for signal strength card
 */
private fun signalGradient(rssi: Int): Pair<Color, Color> {
    return when {
        rssi >= -50 -> Color(0xFF1B5E20) to Color(0xFF00C853)
        rssi >= -60 -> Color(0xFF33691E) to Color(0xFF76FF03)
        rssi >= -70 -> Color(0xFFF57F17) to Color(0xFFFFD600)
        rssi >= -80 -> Color(0xFFE65100) to Color(0xFFFF6D00)
        else -> Color(0xFFB71C1C) to Color(0xFFD50000)
    }
}

/**
 * Get IP address from network interface
 */
private fun getDeviceIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "N/A"
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface == null || !networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses ?: continue
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement() ?: continue
                if (!addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    if (ip.contains('.')) return ip
                }
            }
        }
    } catch (_: Exception) {}
    return "N/A"
}

// ── Composable ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current

    // ── State ──
    var wifiManager by remember { mutableStateOf<WifiManager?>(null) }
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var connectionInfo by remember { mutableStateOf<ConnectionInfo?>(null) }
    var selectedNetwork by remember { mutableStateOf<WiFiNetwork?>(null) }
    var selectedTab by remember { mutableStateOf(AnalyzerTab.LIST) }
    var bandFilter by remember { mutableStateOf(BandFilter.ALL) }
    var sortMode by remember { mutableStateOf(SortMode.BY_SIGNAL) }
    var searchQuery by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(true) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ── Permission launcher ──
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        hasLocationPermission = allGranted
        permissionDenied = !allGranted
    }

    // ── Init WifiManager & check permissions ──
    LaunchedEffect(Unit) {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager = wm

        if (wm == null) {
            errorMessage = "Thiết bị không hỗ trợ WiFi"
            return@LaunchedEffect
        }

        // Check permissions
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (permissionsNeeded.isNotEmpty()) {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            hasLocationPermission = true
        }
    }

    // ── Scanning coroutine ──
    LaunchedEffect(isScanning, hasLocationPermission) {
        if (!isScanning || !hasLocationPermission) return@LaunchedEffect
        val wm = wifiManager ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect

        while (isActive) {
            // Re-check permission on each cycle (user may revoke mid-session)
            val stillHasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            if (!stillHasPermission || !isScanning) break

            try {
                // Start a scan — on API 33+ use new API, fallback to old
                val started = try {
                    @Suppress("DEPRECATION")
                    wm.startScan()
                } catch (_: SecurityException) { false }
                if (!started) {
                    // Scan rate limited; will succeed eventually
                }
            } catch (_: Exception) {}

            delay(500) // small delay for scan to complete

            // Read results
            val results = try {
                @Suppress("DEPRECATION")
                wm.scanResults
            } catch (_: Exception) { emptyList() }

            // Get connection info
            try {
                @Suppress("DEPRECATION")
                val wifiInfo = wm.connectionInfo
                if (wifiInfo != null) {
                    val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: "<Unknown>"
                    val bssid = wifiInfo.bssid ?: ""
                    val ip = getDeviceIpAddress()
                    connectionInfo = ConnectionInfo(
                        ssid = ssid,
                        bssid = bssid,
                        rssi = wifiInfo.rssi,
                        linkSpeed = wifiInfo.linkSpeed,
                        frequency = wifiInfo.frequency,
                        ipAddress = ip
                    )
                }
            } catch (_: Exception) {}

            // Filter and deduplicate results
            val connectedBssid = connectionInfo?.bssid ?: ""
            @Suppress("DEPRECATION")
            scanResults = results
                .filter { it.SSID.isNotEmpty() }
                .distinctBy { it.BSSID }
                .sortedByDescending { it.level }

            // If selected network is no longer in results, clear selection
            if (selectedNetwork != null) {
                val stillExists = results.any { it.BSSID == selectedNetwork!!.bssid }
                if (!stillExists) {
                    selectedNetwork = null
                }
            }

            // Auto-select first if none selected
            if (selectedNetwork == null && scanResults.isNotEmpty()) {
                val first = scanResults.first()
                @Suppress("DEPRECATION")
                selectedNetwork = WiFiNetwork(
                    ssid = first.SSID,
                    bssid = first.BSSID,
                    rssi = first.level,
                    frequency = first.frequency,
                    capabilities = first.capabilities,
                    isConnected = first.BSSID == connectedBssid
                )
            }

            errorMessage = null
            delay(4500) // total cycle ~5 seconds
        }
    }

    // ── Process scanResults into WiFiNetwork list ──
    val networks = remember(scanResults, connectionInfo, bandFilter, sortMode, searchQuery) {
        val connectedBssid = connectionInfo?.bssid ?: ""
        var list = @Suppress("DEPRECATION") scanResults.map { result ->
            WiFiNetwork(
                ssid = result.SSID,
                bssid = result.BSSID,
                rssi = result.level,
                frequency = result.frequency,
                capabilities = result.capabilities,
                isConnected = result.BSSID == connectedBssid
            )
        }

        // Filter by band
        list = when (bandFilter) {
            BandFilter.ALL -> list
            BandFilter.GHZ_2_4 -> list.filter { it.frequency < 2500 }
            BandFilter.GHZ_5 -> list.filter { it.frequency in 4900..6000 }
            BandFilter.GHZ_6 -> list.filter { it.frequency >= 5955 }
        }

        // Filter by search
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            list = list.filter {
                it.ssid.lowercase().contains(q) || it.bssid.lowercase().contains(q)
            }
        }

        // Sort
        list = when (sortMode) {
            SortMode.BY_SSID -> list.sortedBy { it.ssid.lowercase() }
            SortMode.BY_SIGNAL -> list.sortedByDescending { it.rssi }
        }

        list
    }

    // ── Channel data for graph ──
    val channelData = remember(scanResults, bandFilter) {
        // Collect all networks for channel graph (filter by band)
        val results = when (bandFilter) {
            BandFilter.ALL -> scanResults
            BandFilter.GHZ_2_4 -> scanResults.filter { it.frequency < 2500 }
            BandFilter.GHZ_5 -> scanResults.filter { it.frequency in 4900..6000 }
            BandFilter.GHZ_6 -> scanResults.filter { it.frequency >= 5955 }
        }
        // Group by channel
        @Suppress("DEPRECATION")
        results
            .filter { it.SSID.isNotEmpty() }
            .groupBy { frequencyToChannel(it.frequency) }
            .mapValues { it.value.size }
            .toSortedMap()
    }

    // ── Main UI ──
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "WiFi Analyzer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (isScanning) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val infiniteTransition = rememberInfiniteTransition(label = "scan")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulse"
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00C853).copy(alpha = alpha))
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isScanning = !isScanning
                        if (isScanning) {
                            // Force a scan by toggling
                            @Suppress("DEPRECATION")
                            wifiManager?.startScan()
                        }
                    }) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isScanning) "Tạm dừng" else "Tiếp tục",
                            tint = if (isScanning) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        @Suppress("DEPRECATION")
                        wifiManager?.startScan()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Làm mới"
                        )
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
            // ── Permission denied screen ──
            if (permissionDenied && errorMessage != "Thiết bị không hỗ trợ WiFi") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationSearching,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cần quyền truy cập WiFi",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Không thể phân tích WiFi nếu không cấp quyền",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                permissionDenied = false
                                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(
                                        Manifest.permission.NEARBY_WIFI_DEVICES,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                } else {
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                                permissionLauncher.launch(permissions)
                            }
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cấp quyền")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                ).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mở Cài Đặt")
                        }
                    }
                }
                return@Column
            }

            // ── Permission loading ──
            if (!hasLocationPermission && !permissionDenied && errorMessage != "Thiết bị không hỗ trợ WiFi") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Đang xin quyền...",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                return@Column
            }

            // ── Error message ──
            if (errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Current Connection Card ──
            connectionInfo?.let { conn ->
                CurrentConnectionCard(conn)
            }

            // ── Search & Filter Bar ──
            FilterSortBar(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                bandFilter = bandFilter,
                onBandFilterChange = { bandFilter = it },
                sortMode = sortMode,
                onSortModeChange = { sortMode = it },
                networkCount = networks.size,
                totalCount = scanResults.size
            )

            // ── Tab Row ──
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                AnalyzerTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (tab) {
                                        AnalyzerTab.LIST -> Icons.AutoMirrored.Filled.List
                                        AnalyzerTab.CHANNEL_GRAPH -> Icons.Default.BarChart
                                        AnalyzerTab.DETAILS -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tab.label, fontSize = 13.sp)
                            }
                        }
                    )
                }
            }

            // ── Tab Content ──
            when (selectedTab) {
                AnalyzerTab.LIST -> {
                    NetworkListTab(
                        networks = networks,
                        selectedNetwork = selectedNetwork,
                        onNetworkClick = { network ->
                            selectedNetwork = if (selectedNetwork?.bssid == network.bssid) {
                                null // deselect
                            } else network
                        }
                    )
                }
                AnalyzerTab.CHANNEL_GRAPH -> {
                    ChannelGraphTab(
                        channelData = channelData,
                        bandFilter = bandFilter,
                        onBandFilterChange = { bandFilter = it }
                    )
                }
                AnalyzerTab.DETAILS -> {
                    DetailsTab(
                        selectedNetwork = selectedNetwork,
                        connectionInfo = connectionInfo,
                        networks = networks
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────
//  Current Connection Card
// ──────────────────────────────────────────────────────────────────

@Composable
private fun CurrentConnectionCard(conn: ConnectionInfo) {
    val (qualityLabel, qualityColor) = getSignalQuality(conn.rssi)
    val level = rssiToLevel(conn.rssi)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đang kết nối",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                // Signal level indicator
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 0..3) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(if (i == 0) 8.dp else (8 + i * 4).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (i < level) qualityColor
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = conn.rssi.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor
                )
                Text(
                    text = " dBm",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SSID
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NetworkCheck,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = conn.ssid,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailChip(
                    icon = Icons.Default.Speed,
                    label = "${conn.linkSpeed} Mbps"
                )
                DetailChip(
                    icon = Icons.Default.SignalCellularAlt,
                    label = getBandLabel(conn.frequency)
                )
                DetailChip(
                    icon = Icons.Default.Router,
                    label = "CH ${frequencyToChannel(conn.frequency)}"
                )
                DetailChip(
                    icon = Icons.Default.Language,
                    label = conn.ipAddress
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quality text
            Text(
                text = "Chất lượng: $qualityLabel",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = qualityColor
            )
        }
    }
}

@Composable
private fun DetailChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ──────────────────────────────────────────────────────────────────
//  Filter & Sort Bar
// ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSortBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    bandFilter: BandFilter,
    onBandFilterChange: (BandFilter) -> Unit,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    networkCount: Int,
    totalCount: Int
) {
    var showSearch by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Top row: filter chips + sort toggle + search toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Band filter chips
            BandFilter.entries.forEach { band ->
                FilterChip(
                    selected = bandFilter == band,
                    onClick = { onBandFilterChange(band) },
                    label = {
                        Text(
                            band.label,
                            fontSize = 11.sp,
                            fontWeight = if (bandFilter == band) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Text(band.icon, fontSize = 10.sp)
                    },
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Count badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "$networkCount/$totalCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Sort mode toggle
            IconButton(
                onClick = {
                    onSortModeChange(
                        if (sortMode == SortMode.BY_SIGNAL) SortMode.BY_SSID
                        else SortMode.BY_SIGNAL
                    )
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (sortMode == SortMode.BY_SIGNAL) Icons.Default.SignalCellularAlt
                    else Icons.Default.SortByAlpha,
                    contentDescription = sortMode.label,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Search toggle
            IconButton(
                onClick = { showSearch = !showSearch },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (showSearch) Icons.Default.SearchOff else Icons.Default.Search,
                    contentDescription = "Tìm kiếm",
                    modifier = Modifier.size(18.dp),
                    tint = if (showSearch) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Search field (animated)
        AnimatedVisibility(
            visible = showSearch,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                placeholder = { Text("Tìm kiếm SSID hoặc BSSID...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { /* search is live */ }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────
//  Network List Tab
// ──────────────────────────────────────────────────────────────────

@Composable
private fun NetworkListTab(
    networks: List<WiFiNetwork>,
    selectedNetwork: WiFiNetwork?,
    onNetworkClick: (WiFiNetwork) -> Unit
) {
    if (networks.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Không tìm thấy mạng WiFi",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Hãy đảm bảo WiFi đã bật và có quyền truy cập vị trí",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = 6.dp, bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(networks, key = { it.bssid + it.ssid }) { network ->
            NetworkCard(
                network = network,
                isSelected = selectedNetwork?.bssid == network.bssid,
                onClick = { onNetworkClick(network) }
            )

            // Show signal meter below selected network
            if (selectedNetwork?.bssid == network.bssid) {
                SignalMeterCard(network)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────
//  Network Card
// ──────────────────────────────────────────────────────────────────

@Composable
private fun NetworkCard(
    network: WiFiNetwork,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val level = rssiToLevel(network.rssi)
    val (_, endGradient) = signalGradient(network.rssi)
    val securityType = getSecurityType(network.capabilities)
    val channel = frequencyToChannel(network.frequency)
    val bandLabel = getBandLabel(network.frequency)
    val (qualityLabel, _) = getSignalQuality(network.rssi)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Box {
            // Gradient background based on signal
            if (network.isConnected) {
                // Connected: subtle primary gradient
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                                )
                            )
                        )
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                // Top row: SSID + connected badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lock icon for secured networks
                    Icon(
                        imageVector = if (securityType == "Open") Icons.Default.Wifi
                        else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (network.isConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // SSID
                    Text(
                        text = network.ssid,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Connected badge
                    if (network.isConnected) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Đã kết nối",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Middle row: Signal bar + dBm + security + channel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Signal bars
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 0..3) {
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(if (i == 0) 8.dp else (8 + i * 4).dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (i < level) endGradient
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${network.rssi} dBm",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = endGradient
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "($qualityLabel)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Channel + Band
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "CH $channel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (bandLabel) {
                            "6 GHz" -> Color(0xFF7C4DFF).copy(alpha = 0.15f)
                            "5 GHz" -> Color(0xFF00BCD4).copy(alpha = 0.15f)
                            else -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = bandLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (bandLabel) {
                                "6 GHz" -> Color(0xFF7C4DFF)
                                "5 GHz" -> Color(0xFF00BCD4)
                                else -> Color(0xFF4CAF50)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom row: BSSID + security + frequency
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = network.bssid,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Security badge
                    val securityColor = when (securityType) {
                        "Open" -> Color(0xFF4CAF50)
                        "WEP" -> Color(0xFFFF9800)
                        "WPA" -> Color(0xFF2196F3)
                        "WPA2" -> Color(0xFF1565C0)
                        "WPA3" -> Color(0xFF7C4DFF)
                        "OWE" -> Color(0xFF00BCD4)
                        else -> Color(0xFF9E9E9E)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = securityColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = securityType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = securityColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${network.frequency} MHz",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────
//  Signal Meter Card (Analog Gauge)
// ──────────────────────────────────────────────────────────────────

@Composable
private fun SignalMeterCard(network: WiFiNetwork) {
    val (qualityLabel, qualityColor) = getSignalQuality(network.rssi)
    val level = rssiToLevel(network.rssi)

    // Normalize RSSI from -100..-30 to 0..1
    val normalizedSignal = ((network.rssi + 100f) / 70f).coerceIn(0f, 1f)

    // Animated pointer angle (180° sweep: -90 to +90)
    val targetAngle = -90f + (normalizedSignal * 180f)
    val pointerAngle = remember { Animatable(-90f) }

    LaunchedEffect(network.rssi) {
        pointerAngle.animateTo(
            targetValue = targetAngle,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Signal Meter - ${network.ssid}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Analog Gauge ──
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f - 16f
                    val strokeWidth = 20f

                    // Background arc (full gray)
                    drawArc(
                        color = Color(0xFFE0E0E0),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(
                            center.x - radius + strokeWidth / 2f,
                            center.y - radius + strokeWidth / 2f
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            (radius - strokeWidth / 2f) * 2f,
                            (radius - strokeWidth / 2f) * 2f
                        ),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Colored arc segments
                    val segments = listOf(
                        0f to 0.33f to Color(0xFFD50000),     // Red: weak
                        0.33f to 0.55f to Color(0xFFFF6D00),  // Orange: poor
                        0.55f to 0.70f to Color(0xFFFFD600),  // Yellow: fair
                        0.70f to 0.85f to Color(0xFF76FF03),  // Light green: good
                        0.85f to 1.0f to Color(0xFF00C853)    // Green: excellent
                    )

                    for ((range, color) in segments) {
                        val (start, end) = range
                        drawArc(
                            color = color,
                            startAngle = 135f + start * 270f,
                            sweepAngle = (end - start) * 270f,
                            useCenter = false,
                            topLeft = Offset(
                                center.x - radius + strokeWidth / 2f,
                                center.y - radius + strokeWidth / 2f
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                (radius - strokeWidth / 2f) * 2f,
                                (radius - strokeWidth / 2f) * 2f
                            ),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                    }

                    // Pointer (needle)
                    val angleRad = Math.toRadians(pointerAngle.value.toDouble())
                    val pointerLength = radius - strokeWidth - 4f
                    val endX = center.x + (pointerLength * cos(angleRad)).toFloat()
                    val endY = center.y + (pointerLength * sin(angleRad)).toFloat()

                    // Pointer line
                    drawLine(
                        color = Color(0xFF212121),
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )

                    // Center dot
                    drawCircle(
                        color = Color(0xFF212121),
                        radius = 8f,
                        center = center
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = center
                    )

                    // Tick marks and labels
                    val tickCount = 7
                    for (i in 0..tickCount) {
                        val tickAngle = 135f + (i.toFloat() / tickCount) * 270f
                        val tickRad = Math.toRadians(tickAngle.toDouble())
                        val isMain = i % 2 == 0

                        val outerR = radius - strokeWidth - 2f
                        val innerR = if (isMain) radius - strokeWidth - 12f else radius - strokeWidth - 8f
                        val outerX = center.x + (outerR * cos(tickRad)).toFloat()
                        val outerY = center.y + (outerR * sin(tickRad)).toFloat()
                        val innerX = center.x + (innerR * cos(tickRad)).toFloat()
                        val innerY = center.y + (innerR * sin(tickRad)).toFloat()

                        drawLine(
                            color = Color(0xFF757575),
                            start = Offset(innerX, innerY),
                            end = Offset(outerX, outerY),
                            strokeWidth = if (isMain) 2.5f else 1.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Center text: dBm value
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${network.rssi}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = qualityColor,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "dBm",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quality text and bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0..3) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(if (i == 0) 12.dp else (12 + i * 6).dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (i < level) qualityColor
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = qualityLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Network details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MeterInfoItem("Channel", "${frequencyToChannel(network.frequency)}")
                MeterInfoItem("Band", getBandLabel(network.frequency))
                MeterInfoItem("BSSID", network.bssid.take(17))
            }
        }
    }
}

@Composable
private fun MeterInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 1
        )
    }
}

// ──────────────────────────────────────────────────────────────────
//  Channel Graph Tab
// ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelGraphTab(
    channelData: Map<Int, Int>,
    bandFilter: BandFilter,
    onBandFilterChange: (BandFilter) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = "Phân bố Channel WiFi",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = "Số lượng mạng trên mỗi channel — tìm channel ít nhiễu nhất",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 16.dp, top = 2.dp, end = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Channel graph card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Band filter inside graph
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BandFilter.entries.forEach { band ->
                        FilterChip(
                            selected = bandFilter == band,
                            onClick = { onBandFilterChange(band) },
                            label = { Text(band.label, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (channelData.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Không có dữ liệu channel",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    // Bar Chart using Canvas
                    ChannelBarChart(channelData = channelData)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Find least congested channels
                    val leastCongested = channelData.minByOrNull { it.value }
                    Text(
                        text = if (leastCongested != null) {
                            "✨ Channel ít nhiễu nhất: CH ${leastCongested.key} (${leastCongested.value} mạng)"
                        } else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF00C853)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Channel details table
        Text(
            text = "Chi tiết Channel",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Channel list as cards
        if (channelData.isNotEmpty()) {
            val maxCount = channelData.values.maxOrNull() ?: 1
            channelData.entries.forEach { (channel, count) ->
                ChannelDetailRow(
                    channel = channel,
                    count = count,
                    maxCount = maxCount,
                    isLeastCongested = channel == channelData.minByOrNull { it.value }?.key
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ChannelBarChart(channelData: Map<Int, Int>) {
    val maxCount = (channelData.values.maxOrNull() ?: 1).coerceAtLeast(1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barSpacing = 4.dp.toPx()
            val barWidth = ((size.width - barSpacing * (channelData.size + 1)) / channelData.size)
                .coerceAtMost(32.dp.toPx())
            val chartHeight = size.height - 30.dp.toPx()

            val entries = channelData.entries.toList()

            entries.forEachIndexed { index, (channel, count) ->
                val barHeight = (count.toFloat() / maxCount) * chartHeight
                val x = barSpacing + index * (barWidth + barSpacing)
                val y = chartHeight - barHeight

                // Bar color based on congestion level
                val congestionRatio = count.toFloat() / maxCount
                val barColor = when {
                    congestionRatio <= 0.3f -> Color(0xFF00C853)
                    congestionRatio <= 0.6f -> Color(0xFFFFD600)
                    else -> Color(0xFFD50000)
                }

                // Draw bar
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Channel number below bar (rotate for tight spacing)
                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.rotate(-45f, x + barWidth / 2f, size.height - 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(
                    "$channel",
                    x + barWidth / 2f - 8.dp.toPx(),
                    size.height - 4.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 10.dp.toPx()
                        textAlign = android.graphics.Paint.Align.LEFT
                    }
                )
                drawContext.canvas.nativeCanvas.restore()

                // Count label on top
                drawContext.canvas.nativeCanvas.drawText(
                    "$count",
                    x + barWidth / 2f,
                    y - 4.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 11.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

@Composable
private fun ChannelDetailRow(
    channel: Int,
    count: Int,
    maxCount: Int,
    isLeastCongested: Boolean
) {
    val fraction = count.toFloat() / maxCount.coerceAtLeast(1)
    val barColor = when {
        fraction <= 0.3f -> Color(0xFF00C853)
        fraction <= 0.6f -> Color(0xFFFFD600)
        else -> Color(0xFFD50000)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLeastCongested)
                Color(0xFF00C853).copy(alpha = 0.05f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CH $channel",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.width(56.dp)
            )

            // Progress bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(6.dp))
                        .background(barColor)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$count mạng",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = barColor,
                modifier = Modifier.width(56.dp),
                textAlign = TextAlign.End
            )

            if (isLeastCongested) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "✨",
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────
//  Details Tab
// ──────────────────────────────────────────────────────────────────

@Composable
private fun DetailsTab(
    selectedNetwork: WiFiNetwork?,
    connectionInfo: ConnectionInfo?,
    networks: List<WiFiNetwork>
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Connected Network Detail ──
        connectionInfo?.let { conn ->
            Text(
                text = "Kết nối hiện tại",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("SSID", conn.ssid)
                    DetailRow("BSSID", conn.bssid)
                    DetailRow("Tốc độ liên kết", "${conn.linkSpeed} Mbps")
                    DetailRow("Tần số", "${conn.frequency} MHz (${getBandLabel(conn.frequency)})")
                    DetailRow("Channel", "${frequencyToChannel(conn.frequency)}")
                    DetailRow("Cường độ tín hiệu", "${conn.rssi} dBm")
                    DetailRow("Chất lượng", getSignalQuality(conn.rssi).first)
                    DetailRow("Địa chỉ IP", conn.ipAddress)
                }
            }
        }

        // ── Selected Network Detail ──
        selectedNetwork?.let { network ->
            Text(
                text = "Mạng đã chọn",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            val securityType = getSecurityType(network.capabilities)
            val bandLabel = getBandLabel(network.frequency)
            val channel = frequencyToChannel(network.frequency)
            val (qualityLabel, _) = getSignalQuality(network.rssi)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("SSID", network.ssid)
                    DetailRow("BSSID", network.bssid)
                    DetailRow("Cường độ tín hiệu", "${network.rssi} dBm")
                    DetailRow("Chất lượng", qualityLabel)
                    DetailRow("Tần số", "${network.frequency} MHz ($bandLabel)")
                    DetailRow("Channel", "$channel")
                    DetailRow("Bảo mật", securityType)
                    DetailRow("Đã kết nối", if (network.isConnected) "Có" else "Không")
                    if (network.capabilities.isNotEmpty()) {
                        DetailRow("Capabilities", network.capabilities)
                    }
                }
            }
        }

        // ── Network Statistics ──
        Text(
            text = "Thống kê",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val totalNetworks = networks.size
                val strongest = networks.maxByOrNull { it.rssi }
                val weakest = networks.minByOrNull { it.rssi }
                val avgRssi = if (networks.isNotEmpty()) {
                    networks.map { it.rssi }.average().toInt()
                } else 0

                val bands = networks.groupBy { getBandLabel(it.frequency) }

                DetailRow("Tổng số mạng", "$totalNetworks")
                DetailRow("Mạng mạnh nhất", strongest?.let { "${it.ssid} (${it.rssi} dBm)" } ?: "N/A")
                DetailRow("Mạng yếu nhất", weakest?.let { "${it.ssid} (${it.rssi} dBm)" } ?: "N/A")
                DetailRow("Tín hiệu trung bình", "$avgRssi dBm")
                bands.forEach { (band, nets) ->
                    DetailRow("Mạng $band", "${nets.size}")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
