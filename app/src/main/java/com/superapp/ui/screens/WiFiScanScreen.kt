package com.superapp.ui.screens

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WiFiScanScreen(navController: NavController) {
    val context = LocalContext.current
    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }

    var scanResults by remember { mutableStateOf<List<WiFiNetwork>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedNetwork by remember { mutableStateOf<WiFiNetwork?>(null) }
    var bandFilter by remember { mutableStateOf(0) } // 0=All, 1=2.4, 2=5, 3=6
    var sortMode by remember { mutableStateOf(0) }   // 0=Signal, 1=Channel, 2=SSID
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(
        context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES
        else Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED) }

    val scanReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                isScanning = false
                val results = wifiManager.scanResults
                val connectedInfo = wifiManager.connectionInfo
                val connectedBssid = connectedInfo?.bssid ?: ""

                scanResults = results
                    .filter { it.SSID.isNotEmpty() || it.BSSID.isNotEmpty() }
                    .distinctBy { it.BSSID }
                    .sortedByDescending { it.level }
                    .map { scanResultToNetwork(it, connectedBssid) }
            }
        }
    }

    // Register receiver
    LaunchedEffect(Unit) {
        context.registerReceiver(
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            context.unregisterReceiver(scanReceiver)
        }
    }

    // Filter & sort
    val filteredNetworks = remember(scanResults, bandFilter, sortMode) {
        val bandFiltered = when (bandFilter) {
            1 -> scanResults.filter { it.frequency < 2500 }
            2 -> scanResults.filter { it.frequency in 5000..5850 }
            3 -> scanResults.filter { it.frequency > 5850 }
            else -> scanResults
        }
        when (sortMode) {
            1 -> bandFiltered.sortedBy { frequencyToChannel(it.frequency) }
            2 -> bandFiltered.sortedBy { it.ssid }
            else -> bandFiltered.sortedByDescending { it.rssi }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quét WiFi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (hasPermission) {
                            isScanning = true
                            wifiManager.startScan()
                        }
                    }) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan")
                        }
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
        ) {
            // Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Tất cả", "2.4GHz", "5GHz", "6GHz").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = bandFilter == idx,
                        onClick = { bandFilter = idx },
                        label = { Text(label, fontSize = 13.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Sort dropdown
                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf("Theo tín hiệu", "Theo kênh", "Theo tên").forEachIndexed { idx, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { sortMode = idx; showSortMenu = false },
                                trailingIcon = { if (sortMode == idx) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                }
            }

            // Network list
            if (filteredNetworks.isEmpty()) {
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
                            if (isScanning) "Đang quét..." else "Chưa có kết quả quét",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                        if (!isScanning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Nhấn nút Refresh để quét mạng WiFi",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "${filteredNetworks.size} mạng WiFi",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredNetworks, key = { it.bssid }) { network ->
                        ScanResultCard(
                            network = network,
                            isExpanded = selectedNetwork?.bssid == network.bssid,
                            onClick = {
                                selectedNetwork = if (selectedNetwork?.bssid == network.bssid) null else network
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScanResultCard(
    network: WiFiNetwork,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val (qualityLabel, qualityColor) = getSignalQuality(network.rssi)
    val channel = frequencyToChannel(network.frequency)
    val securityType = getSecurityType(network.capabilities)
    val (wifiGen, wifiGenColor) = getWifiGeneration(network.capabilities, network.frequency)
    val manufacturer = getManufacturer(network.bssid)
    val distance = estimateDistance(network.rssi, network.frequency)
    val distanceLabel = getDistanceLabel(distance)
    val band = getBandFromFrequency(network.frequency)

    val isDoubleBand = network.capabilities.contains("HT40") || network.capabilities.contains("HT20")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Signal bars
                val level = rssiToLevel(network.rssi)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (i in 0..3) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height((6 + i * 5).dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (i < level) qualityColor else Color(0xFF333333))
                            )
                        }
                    }
                    Text(
                        "${network.rssi}",
                        fontSize = 11.sp,
                        color = qualityColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "dBm",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // SSID + details
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = network.ssid,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (network.isConnected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Đã kết nối",
                                fontSize = 9.sp,
                                color = Color(0xFF00C853),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CH $channel",
                            fontSize = 12.sp,
                            color = Color(0xFF00BCD4),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${network.frequency}MHz",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$band GHz",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = qualityLabel,
                            fontSize = 11.sp,
                            color = qualityColor,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "~${distanceLabel}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (manufacturer != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = manufacturer,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Wi-Fi gen badge
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = wifiGenColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = wifiGen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            color = wifiGenColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF333333)
                    ) {
                        Text(
                            text = securityType,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }
            }

            // Expanded details
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow("BSSID", network.bssid)
                    DetailRow("Tần số", "${network.frequency} MHz")
                    DetailRow("Kênh", "CH $channel (${getBandFromFrequency(network.frequency)} GHz)")
                    DetailRow("Bảo mật", securityType)
                    DetailRow("Khoảng cách", distanceLabel)
                    if (manufacturer != null) DetailRow("Hãng", manufacturer)
                    DetailRow("Mức tín hiệu", "${network.rssi} dBm ($qualityLabel)")
                    DetailRow("Thời gian", java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(network.timestamp)))
                    DetailRow("Công nghệ", wifiGen)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
