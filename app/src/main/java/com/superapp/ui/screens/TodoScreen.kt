package com.superapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

enum class Priority(val label: String, val color: Color) {
    CAO("Cao", Color(0xFFE74C3C)),
    VUA("Vừa", Color(0xFFF39C12)),
    THAP("Thấp", Color(0xFF2ECC71))
}

data class TodoItem(
    val id: Int,
    val text: String,
    val isDone: Boolean = false,
    val priority: Priority = Priority.VUA,
    val createdAt: Long = System.currentTimeMillis()
)

enum class FilterMode(val label: String) {
    ALL("Tất cả"),
    ACTIVE("Đang làm"),
    DONE("Đã xong")
}

object TodoStore {
    private val _items = mutableStateListOf<TodoItem>()
    private var nextId = 1

    fun getAll(): List<TodoItem> = _items.toList()
    fun getActiveCount(): Int = _items.count { !it.isDone }
    fun getDoneCount(): Int = _items.count { it.isDone }
    fun getByPriority(priority: Priority): Int = _items.count { it.priority == priority }
    fun getActiveByPriority(priority: Priority): Int = _items.count { !it.isDone && it.priority == priority }

    fun add(text: String, priority: Priority = Priority.VUA) {
        if (text.isNotBlank()) {
            _items.add(0, TodoItem(nextId++, text.trim(), priority = priority))
        }
    }

    fun toggle(id: Int) {
        val idx = _items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _items[idx] = _items[idx].copy(isDone = !_items[idx].isDone)
        }
    }

    fun delete(id: Int) {
        _items.removeAll { it.id == id }
    }

    fun deleteDone() {
        _items.removeAll { it.isDone }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(navController: NavController) {
    var inputText by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(Priority.VUA) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(FilterMode.ALL) }
    var showPriorityPicker by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(TodoStore.getAll()) }

    // Derived stats
    val totalCount = items.size
    val activeCount = items.count { !it.isDone }
    val doneCount = items.count { it.isDone }
    val caoCount = items.count { it.priority == Priority.CAO }
    val vuaCount = items.count { it.priority == Priority.VUA }
    val thapCount = items.count { it.priority == Priority.THAP }

    // Filtered + searched items
    val filteredItems = remember(items, filterMode, searchQuery) {
        items
            .filter { item ->
                when (filterMode) {
                    FilterMode.ALL -> true
                    FilterMode.ACTIVE -> !item.isDone
                    FilterMode.DONE -> item.isDone
                }
            }
            .filter { item ->
                searchQuery.isBlank() || item.text.contains(searchQuery, ignoreCase = true)
            }
    }

    fun refresh() {
        items = TodoStore.getAll()
    }

    fun addItem() {
        if (inputText.isNotBlank()) {
            TodoStore.add(inputText, selectedPriority)
            inputText = ""
            selectedPriority = Priority.VUA
            refresh()
        }
    }

    val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Công Việc", fontWeight = FontWeight.Bold)
                        if (totalCount > 0) {
                            Text(
                                "$activeCount việc cần làm · $doneCount hoàn thành",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    if (doneCount > 0) {
                        IconButton(onClick = {
                            TodoStore.deleteDone()
                            refresh()
                        }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Xoá đã hoàn thành",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
            // ── Search bar ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm kiếm công việc...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xoá",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4ECDC4),
                    cursorColor = Color(0xFF4ECDC4)
                )
            )

            // ── Filter tabs ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterMode.entries.forEach { mode ->
                    val isSelected = filterMode == mode
                    val chipCount = when (mode) {
                        FilterMode.ALL -> totalCount
                        FilterMode.ACTIVE -> activeCount
                        FilterMode.DONE -> doneCount
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { filterMode = mode },
                        label = { Text("${mode.label} ($chipCount)", fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4ECDC4).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF4ECDC4)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) Color(0xFF4ECDC4) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            selectedBorderColor = Color(0xFF4ECDC4)
                        )
                    )
                }
            }

            // ── Stats row ──
            if (totalCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Progress bar
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFF4ECDC4),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Stats breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Overall
                        Text(
                            "$doneCount / $totalCount hoàn thành",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        // Priority breakdown
                        Text(
                            "● Cao $caoCount  ● Vừa $vuaCount  ● Thấp $thapCount",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Priority mini legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Priority.entries.forEach { pri ->
                            val count = TodoStore.getActiveByPriority(pri)
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(pri.color)
                                )
                                Text(
                                    "$count ",
                                    fontSize = 11.sp,
                                    color = pri.color.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (filteredItems.isEmpty() && totalCount > 0) {
                            Text(
                                "Không có kết quả",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        } else if (filteredItems.isNotEmpty()) {
                            Text(
                                "Hiển thị ${filteredItems.size}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // ── Empty state ──
            if (totalCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Chưa có việc nào",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                        Text(
                            "Thêm việc cần làm phía dưới",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                    }
                }
            } else if (filteredItems.isEmpty()) {
                // Filtered empty
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "Không tìm thấy \"$searchQuery\""
                            else "Không có việc nào trong mục này",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                // ── Task list ──
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        TodoItemCard(
                            item = item,
                            onToggle = {
                                TodoStore.toggle(item.id)
                                refresh()
                            },
                            onDelete = {
                                TodoStore.delete(item.id)
                                refresh()
                            }
                        )
                    }
                }
            }

            // ── Input bar ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    // Priority selector (expandable)
                    if (showPriorityPicker) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ưu tiên:", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Priority.entries.forEach { pri ->
                                val isPriSelected = selectedPriority == pri
                                FilterChip(
                                    selected = isPriSelected,
                                    onClick = {
                                        selectedPriority = pri
                                        showPriorityPicker = false
                                    },
                                    label = { Text(pri.label, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(pri.color)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = pri.color.copy(alpha = 0.12f),
                                        selectedLabelColor = pri.color
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (isPriSelected) pri.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        selectedBorderColor = pri.color,
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Priority quick icon button
                        IconButton(
                            onClick = { showPriorityPicker = !showPriorityPicker }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(selectedPriority.color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Flag,
                                    contentDescription = "Chọn ưu tiên",
                                    tint = selectedPriority.color,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Nhập việc cần làm...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4ECDC4),
                                cursorColor = Color(0xFF4ECDC4)
                            )
                        )
                        FilledIconButton(
                            onClick = { addItem() },
                            enabled = inputText.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF4ECDC4)
                            )
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Thêm", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TodoItemCard(item: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDone)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isDone,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4ECDC4),
                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            )

            // Priority badge
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = item.priority.color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = item.priority.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = item.priority.color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = item.text,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.isDone)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Timestamp
            Text(
                text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(item.createdAt)),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.padding(end = 4.dp)
            )

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Xoá",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
