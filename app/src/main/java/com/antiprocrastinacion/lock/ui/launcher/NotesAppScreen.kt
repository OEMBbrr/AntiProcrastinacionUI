package com.antiprocrastinacion.lock.ui.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiprocrastinacion.lock.LockManager
import com.antiprocrastinacion.lock.ZenNote
import java.text.SimpleDateFormat
import java.util.*

/**
 * V25: App Notas del nuevo launcher (pantalla completa).
 * UI propia, se instalará junto al launcher. Reutiliza el LockManager
 * para el guardado local + sincronización híbrida con la extensión.
 */
@Composable
fun NotesAppScreen(
    lockManager: LockManager,
    onBack: () -> Unit
) {
    var notesList by remember { mutableStateOf<List<ZenNote>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lockManager.observeNotes { updated ->
            notesList = updated
        }
    }

    val filtered = remember(notesList, query) {
        if (query.isBlank()) notesList
        else notesList.filter { it.content.contains(query, ignoreCase = true) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LchBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp)) // Dynamic Island

            // Cabecera
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = LchText
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Notas",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LchText
                    )
                    Text(
                        text = "Sincronizadas con tu extensión",
                        fontSize = 11.sp,
                        color = LchMuted
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = LchAccent,
                    contentColor = LchSurfaceHi,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva nota")
                }
            }

            // Buscador
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar nota...", fontSize = 13.sp, color = LchMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LchMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LchAccent,
                    unfocusedBorderColor = LchBorder,
                    focusedContainerColor = LchSurface,
                    unfocusedContainerColor = LchSurface,
                    cursorColor = LchAccent,
                    focusedTextColor = LchText,
                    unfocusedTextColor = LchText
                )
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "📝", fontSize = 34.sp)
                        Text(
                            text = if (notesList.isEmpty()) "No tienes notas todavía" else "Sin resultados",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LchText
                        )
                        Text(
                            text = "Toca + para escribir tu primera nota.\nSe sincronizará con el PC automáticamente.",
                            fontSize = 12.sp,
                            color = LchMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { note ->
                        LauncherNoteCard(
                            note = note,
                            onDelete = { lockManager.deleteNote(note.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddNoteDialog(
            onDismiss = { showAddDialog = false },
            onSave = { text, category ->
                lockManager.addNote(text, category)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun LauncherNoteCard(
    note: ZenNote,
    onDelete: () -> Unit
) {
    val dateStr = remember(note.timestamp) {
        if (note.timestamp == 0L) ""
        else SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(note.timestamp))
    }
    val categoryBadge = when (note.category) {
        "tarea" -> "Tarea"
        "idea" -> "Idea"
        "reflexion" -> "Reflexión"
        else -> "General"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LchSurface),
        border = BorderStroke(1.dp, LchBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = LchAccent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = categoryBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = LchAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = LchWarm,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = note.content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = LchText,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateStr,
                fontSize = 10.sp,
                color = LchMuted
            )
        }
    }
}

@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    val categories = listOf("general", "tarea", "idea", "reflexion")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LchSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Nueva nota",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = LchText
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Escribe tu idea, pensamiento o tarea...", fontSize = 13.sp, color = LchMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LchAccent,
                        unfocusedBorderColor = LchBorder,
                        focusedContainerColor = LchBg,
                        unfocusedContainerColor = LchBg,
                        cursorColor = LchAccent,
                        focusedTextColor = LchText,
                        unfocusedTextColor = LchText
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val selected = category == cat
                        Surface(
                            onClick = { category = cat },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) LchAccent else LchBg,
                            border = BorderStroke(1.dp, if (selected) LchAccent else LchBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (cat) {
                                        "tarea" -> "Tarea"
                                        "idea" -> "Idea"
                                        "reflexion" -> "Reflexión"
                                        else -> "General"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) LchSurfaceHi else LchText
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSave(text.trim(), category) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = LchAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = LchMuted)
            }
        }
    )
}
