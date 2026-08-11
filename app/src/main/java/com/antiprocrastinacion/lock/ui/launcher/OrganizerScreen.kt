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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * V25: App Organizador de Tareas estilo Notion del nuevo launcher.
 * Esqueleto funcional: permite añadir y completar tareas del día.
 * La versión completa (categorías, proyectos, sincronización) llega en
 * una iteración posterior.
 */
@Composable
fun OrganizerScreen(
    onBack: () -> Unit
) {
    var tasks by remember {
        mutableStateOf(
            listOf(
                LauncherTask(title = "Planificar el día", done = false, time = System.currentTimeMillis()),
                LauncherTask(title = "Hacer una pausa consciente", done = false, time = System.currentTimeMillis() - 60_000),
                LauncherTask(title = "Revisar mis notas", done = true, time = System.currentTimeMillis() - 120_000)
            )
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }

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
                        text = "Organizador",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LchText
                    )
                    Text(
                        text = "Tareas del día, estilo Notion",
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
                    Icon(Icons.Default.Add, contentDescription = "Nueva tarea")
                }
            }

            // Resumen del día
            val doneCount = tasks.count { it.done }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LchSurface),
                border = BorderStroke(1.dp, LchBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(LchAccent.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = LchAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Tareas de hoy",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LchText
                        )
                        Text(
                            text = "$doneCount de ${tasks.size} completadas",
                            fontSize = 12.sp,
                            color = LchMuted
                        )
                    }
                }
            }

            if (tasks.isEmpty()) {
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
                        Text(text = "🗂️", fontSize = 34.sp)
                        Text(
                            text = "Nada pendiente por hoy",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LchText
                        )
                        Text(
                            text = "Añade tu primera tarea tocando +",
                            fontSize = 12.sp,
                            color = LchMuted
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
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = {
                                tasks = tasks.map { t ->
                                    if (t.id == task.id) t.copy(done = !t.done) else t
                                }
                            },
                            onDelete = {
                                tasks = tasks.filterNot { it.id == task.id }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            onDismiss = { showAddDialog = false },
            onSave = { text ->
                tasks = listOf(
                    LauncherTask(title = text, done = false, time = System.currentTimeMillis())
                ) + tasks
                showAddDialog = false
            }
        )
    }
}

data class LauncherTask(
    val id: String = "task_${System.currentTimeMillis()}_${(1000..9999).random()}",
    val title: String,
    val done: Boolean,
    val time: Long
)

@Composable
private fun TaskRow(
    task: LauncherTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(task.time) {
        if (task.time == 0L) "" else SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(task.time))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LchSurface),
        border = BorderStroke(1.dp, LchBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (task.done) LchAccent.copy(alpha = 0.18f) else LchSurfaceHi)
                    .border(1.dp, if (task.done) LchAccent else LchBorder, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (task.done) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completada",
                        tint = LchAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (task.done) LchMuted else LchText,
                    textDecoration = if (task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    color = LchMuted
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Eliminar",
                    tint = LchWarm,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(45f)
                )
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LchSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Nueva tarea",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = LchText
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("¿Qué tienes que hacer?", fontSize = 13.sp, color = LchMuted) },
                modifier = Modifier.fillMaxWidth(),
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
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSave(text.trim()) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = LchAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Añadir", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = LchMuted)
            }
        }
    )
}
