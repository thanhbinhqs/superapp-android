package com.superapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

// ──────────────────────────────────────────────
// Data model – extended with isPinned and category
// ──────────────────────────────────────────────
data class Note(
    val id: Int,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val category: String = "default"
)

// ──────────────────────────────────────────────
// In-memory store – enhanced with pin & sort
// ──────────────────────────────────────────────
object NotesStore {
    private val _notes = mutableStateListOf<Note>()
    private var nextId = 1

    fun getAll(): List<Note> =
        _notes.sortedByDescending { it.isPinned }.let { sorted ->
            sorted.sortedByDescending { note ->
                if (note.isPinned) note.timestamp else 0L
            }.let { pinnedSorted ->
                val unpinned = pinnedSorted.filter { !it.isPinned }
                    .sortedByDescending { it.timestamp }
                pinnedSorted.filter { it.isPinned } + unpinned
            }
        }

    fun add(title: String, content: String, category: String = "default"): Note {
        val note = Note(
            id = nextId++,
            title = title.ifBlank { "Không có tiêu đề" },
            content = content,
            category = category
        )
        _notes.add(0, note)
        return note
    }

    fun update(id: Int, title: String, content: String, category: String? = null) {
        val idx = _notes.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _notes[idx] = _notes[idx].copy(
                title = title.ifBlank { "Không có tiêu đề" },
                content = content,
                timestamp = System.currentTimeMillis(),
                category = category ?: _notes[idx].category
            )
        }
    }

    fun delete(id: Int) {
        _notes.removeAll { it.id == id }
    }

    fun togglePin(id: Int) {
        val idx = _notes.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _notes[idx] = _notes[idx].copy(isPinned = !_notes[idx].isPinned)
        }
    }
}

// ──────────────────────────────────────────────
// Category colour palette
// ──────────────────────────────────────────────
val categoryColors = mapOf(
    "default"  to Color(0xFFFF6B6B),   // Coral
    "work"     to Color(0xFF4A90D9),   // Blue
    "personal" to Color(0xFF50C878),   // Emerald
    "idea"     to Color(0xFFFFD700),   // Gold
    "urgent"   to Color(0xFFFF4500),   // OrangeRed
)

fun colorForCategory(cat: String): Color =
    categoryColors[cat] ?: categoryColors["default"]!!

// ──────────────────────────────────────────────
// Main screen composable
// ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(navController: NavController) {
    var showDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    var noteCategory by remember { mutableStateOf("default") }
    var searchQuery by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(NotesStore.getAll()) }

    fun refresh() { notes = NotesStore.getAll() }

    fun openNewNote() {
        editingNote = null
        noteTitle = ""
        noteContent = ""
        noteCategory = "default"
        showDialog = true
    }

    fun openEditNote(note: Note) {
        editingNote = note
        noteTitle = note.title
        noteContent = note.content
        noteCategory = note.category
        showDialog = true
    }

    fun saveNote() {
        if (editingNote != null) {
            NotesStore.update(editingNote!!.id, noteTitle, noteContent, noteCategory)
        } else {
            NotesStore.add(noteTitle, noteContent, noteCategory)
        }
        refresh()
        showDialog = false
    }

    fun deleteNote(note: Note) {
        NotesStore.delete(note.id)
        refresh()
    }

    fun togglePinNote(note: Note) {
        NotesStore.togglePin(note.id)
        refresh()
    }

    // ── Filtered list ──
    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes
        else notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
        }
    }

    // ── Dialog (New / Edit) ──
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(if (editingNote != null) "Sửa Ghi Chú" else "Ghi Chú Mới")
            },
            text = {
                Column {
                    // Title
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Tiêu đề") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Content
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Nội dung") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        maxLines = 10
                    )

                    // Character count
                    Text(
                        text = "${noteContent.length} ký tự",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, end = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category picker
                    Text(
                        text = "Phân loại",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryColors.keys.forEach { cat ->
                            val isSelected = noteCategory == cat
                            val catColor = colorForCategory(cat)
                            FilterChip(
                                selected = isSelected,
                                onClick = { noteCategory = cat },
                                label = {
                                    Text(
                                        when (cat) {
                                            "default"  -> "Chung"
                                            "work"     -> "Công việc"
                                            "personal" -> "Cá nhân"
                                            "idea"     -> "Ý tưởng"
                                            "urgent"   -> "Gấp"
                                            else       -> cat
                                        },
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(catColor)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = catColor.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { saveNote() }) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Huỷ")
                }
            }
        )
    }

    // ── Main scaffold ──
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ghi Chú", fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openNewNote() },
                containerColor = Color(0xFFFF6B6B)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm ghi chú", tint = Color.White)
            }
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
                placeholder = { Text("Tìm kiếm ghi chú...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Xoá",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Content area ──
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (searchQuery.isNotEmpty()) Icons.Default.SearchOff
                                          else Icons.Default.NoteAdd,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Không tìm thấy ghi chú nào"
                                   else "Chưa có ghi chú nào",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Nhấn + để tạo ghi chú mới",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { openEditNote(note) },
                            onPin = { togglePinNote(note) },
                            onDelete = { deleteNote(note) }
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Note card composable
// ──────────────────────────────────────────────
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val accentColor = colorForCategory(note.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (note.isPinned) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 4.dp, top = 0.dp, bottom = 0.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Accent colour bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(14.dp))

            // Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 12.dp, bottom = 12.dp)
            ) {
                // Title row (pin icon + title)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Đã ghim",
                            modifier = Modifier.size(16.dp),
                            tint = accentColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = note.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Content preview
                Text(
                    text = note.content,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Timestamp + category label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormat.format(Date(note.timestamp)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (note.category) {
                            "default"  -> "Chung"
                            "work"     -> "Công việc"
                            "personal" -> "Cá nhân"
                            "idea"     -> "Ý tưởng"
                            "urgent"   -> "Gấp"
                            else       -> note.category
                        },
                        fontSize = 11.sp,
                        color = accentColor
                    )
                }
            }

            // Action buttons column
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onPin) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Default.PushPin
                                      else Icons.Default.PushPin,
                        contentDescription = if (note.isPinned) "Bỏ ghim" else "Ghim",
                        tint = if (note.isPinned) accentColor
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Xoá",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
