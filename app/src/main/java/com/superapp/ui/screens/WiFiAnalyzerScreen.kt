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
import com.superapp.navigation.Routes
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

@Suppress("DEPRECATION")
enum class AnalyzerTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CHANNEL_GRAPH("Kênh", Icons.Default.BarChart),
    SPECTRUM("Phổ", Icons.Default.ShowChart),
    BEST_CHANNELS("Gợi ý", Icons.Default.Star),
    ACCESS_POINTS("AP", Icons.Default.TrackChanges),
    DETAILS("Chi Tiết", Icons.Default.Info)
}

// ── Channel Analysis Data ──

data class ChannelAnalysis(
    val channel: Int,
    val totalNetworks: Int,
    val congestionPercent: Int,
    val isBest: Boolean = false,
    val isConnectedHere: Boolean = false,
    val networks: List<WiFiNetwork> = emptyList()
)

data class BestChannelResult(
    val channel: Int,
    val score: Int, // 0-100
    val label: String,
    val description: String
)

// ── Manufacturer OUI database (common ones) ──
val OUI_MAP = mapOf(
    "08:40:f3" to "Tenda",
    "00:1a:6b" to "TP-Link",
    "00:1e:2a" to "TP-Link",
    "00:23:cd" to "TP-Link",
    "08:10:76" to "TP-Link",
    "10:f6:81" to "TP-Link",
    "2c:b0:5d" to "TP-Link",
    "34:e8:94" to "TP-Link",
    "50:c7:bf" to "TP-Link",
    "5c:9a:d8" to "TP-Link",
    "60:32:b1" to "TP-Link",
    "64:66:b3" to "TP-Link",
    "68:72:51" to "TP-Link",
    "70:3a:cb" to "TP-Link",
    "70:8b:cd" to "TP-Link",
    "7c:d1:c3" to "TP-Link",
    "84:3d:c6" to "TP-Link",
    "90:cc:24" to "TP-Link",
    "94:d9:b3" to "TP-Link",
    "a0:f3:c1" to "TP-Link",
    "b0:95:75" to "TP-Link",
    "b0:be:76" to "TP-Link",
    "c0:4a:00" to "TP-Link",
    "c8:3a:35" to "TP-Link",
    "d4:9a:20" to "TP-Link",
    "e0:cc:7a" to "TP-Link",
    "e8:de:27" to "TP-Link",
    "f4:ec:38" to "TP-Link",
    "00:0c:43" to "Linksys",
    "00:14:bf" to "Linksys",
    "00:1a:70" to "Linksys",
    "00:21:29" to "Linksys",
    "58:6d:8f" to "Linksys",
    "c0:56:e3" to "Linksys",
    "84:c9:b2" to "Xiaomi",
    "f4:d1:08" to "Xiaomi",
    "18:fe:34" to "Huawei",
    "24:69:68" to "Huawei",
    "38:59:f9" to "Huawei",
    "00:1b:10" to "Asus",
    "10:bf:48" to "Asus",
    "2c:4d:54" to "Asus",
    "74:d0:2b" to "Asus",
    "90:5c:44" to "Asus",
    "9c:5c:8e" to "Asus",
    "b0:6a:2a" to "Asus",
    "b0:c4:de" to "Asus",
    "d4:5d:64" to "Asus",
    "08:e6:89" to "Samsung",
    "f0:25:b7" to "Samsung",
    "00:03:7f" to "Intel",
    "00:15:00" to "Intel",
    "1c:69:7a" to "Intel",
    "3c:7c:3f" to "Intel",
    "b4:96:82" to "Intel",
    "f8:2f:7a" to "Intel",
    "00:1d:d0" to "Apple",
    "04:d4:c4" to "Apple",
    "34:36:3b" to "Apple",
    "8c:7b:9d" to "Apple",
    "e0:ac:cb" to "Apple",
    "f0:9e:9a" to "Apple",
    "00:17:f2" to "Cisco",
    "00:1a:a1" to "Cisco",
    "00:1d:a2" to "Cisco",
    "10:7b:44" to "Netgear",
    "2c:33:11" to "Netgear",
    "6c:b0:ce" to "Netgear",
    "78:d2:94" to "Netgear",
    "a0:14:3d" to "Netgear",
    "ac:22:0b" to "Netgear",
    "b0:48:7a" to "Netgear",
    "e0:91:53" to "Netgear",
    "f0:b4:29" to "Netgear",
    "74:ac:b9" to "D-Link",
    "78:54:2e" to "D-Link",
    "c0:3f:0e" to "D-Link",
    "28:10:7b" to "Google",
    "48:8e:e2" to "Google",
    "a0:e9:db" to "Google",
    "b4:45:06" to "Google",
)

// ── Helper functions ──

/**
 * Convert RSSI (dBm) to signal level 0-4 (0 = very weak, 4 = excellent)
 */
fun rssiToLevel(rssi: Int): Int {
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
fun frequencyToChannel(freq: Int): Int {
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
fun getBandLabel(freq: Int): String {
    return when {
        freq < 2500 -> "2.4 GHz"
        freq < 6000 -> "5 GHz"
        else -> "6 GHz"
    }
}

/**
 * Get security type from capabilities string
 */
fun getSecurityType(capabilities: String): String {
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
fun getSignalQuality(rssi: Int): Pair<String, Color> {
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
fun getDeviceIpAddress(): String {
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

/**
 * Get manufacturer from BSSID (first 3 octets = OUI)
 */
fun getManufacturer(bssid: String): String? {
    if (bssid.length < 8) return null
    val oui = bssid.trim().uppercase().take(8)
    // Handle both XX:XX:XX and xxxx:xxxx:xxxx formats
    val normalized = if (oui.contains(':')) oui else {
        oui.chunked(2).joinToString(":")
    }
    return OUI_MAP[normalized]
}

/**
 * Detect WiFi generation from capabilities string
 */
fun getWifiGeneration(capabilities: String): Pair<String, Color> {
    val cap = capabilities.uppercase()
    return when {
        cap.contains("WIFI6") || cap.contains("HE") || cap.contains("802.11AX") -> "WiFi 6" to Color(0xFF1565C0)
        cap.contains("WIFI5") || cap.contains("VHT") || cap.contains("802.11AC") -> "WiFi 5" to Color(0xFF00BCD4)
        cap.contains("WIFI4") || cap.contains("HT") || cap.contains("802.11N") -> "WiFi 4" to Color(0xFF4CAF50)
        cap.contains("802.11A") || cap.contains("802.11G") -> "WiFi 3" to Color(0xFFFF9800)
        else -> "Legacy" to Color(0xFF9E9E9E)
    }
}

/**
 * Estimate channel width from capabilities string (MHz)
 */
fun getChannelWidth(capabilities: String): Int {
    val cap = capabilities.uppercase()
    return when {
        cap.contains("VHT160") || cap.contains("160MHZ") -> 160
        cap.contains("VHT80") || cap.contains("80MHZ") -> 80
        cap.contains("VHT40") || cap.contains("40MHZ") || cap.contains("HT40") -> 40
        cap.contains("VHT20") || cap.contains("20MHZ") || cap.contains("HT20") -> 20
        else -> 20
    }
}

/**
 * Estimate distance from signal strength (dBm) using free-space path loss model
 * Returns approximate distance in meters
 */
fun estimateDistance(rssi: Int, freqMHz: Int): Float {
    // Using simplified FSPL model: distance = 10^((TxPower - RSSI - 20*log10(f) + 27.55) / 20)
    // Assume TxPower = 20 dBm for typical router
    val txPower = 20.0
    val freqGHz = freqMHz / 1000.0
    if (freqGHz <= 0) return 0f
    val pathLoss = txPower - rssi
    val distanceMeters = Math.pow(10.0, (pathLoss - 20 * Math.log10(freqGHz) - 27.55) / 20.0)
    return (distanceMeters).toFloat().coerceIn(0.1f, 100f)
}

/**
 * Get distance label in human-readable format
 */
fun getDistanceLabel(meters: Float): String {
    return when {
        meters < 1f -> "<1m"
        meters < 10f -> "${meters.toInt()}m"
        meters < 100f -> "${meters.toInt()}m"
        else -> ">100m"
    }
}

/**
 * Analyze all channels and return sorted analysis with recommendations
 */
private fun analyzeChannels(networks: List<WiFiNetwork>, connectedBssid: String): List<ChannelAnalysis> {
    if (networks.isEmpty()) return emptyList()

    // Group networks by channel
    val byChannel = networks.groupBy { frequencyToChannel(it.frequency) }

    val allChannels = byChannel.entries.map { (ch, nets) ->
        ChannelAnalysis(
            channel = ch,
            totalNetworks = nets.size,
            congestionPercent = minOf(100, (nets.size * 100) / maxOf(1, byChannel.size)),
            isConnectedHere = nets.any { it.bssid == connectedBssid },
            networks = nets
        )
    }

    return allChannels.sortedBy { it.channel }
}

/**
 * Get possible channels for a frequency band
 */
private fun getBandChannels(freqMHz: Int): List<Int> {
    return when {
        freqMHz < 2500 -> (1..13).toList()
        freqMHz < 6000 -> listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
        else -> (1..233).filter { it % 4 == 1 } // 6 GHz channels
    }
}

/**
 * Find the best channel recommendations
 */
private fun findBestChannels(
    networks: List<WiFiNetwork>,
    connectedBssid: String,
    preferredBand: String = "5"
): List<BestChannelResult> {
    if (networks.isEmpty()) return emptyList()

    val bandFiltered = if (preferredBand == "5") {
        networks.filter { it.frequency in 4900..6000 }
    } else if (preferredBand == "2.4") {
        networks.filter { it.frequency < 2500 }
    } else networks

    if (bandFiltered.isEmpty()) return emptyList()

    val bandChannels = getBandChannels(bandFiltered.firstOrNull()?.frequency ?: 5180)
    val activeNets = bandFiltered.groupBy { frequencyToChannel(it.frequency) }

    val results = bandChannels.map { ch ->
        val nets = activeNets[ch] ?: emptyList()
        val congestion = nets.size
        // Score: fewer networks = higher score
        // Also consider signal strength of connected network
        val connectedNet = nets.find { it.bssid == connectedBssid }
        val connectedBonus = if (connectedNet != null) 5 else 0
        val signalPenalty = if (connectedNet != null && connectedNet.rssi < -70) -10 else 0
        val rawScore = maxOf(0, 100 - (congestion * 15) + connectedBonus + signalPenalty)

        val (label, desc) = when {
            rawScore >= 85 -> "Tuyệt vời" to "Kênh sạch — không bị chồng lấn"
            rawScore >= 65 -> "Tốt" to "Ít nhiễu — khả dụng"
            rawScore >= 45 -> "Trung bình" to "Một số mạng chồng lấn"
            rawScore >= 25 -> "Đông đúc" to "Nhiều mạng chồng lấn"
            else -> "Quá tải" to "Rất nhiều mạng trên kênh này"
        }

        BestChannelResult(
            channel = ch,
            score = rawScore.coerceIn(0, 100),
            label = label,
            description = "$desc · ${nets.size} mạng gần đây"
        )
    }

    return results.sortedByDescending { it.score }
}

// ── Mock data for emulator ──

@Suppress("DEPRECATION")
private fun createMockScanResults(): List<ScanResult> {
    fun makeScanResult(ssid: String, bssid: String, level: Int, freq: Int, caps: String): ScanResult {
        val sr = ScanResult()
        sr.SSID = ssid
        sr.BSSID = bssid
        sr.level = level
        sr.frequency = freq
        sr.capabilities = caps
        sr.timestamp = System.currentTimeMillis() * 1000L
        return sr
    }

    return listOf(
        // BINH_5G AP1 — strong signal, channel 36
        makeScanResult("BINH_5G", "08:40:f3:fd:2b:d1", -52, 5180, "[WPA2-PSK-CCMP][WPA3-SAE][RSN-VHT-80]"),
        // HiddenSSID AP1 — ch36
        makeScanResult("", "08:40:f3:fd:2b:d0", -63, 5180, "[WPA2-PSK-CCMP][RSN-VHT-80]"),
        // BINH_5G AP2 — channel 44
        makeScanResult("BINH_5G", "08:40:f3:fd:35:c1", -65, 5220, "[WPA2-PSK-CCMP][RSN-VHT-80]"),
        // HiddenSSID AP2 — channel 48
        makeScanResult("", "08:40:f3:fd:35:c0", -81, 5240, "[WPA2-PSK-CCMP][RSN-VHT-80]"),
        // BINH_5G AP3 — weak signal, channel 52
        makeScanResult("BINH_5G", "08:40:f3:fd:36:21", -77, 5260, "[WPA2-PSK-CCMP][RSN-VHT-80]"),
        // Some 2.4GHz networks for variety
        makeScanResult("BINH_2G", "08:40:f3:fd:2b:d2", -45, 2437, "[WPA2-PSK-CCMP][RSN-HT-40]"),
        makeScanResult("Nha Ben Canh", "ac:84:c6:12:34:56", -72, 2412, "[WPA2-PSK-CCMP]"),
        makeScanResult("Viettel_5G_ABC", "0a:1b:2c:3d:4e:5f", -78, 5500, "[WPA2-PSK-CCMP][RSN-VHT-80]"),
    )
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
    var selectedTab by remember { mutableStateOf(AnalyzerTab.CHANNEL_GRAPH) }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
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
            val filteredResults = results
                .filter { it.SSID.isNotEmpty() }
                .distinctBy { it.BSSID }
                .sortedByDescending { it.level }

            // Use mock data ONLY on emulator when no real scan results
            val isEmulator = Build.BRAND?.lowercase()?.contains("generic") == true ||
                Build.FINGERPRINT?.lowercase()?.contains("generic") == true ||
                Build.PRODUCT?.lowercase()?.startsWith("sdk") == true ||
                Build.MODEL?.lowercase()?.startsWith("sdk") == true ||
                Build.MANUFACTURER?.lowercase()?.contains("google") == true && 
                Build.FINGERPRINT?.lowercase()?.contains("sdk") == true

            scanResults = if (filteredResults.isEmpty() && results.isEmpty() && isEmulator) {
                createMockScanResults()
            } else {
                filteredResults
            }

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
        val results = when (bandFilter) {
            BandFilter.ALL -> scanResults
            BandFilter.GHZ_2_4 -> scanResults.filter { it.frequency < 2500 }
            BandFilter.GHZ_5 -> scanResults.filter { it.frequency in 4900..6000 }
            BandFilter.GHZ_6 -> scanResults.filter { it.frequency >= 5955 }
        }
        @Suppress("DEPRECATION")
        results
            .filter { it.SSID.isNotEmpty() }
            .groupBy { frequencyToChannel(it.frequency) }
            .mapValues { it.value.size }
            .toSortedMap()
    }

    // ── Enhanced channel analysis ──
    val connectedBssid = connectionInfo?.bssid ?: ""
    val channelAnalysis = remember(networks, connectedBssid) {
        analyzeChannels(networks, connectedBssid)
    }
    val bestChannels = remember(networks, connectedBssid, bandFilter) {
        val band = when (bandFilter) {
            BandFilter.ALL -> "5"
            BandFilter.GHZ_2_4 -> "2.4"
            BandFilter.GHZ_5 -> "5"
            BandFilter.GHZ_6 -> "6"
        }
        findBestChannels(networks, connectedBssid, band)
    }

    // ── Networks for spectrum view (with signal data) ──
    val spectrumNetworks = remember(scanResults, bandFilter) {
        val results = when (bandFilter) {
            BandFilter.ALL -> scanResults
            BandFilter.GHZ_2_4 -> scanResults.filter { it.frequency < 2500 }
            BandFilter.GHZ_5 -> scanResults.filter { it.frequency in 4900..6000 }
            BandFilter.GHZ_6 -> scanResults.filter { it.frequency >= 5955 }
        }
        @Suppress("DEPRECATION")
        results.filter { it.SSID.isNotEmpty() }.distinctBy { it.BSSID }
    }

    // ── Tab content based on selected tab ──
    val tabContent: @Composable () -> Unit = {
        when (selectedTab) {
            AnalyzerTab.CHANNEL_GRAPH -> {
                ChannelGraphTab(
                    channelData = channelData,
                    networks = spectrumNetworks,
                    connectionInfo = connectionInfo,
                    bandFilter = bandFilter,
                    onBandFilterChange = { bandFilter = it }
                )
            }
            AnalyzerTab.SPECTRUM -> {
                SpectrumTab(
                    networks = spectrumNetworks,
                    bandFilter = bandFilter,
                    onBandFilterChange = { bandFilter = it }
                )
            }
            AnalyzerTab.BEST_CHANNELS -> {
                BestChannelsTab(
                    bestChannels = bestChannels,
                    channelAnalysis = channelAnalysis,
                    connectionInfo = connectionInfo,
                    bandFilter = bandFilter,
                    onBandFilterChange = { bandFilter = it }
                )
            }
            AnalyzerTab.ACCESS_POINTS -> {
                AccessPointsTab(
                    networks = networks,
                    selectedNetwork = selectedNetwork,
                    onNetworkClick = { network ->
                        selectedNetwork = if (selectedNetwork?.bssid == network.bssid) {
                            null
                        } else network
                    },
                    navController = navController
                )
            }
            AnalyzerTab.DETAILS -> {
                DetailsTab(
                    selectedNetwork = selectedNetwork,
                    connectionInfo = connectionInfo,
                    networks = networks,
                    navController = navController
                )
            }
        }
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
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
                                    )
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                    )
                                }
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

            // ── Tab Row (5 tabs with icons) ──
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                AnalyzerTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selectedTab == tab) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        text = {
                            Text(
                                tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ── Tab Content ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                tabContent()
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
//  Spectrum Tab — parabolic signal curves over channel grid
// ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpectrumTab(
    networks: List<android.net.wifi.ScanResult>,
    bandFilter: BandFilter,
    onBandFilterChange: (BandFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Band selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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

        Spacer(modifier = Modifier.height(8.dp))

        // Spectrum Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Phổ Tín Hiệu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Cường độ tín hiệu theo kênh (dBm)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Spectrum canvas with parabolic curves
                if (networks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Không có dữ liệu",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    SpectrumCanvas(networks = networks)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend
        if (networks.isNotEmpty()) {
            Text(
                text = "Mạng phát hiện",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            networks.take(8).forEach { result ->
                @Suppress("DEPRECATION")
                SpectrumLegendItem(result = result)
            }
            if (networks.size > 8) {
                Text(
                    text = "+${networks.size - 8} mạng khác",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SpectrumCanvas(networks: List<android.net.wifi.ScanResult>) {
    val maxSignal = networks.maxOfOrNull { it.level.toFloat() } ?: -30f
    val minSignal = networks.minOfOrNull { it.level.toFloat() } ?: -90f
    val signalRange = maxOf(1f, maxSignal - minSignal)

    // Get unique channels in sorted order
    val channels = networks.map { frequencyToChannel(it.frequency) }.distinct().sorted()
    val minCh = channels.firstOrNull() ?: 1
    val maxCh = channels.lastOrNull() ?: 165
    val chRange = maxOf(1, maxCh - minCh)

    // Colors for curves
    val colors = listOf(
        Color(0xFF00BCD4), Color(0xFFFF6D00), Color(0xFF7C4DFF),
        Color(0xFF4CAF50), Color(0xFFE91E63), Color(0xFFFFD600),
        Color(0xFF2196F3), Color(0xFFFF5722)
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        val chartPadding = 48.dp.toPx()
        val chartWidth = size.width - chartPadding - 16.dp.toPx()
        val chartHeight = size.height - 50.dp.toPx()
        val topPadding = 16.dp.toPx()

        // ── Draw grid lines ──
        for (i in 0..4) {
            val y = topPadding + (chartHeight * i / 4f)
            drawLine(
                color = Color(0xFF333333),
                start = Offset(chartPadding, y),
                end = Offset(size.width - 8.dp.toPx(), y),
                strokeWidth = 1f
            )
            // dBm labels
            val dbm = (minSignal + signalRange * (4 - i) / 4f).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                "$dbm",
                4.dp.toPx(),
                y + 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 9.dp.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }

        // ── Draw channel labels on X-axis ──
        channels.forEach { ch ->
            val x = chartPadding + ((ch - minCh).toFloat() / chRange) * chartWidth
            drawContext.canvas.nativeCanvas.drawText(
                "$ch",
                x,
                size.height - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 9.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // ── Draw parabolic curves for each network ──
        val grouped = networks.groupBy { frequencyToChannel(it.frequency) }
        var colorIndex = 0

        grouped.forEach { (ch, results) ->
            val x = chartPadding + ((ch - minCh).toFloat() / chRange) * chartWidth
            val curveColor = colors[colorIndex % colors.size]
            colorIndex++

            results.forEach { result ->
                val normalizedRssi = ((result.level.toFloat() - minSignal) / signalRange)
                    .coerceIn(0.05f, 1f)
                val peakY = topPadding + chartHeight * (1f - normalizedRssi)

                // Draw gaussian-like curve
                val path = androidx.compose.ui.graphics.Path()
                val curveWidth = chartWidth * 0.15f
                val steps = 40

                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val dx = (t - 0.5f) * 2f * curveWidth
                    val curveX = x + dx
                    if (curveX < chartPadding || curveX > size.width - 8.dp.toPx()) continue

                    // Gaussian: y = peak * exp(-dx² / (2*σ²))
                    val sigma = curveWidth * 0.35f
                    val gaussianY = normalizedRssi * kotlin.math.exp(-(dx * dx) / (2f * sigma * sigma))
                    val curveY = topPadding + chartHeight * (1f - gaussianY)

                    if (i == 0) path.moveTo(curveX, curveY)
                    else path.lineTo(curveX, curveY)
                }

                drawPath(
                    path = path,
                    color = curveColor.copy(alpha = 0.6f),
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )

                // Draw peak dot
                drawCircle(
                    color = curveColor,
                    radius = 3f,
                    center = Offset(x, peakY)
                )
            }
        }
    }
}

@Composable
private fun SpectrumLegendItem(result: android.net.wifi.ScanResult) {
    @Suppress("DEPRECATION")
    val ssid = result.SSID.ifEmpty { "(HiddenSSID)" }
    val channel = frequencyToChannel(result.frequency)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal strength bar
            val level = rssiToLevel(result.level)
            val color = when {
                level >= 3 -> Color(0xFF00C853)
                level >= 2 -> Color(0xFFFFD600)
                else -> Color(0xFFD50000)
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(ssid, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${result.level} dBm", fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1565C0).copy(alpha = 0.12f)) {
                Text(
                    "CH $channel",
                    fontSize = 10.sp, fontWeight = FontWeight.Medium,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────
//  Best Channels Tab — recommendations & channel analysis
// ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BestChannelsTab(
    bestChannels: List<BestChannelResult>,
    @Suppress("UNUSED_PARAMETER") channelAnalysis: List<ChannelAnalysis>,
    connectionInfo: ConnectionInfo?,
    bandFilter: BandFilter,
    onBandFilterChange: (BandFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Band selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        if (bestChannels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Không có dữ liệu quét", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
            return@Column
        }

        // ── Best Channel Recommendation Card ──
        val topChannel = bestChannels.firstOrNull()
        if (topChannel != null) {
            val scoreColor = when {
                topChannel.score >= 80 -> Color(0xFF7C4DFF)
                topChannel.score >= 60 -> Color(0xFF00BCD4)
                topChannel.score >= 40 -> Color(0xFFFFD600)
                else -> Color(0xFFD50000)
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = scoreColor.copy(alpha = 0.1f)
                ),
                border = BorderStroke(1.5.dp, scoreColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Score circle
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { topChannel.score / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = scoreColor,
                            strokeWidth = 4.dp,
                            trackColor = scoreColor.copy(alpha = 0.2f)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${topChannel.score}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = scoreColor
                            )
                            Text(
                                "%",
                                fontSize = 10.sp,
                                color = scoreColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "KÊNH TỐT NHẤT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = scoreColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Channel ${topChannel.channel}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = scoreColor
                        )
                        Text(
                            "${topChannel.label} · ${topChannel.score}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scoreColor.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            topChannel.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Current Connection Card ──
            connectionInfo?.let { conn ->
                val connectedChannel = frequencyToChannel(conn.frequency)
                val connectedScore = bestChannels.find { it.channel == connectedChannel }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF00C853).copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "BẠN ĐANG KẾT NỐI Ở ĐÂY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFF00C853)
                            )
                            Text(
                                conn.ssid,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Channel $connectedChannel · Tín hiệu ${conn.rssi} dBm",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        // Score for current channel
                        if (connectedScore != null) {
                            val switchText = if (topChannel.channel != connectedChannel) {
                                "Chuyển lên CH ${topChannel.channel} có thể cải thiện nhiễu."
                            } else ""
                            if (switchText.isNotEmpty()) {
                                Text(
                                    switchText,
                                    fontSize = 10.sp,
                                    color = Color(0xFFFF6D00),
                                    modifier = Modifier.width(120.dp),
                                    textAlign = TextAlign.End,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── ALL Channels List ──
        Text(
            text = "TẤT CẢ KÊNH",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))

        bestChannels.take(10).forEach { channel ->
            val isBest = channel == topChannel
            val isConnected = connectionInfo?.let {
                frequencyToChannel(it.frequency) == channel.channel
            } ?: false

            val scoreColor = when {
                channel.score >= 80 -> Color(0xFF7C4DFF)
                channel.score >= 60 -> Color(0xFF00BCD4)
                channel.score >= 40 -> Color(0xFFFFD600)
                else -> Color(0xFFD50000)
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isBest -> scoreColor.copy(alpha = 0.05f)
                        isConnected -> Color(0xFF00C853).copy(alpha = 0.05f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = if (isBest) BorderStroke(1.dp, scoreColor.copy(alpha = 0.3f)) else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Channel number
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                        Text(
                            "CH ${channel.channel}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isBest) scoreColor else MaterialTheme.colorScheme.onSurface
                        )
                        if (isBest) {
                            Text(
                                "★ Tốt nhất",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor
                            )
                        }
                        if (isConnected && !isBest) {
                            Text(
                                "Đã kết nối",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00C853)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Score bar
                    Box(
                        modifier = Modifier.weight(1f).height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight()
                                .fillMaxWidth(channel.score / 100f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(scoreColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Score percentage
                    Text(
                        "${channel.score}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scoreColor,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Label
                    Text(
                        channel.label,
                        fontSize = 11.sp,
                        color = scoreColor,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ──────────────────────────────────────────────────────────────────
//  Access Points Tab — detailed AP list with signal gauges inline
// ──────────────────────────────────────────────────────────────────

@Composable
private fun AccessPointsTab(
    networks: List<WiFiNetwork>,
    selectedNetwork: WiFiNetwork?,
    onNetworkClick: (WiFiNetwork) -> Unit,
    navController: NavController
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header with scan button ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${networks.size} mạng WiFi",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
            FilledTonalButton(
                onClick = { navController.navigate(Routes.WIFI_SCAN) },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Quét WiFi", fontSize = 13.sp)
            }
        }

        if (networks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                        "Không tìm thấy mạng WiFi",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(networks, key = { it.bssid + it.ssid }) { network ->
                    AccessPointCard(
                        network = network,
                        isSelected = selectedNetwork?.bssid == network.bssid,
                        onClick = { onNetworkClick(network) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessPointCard(
    network: WiFiNetwork,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (qualityLabel, qualityColor) = getSignalQuality(network.rssi)
    val level = rssiToLevel(network.rssi)
    val securityType = getSecurityType(network.capabilities)
    val channel = frequencyToChannel(network.frequency)
    val (wifiGen, wifiGenColor) = getWifiGeneration(network.capabilities)
    val channelWidth = getChannelWidth(network.capabilities)
    val distance = estimateDistance(network.rssi, network.frequency)
    val distanceLabel = getDistanceLabel(distance)
    val manufacturer = getManufacturer(network.bssid)

    // Normalized signal for gauge
    val normalizedSignal = ((network.rssi + 100f) / 70f).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Signal Gauge (mini circular) ──
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f - 4f
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
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )

                    // Signal arc
                    val signalSweep = sweep * normalizedSignal
                    val signalColor = when {
                        normalizedSignal >= 0.7f -> Color(0xFF00C853)
                        normalizedSignal >= 0.4f -> Color(0xFFFFD600)
                        else -> Color(0xFFD50000)
                    }
                    drawArc(
                        color = signalColor,
                        startAngle = startAngle,
                        sweepAngle = signalSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )

                    // Needle
                    val needleAngle = startAngle + signalSweep
                    val needleRad = Math.toRadians(needleAngle.toDouble())
                    val needleLen = radius - 2f
                    drawLine(
                        color = signalColor,
                        start = center,
                        end = Offset(
                            center.x + (needleLen * cos(needleRad)).toFloat(),
                            center.y + (needleLen * sin(needleRad)).toFloat()
                        ),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }

                // Center text (dBm)
                Text(
                    text = "${network.rssi}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // ── Info section ──
            Column(modifier = Modifier.weight(1f)) {
                // SSID row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ssidColor = if (network.ssid.startsWith("(") || network.ssid.isEmpty())
                        Color(0xFFFF6D00) else MaterialTheme.colorScheme.onSurface
                    Text(
                        text = network.ssid.ifEmpty { "(HiddenSSID)" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = ssidColor,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Details row: MAC · distance · channel
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = network.bssid.take(17),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = " · $distanceLabel",
                        fontSize = 10.sp,
                        color = Color(0xFF00BCD4),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Badges row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // WiFi Generation badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = wifiGenColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = wifiGen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = wifiGenColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Channel badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF00BCD4).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${network.frequency}MHz (${channelWidth}MHz) · CH $channel",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF00BCD4),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Security badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1565C0).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = securityType,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1565C0),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Manufacturer badge
                    if (manufacturer != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF6D00).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "[$manufacturer]",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF6D00),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Quality and speed row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = qualityLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = qualityColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Signal bars
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (i in 0..3) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(if (i == 0) 6.dp else (6 + i * 3).dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(
                                        if (i < level) qualityColor
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelGraphTab(
    channelData: Map<Int, Int>,
    networks: List<android.net.wifi.ScanResult>,
    connectionInfo: ConnectionInfo?,
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

        // Spectrum Card at top — show all networks as parabolic curves
        if (networks.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Phổ Tín Hiệu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Cường độ tín hiệu theo kênh (dBm)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ChannelSpectrumCanvas(networks = networks)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Text(
                text = "Mạng phát hiện",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            networks.take(8).forEach { result ->
                ChannelLegendItem(result = result)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Channel distribution card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Phân bố Channel WiFi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Số lượng mạng trên mỗi channel",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.height(12.dp))

                if (channelData.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
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

// ── Spectrum Canvas for Channel Graph ──

@Composable
private fun ChannelSpectrumCanvas(networks: List<android.net.wifi.ScanResult>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val w = size.width
        val h = size.height
        val paddingLeft = 40.dp.toPx()
        val paddingRight = 12.dp.toPx()
        val paddingTop = 12.dp.toPx()
        val paddingBottom = 30.dp.toPx()
        val chartW = w - paddingLeft - paddingRight
        val chartH = h - paddingTop - paddingBottom

        // Determine frequency range
        val freqs = networks.map { it.frequency }.filter { it > 0 }
        if (freqs.isEmpty()) return@Canvas
        val minFreq = (freqs.min() - 20).coerceAtLeast(2400)
        val maxFreq = (freqs.max() + 20).coerceAtMost(6000)
        val freqRange = (maxFreq - minFreq).coerceAtLeast(1)

        // Draw grid lines
        val gridColor = Color(0xFF333333)
        val steps = 6
        for (i in 0..steps) {
            val y = paddingTop + chartH * i / steps
            drawLine(gridColor, Offset(paddingLeft, y), Offset(w - paddingRight, y), strokeWidth = 1f)
            val dbm = -20 - (i * 70 / steps)
            drawContext.canvas.nativeCanvas.drawText(
                "$dbm", 2.dp.toPx(), y + 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 9.dp.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }

        // Draw channel labels on X axis
        val channels = networks.map { frequencyToChannel(it.frequency) }.distinct().sorted()
        channels.forEach { ch ->
            val freq = chToApproxFreq(ch)
            val x = paddingLeft + (freq - minFreq).toFloat() / freqRange * chartW
            drawContext.canvas.nativeCanvas.drawText(
                "$ch", x - 8.dp.toPx(), h - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 9.dp.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }

        // Draw parabolic curves for each network
        val colors = listOf(
            Color(0xFFFF5252), Color(0xFFFF4081), Color(0xFFE040FB),
            Color(0xFF7C4DFF), Color(0xFF448AFF), Color(0xFF00BCD4),
            Color(0xFF00E676), Color(0xFFFFD740), Color(0xFFFF6E40),
            Color(0xFFA1887F)
        )

        networks.forEachIndexed { idx, network ->
            if (network.frequency <= 0) return@forEachIndexed
            val col = colors[idx % colors.size]
            val centerX = paddingLeft + (network.frequency - minFreq).toFloat() / freqRange * chartW
            val normLevel = ((network.level.coerceIn(-90, -20) + 90).toFloat() / 70f).coerceIn(0.05f, 1f)
            val curveHeight = chartH * normLevel * 0.9f
            val curveWidth = chartW * 0.04f

            val path = Path()
            for (i in -20..20) {
                val t = i / 20f
                val px = centerX + t * curveWidth * 4
                val py = paddingTop + chartH - curveHeight * exp(-t * t * 3f)
                if (i == -20) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, col.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private fun chToApproxFreq(channel: Int): Int {
    return when {
        channel in 1..13 -> 2412 + (channel - 1) * 5
        channel == 14 -> 2484
        channel in 36..64 -> 5180 + (channel - 36) * 5
        channel in 100..144 -> 5500 + (channel - 100) * 5
        channel in 149..165 -> 5745 + (channel - 149) * 5
        else -> 5180
    }
}

@Composable
private fun ChannelLegendItem(result: android.net.wifi.ScanResult) {
    val ssid = result.SSID.ifEmpty { "(HiddenSSID)" }
    val channel = frequencyToChannel(result.frequency)
    val dist = rssiToDistance(result.level)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal strength indicator
            val signalColor = when {
                result.level >= -50 -> Color(0xFF00C853)
                result.level >= -70 -> Color(0xFFFFD600)
                else -> Color(0xFFD50000)
            }
            val signalWidth = ((result.level.coerceIn(-90, -20) + 90) / 70f * 40 + 10).dp
            Box(
                modifier = Modifier
                    .width(signalWidth)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(signalColor.copy(alpha = 0.7f))
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = ssid,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${result.level} dBm",
                fontSize = 12.sp,
                color = signalColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "~${dist}m",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CH $channel",
                fontSize = 11.sp,
                color = Color(0xFF00BCD4)
            )
        }
    }
}

fun rssiToDistance(rssi: Int): Int {
    // Rough distance estimation
    val txPower = -40 // typical TX power in dBm
    val ratio = txPower.toDouble() - rssi
    val distance = Math.pow(10.0, ratio / 20.0)
    return distance.roundToInt().coerceIn(1, 100)
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
    networks: List<WiFiNetwork>,
    navController: NavController
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { navController.navigate(Routes.WIFI_DETAIL) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xem chi tiết đầy đủ", fontSize = 14.sp)
                    }
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

// ──────────────────────────────────────────────────────────────────
//  WiFi Utility Functions (for screens in the same package)
// ──────────────────────────────────────────────────────────────────

/**
 * Get network interface utilities
 */
fun getDeviceMacAddress(): String? {
    return try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.name == "wlan0" || networkInterface.name.startsWith("eth")) {
                val mac = networkInterface.hardwareAddress ?: return null
                return mac.joinToString(":") { "%02X".format(it) }
            }
        }
        val interfaces2 = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces2.hasMoreElements()) {
            val ni = interfaces2.nextElement()
            if (!ni.isLoopback && !ni.isVirtual && ni.isUp) {
                val mac = ni.hardwareAddress ?: continue
                return mac.joinToString(":") { "%02X".format(it) }
            }
        }
        null
    } catch (_: Exception) { null }
}

fun getGatewayAddress(): String {
    return try {
        val cmd = java.lang.Runtime.getRuntime().exec("ip route")
        val reader = java.io.BufferedReader(java.io.InputStreamReader(cmd.inputStream))
        val line = reader.readLine()
        val parts = line?.split(" ") ?: return "N/A"
        if (parts.size >= 3) parts[2] else "N/A"
    } catch (_: Exception) { "N/A" }
}

fun getDnsServers(): List<String> {
    val dnsList = mutableListOf<String>()
    try {
        val cmd = java.lang.Runtime.getRuntime().exec("getprop")
        val reader = java.io.BufferedReader(java.io.InputStreamReader(cmd.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (l.contains("net.dns") || l.contains("dhcp.*.dns")) {
                val parts = l.split(": ")
                if (parts.size >= 2) {
                    val value = parts[1].trim('[', ']', ' ')
                    if (value.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                        dnsList.add(value)
                    }
                }
            }
        }
    } catch (_: Exception) { }
    return dnsList.distinct()
}

@Suppress("DEPRECATION")
fun android.net.wifi.WifiInfo.toConnectionInfo(): ConnectionInfo {
    val ssidStr = this.ssid?.removeSurrounding("\"") ?: "<unknown ssid>"
    val bssidStr = this.bssid ?: "N/A"
    val ipLong = this.ipAddress.toLong()
    val ipStr = try {
        java.net.InetAddress.getByAddress(
            byteArrayOf(
                (ipLong and 0xFF).toByte(),
                ((ipLong shr 8) and 0xFF).toByte(),
                ((ipLong shr 16) and 0xFF).toByte(),
                ((ipLong shr 24) and 0xFF).toByte()
            )
        ).hostAddress ?: "N/A"
    } catch (_: Exception) { "N/A" }

    return ConnectionInfo(
        ssid = ssidStr,
        bssid = bssidStr,
        rssi = this.rssi,
        linkSpeed = this.linkSpeed,
        frequency = this.frequency,
        ipAddress = ipStr
    )
}

/**
 * Convert ScanResult to WiFiNetwork data class
 */
fun scanResultToNetwork(result: android.net.wifi.ScanResult, connectedBssid: String = ""): WiFiNetwork {
    val ssid = result.SSID.ifEmpty { "(Hidden SSID)" }
    val isConnected = result.BSSID.equals(connectedBssid, ignoreCase = true)
    return WiFiNetwork(
        ssid = ssid,
        bssid = result.BSSID,
        rssi = result.level,
        frequency = result.frequency,
        capabilities = result.capabilities,
        timestamp = result.timestamp,
        isConnected = isConnected
    )
}

/**
 * Get band label (short form like "5 GHz") from frequency
 */
fun getBandFromFrequency(freq: Int): String {
    return when {
        freq < 2500 -> "2.4"
        freq in 4900..6000 -> "5"
        freq >= 5955 -> "6"
        else -> "?"
    }
}

/**
 * WiFi generation detection with frequency context
 */
fun getWifiGeneration(capabilities: String, freqMHz: Int): Pair<String, Color> {
    val cap = capabilities.uppercase()
    val freqBand = freqMHz
    return when {
        cap.contains("WIFI6") || cap.contains("AX") || cap.contains("HE") -> "WiFi 6" to Color(0xFF2196F3)
        cap.contains("WIFI7") || cap.contains("BE") || cap.contains("EHT") -> "WiFi 7" to Color(0xFF9C27B0)
        cap.contains("AC") || cap.contains("VHT") || freqBand > 5000 -> "WiFi 5" to Color(0xFF4CAF50)
        cap.contains("N") || cap.contains("HT") -> "WiFi 4" to Color(0xFFFF9800)
        cap.contains("G") || cap.contains("OFDM") -> "WiFi 3" to Color(0xFF795548)
        else -> "WiFi ?" to Color(0xFF9E9E9E)
    }
}

