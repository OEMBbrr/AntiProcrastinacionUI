package com.antiprocrastinacion.lock.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.antiprocrastinacion.lock.LockManager
import com.antiprocrastinacion.lock.ZenNote
import com.antiprocrastinacion.lock.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZenNotesModal(
    lockManager: LockManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var fontSizeSp by remember { mutableIntStateOf(14) } // Control de tamaño de texto (12-24sp)
    var notesList by remember { mutableStateOf<List<ZenNote>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("Todas") }
    var newNoteCategory by remember { mutableStateOf("general") }
    var isAdding by remember { mutableStateOf(false) }
    // V24.1: claves CANÓNICAS compartidas con la extensión (minúsculas, sin acentos)
    val categories = listOf("Todas", "general", "tarea", "idea", "reflexion")
    val noteCreationCategories = listOf("general", "tarea", "idea", "reflexion")

    LaunchedEffect(Unit) {
        lockManager.observeNotes { updated ->
            notesList = updated
        }
    }

    val filteredNotes = remember(notesList, searchQuery, selectedCategory) {
        notesList.filter { note ->
            val matchesCategory = if (selectedCategory == "Todas") true else note.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() || note.content.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(2.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ZenWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cabecera Principal Workspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "📝", fontSize = 22.sp)
                        Column {
                            Text(
                                text = "Mis Notas",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZenCharcoal
                            )
                            Text(
                                text = "Sincronización Híbrida Instantánea",
                                fontSize = 10.sp,
                                color = ZenSage
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = ZenSage)
                    }
                }

                // Control de Agrandar / Reducir Tamaño de Texto (Paleta Zen)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CreamBackground, RoundedCornerShape(12.dp))
                        .border(1.dp, ZenSage.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tamaño de Texto:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenOlive
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { if (fontSizeSp > 12) fontSizeSp-- },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ZenSage.copy(alpha = 0.2f)),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("A-", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ZenCharcoal)
                        }
                        Text(
                            text = "${fontSizeSp}sp",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ZenCharcoal
                        )
                        Button(
                            onClick = { if (fontSizeSp < 24) fontSizeSp++ },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ZenSage.copy(alpha = 0.2f)),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("A+", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ZenCharcoal)
                        }
                    }
                }

                // Campo para añadir nueva nota (multilínea + categoría)
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Escribe aquí tu idea, pensamiento o tarea...", fontSize = 13.sp, color = ZenSage) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZenOlive,
                            unfocusedBorderColor = ZenSage.copy(alpha = 0.4f),
                            focusedContainerColor = CreamBackground,
                            unfocusedContainerColor = CreamBackground
                        )
                    )

                    // Selector de Categoría para la nueva nota
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Categoría:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ZenSage)
                        noteCreationCategories.forEach { cat ->
                            val isSelected = newNoteCategory == cat
                            Surface(
                                onClick = { newNoteCategory = cat },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) ZenOlive else CreamBackground,
                                border = BorderStroke(1.dp, if (isSelected) ZenOlive else ZenSage.copy(alpha = 0.3f)),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = categoryLabel(cat),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else ZenCharcoal
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (noteText.isNotBlank()) {
                                isAdding = true
                                lockManager.addNote(noteText, newNoteCategory) {
                                    isAdding = false
                                    noteText = ""
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✨ Guardar Nota Zen", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Buscador de Notas y Filtro de Categorías
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("🔍 Buscar en mis notas...", fontSize = 12.sp, color = ZenSage) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZenOlive,
                            unfocusedBorderColor = ZenSage.copy(alpha = 0.3f),
                            focusedContainerColor = ZenWhite,
                            unfocusedContainerColor = ZenWhite
                        )
                    )

                    // Filtros por pestaña de Categoría
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                onClick = { selectedCategory = cat },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) ZenCharcoal else CreamBackground,
                                modifier = Modifier.weight(1f).height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = categoryLabel(cat),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else ZenSage
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                // Lista de Notas Sincronizadas
                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("💡", fontSize = 32.sp)
                            Text("No hay notas encontradas.", fontSize = 13.sp, color = ZenSage, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Tus notas guardadas aquí no distraerán tu sesión de enfoque y se mantendrán sincronizadas.",
                                fontSize = 11.sp,
                                color = ZenSage.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                fontSizeSp = fontSizeSp,
                                onDelete = { lockManager.deleteNote(note.id) },
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Nota Zen", note.content)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Nota copiada al portapapeles", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: ZenNote,
    fontSizeSp: Int,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    val dateStr = remember(note.timestamp) {
        if (note.timestamp == 0L) ""
        else {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(Date(note.timestamp))
        }
    }

    val sourceBadge = if (note.deviceSource == "chrome_extension") "🌐 Chrome" else "📱 Android"
    val categoryBadge = when (note.category) {
        "tarea" -> "Tarea"
        "idea" -> "Idea"
        "reflexion" -> "Reflexión"
        else -> "General"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CreamBackground),
        border = BorderStroke(1.dp, ZenOlive.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ZenOlive.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "🏷️ $categoryBadge",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenOlive,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = note.content,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp + 6).sp,
                fontWeight = FontWeight.Normal,
                color = ZenCharcoal
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = dateStr, fontSize = 10.sp, color = ZenSage)
                    Text(text = "• $sourceBadge", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ZenOlive)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar", tint = ZenSage, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar", tint = ZenCoral, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// V24.1: etiqueta legible para cada categoría canónica compartida con la extensión.
private fun categoryLabel(cat: String): String = when (cat) {
    "tarea" -> "Tarea"
    "idea" -> "Idea"
    "reflexion" -> "Reflexión"
    "general" -> "General"
    "Todas" -> "Todas"
    else -> cat
}
