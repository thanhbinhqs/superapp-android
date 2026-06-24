package com.superapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

data class AppCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val featureCount: Int,
    val route: String
)

val homeCategories = listOf(
    AppCategory(
        "tools", "Công Cụ", "7 tiện ích đa năng",
        Icons.Default.BuildCircle,
        listOf(Color(0xFF6C63FF), Color(0xFFE040FB)),
        7, "tools_gallery"
    )
)

// Recently used (stored in companion for persistence across nav)
object RecentTools {
    private val _recent = mutableListOf<String>()
    fun getAll() = _recent.toList()
    fun add(route: String) { _recent.remove(route); _recent.add(0, route); if (_recent.size > 4) _recent.removeAt(_recent.lastIndex) }
}

// Map route to feature info for recent display
data class RecentToolInfo(val title: String, val icon: ImageVector, val color: Color)
val recentToolMap = mapOf(
    "calculator" to RecentToolInfo("Máy Tính", Icons.Default.Calculate, Color(0xFF6C63FF)),
    "notes" to RecentToolInfo("Ghi Chú", Icons.Default.NoteAlt, Color(0xFFFF6B6B)),
    "todo" to RecentToolInfo("Công Việc", Icons.Default.Checklist, Color(0xFF4ECDC4)),
    "converter" to RecentToolInfo("Đổi Đơn Vị", Icons.Default.SwapHoriz, Color(0xFFFFA07A)),
    "qr" to RecentToolInfo("Mã QR", Icons.Default.QrCode, Color(0xFF7C4DFF)),
    "stopwatch" to RecentToolInfo("Bấm Giờ", Icons.Default.Timer, Color(0xFFFFD93D)),
    "flashlight" to RecentToolInfo("Đèn Pin", Icons.Default.FlashlightOn, Color(0xFF00BCD4))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val recent = remember { RecentTools.getAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Siêu Ứng Dụng", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("Chọn công cụ để bắt đầu", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // === Category cards ===
            Text("Danh mục", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            homeCategories.forEach { category ->
                CategoryCard(
                    category = category,
                    onClick = { navController.navigate(category.route) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // === Quick Access (recent) ===
            if (recent.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gần đây", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recent) { route ->
                        val info = recentToolMap[route]
                        if (info != null) {
                            RecentToolChip(
                                title = info.title,
                                icon = info.icon,
                                color = info.color,
                                onClick = { navController.navigate(route) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === Footer ===
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Chạm vào danh mục để xem tất cả công cụ",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CategoryCard(category: AppCategory, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = category.colors,
                        start = Offset(0f, 0f),
                        end = Offset(1200f, 800f)
                    )
                )
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        category.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${category.featureCount} tiện ích",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }

                // Feature icons row
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecentToolChip(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(110.dp)
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
