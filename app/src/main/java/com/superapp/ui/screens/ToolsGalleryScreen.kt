package com.superapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.superapp.data.models.Feature
import com.superapp.data.models.Features

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsGalleryScreen(navController: NavController) {
    val features = remember { Features.list }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Công Cụ", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("6 tiện ích đa năng", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(features) { feature ->
                FeatureCard(
                    feature = feature,
                    onClick = {
                        RecentTools.add(feature.route)
                        navController.navigate(feature.route)
                    }
                )
            }
        }
    }
}

@Composable
fun FeatureCard(
    feature: Feature,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = feature.colors,
                        start = Offset(0f, 0f),
                        end = Offset(800f, 800f)
                    )
                )
                .padding(18.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.title,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = feature.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = feature.description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
