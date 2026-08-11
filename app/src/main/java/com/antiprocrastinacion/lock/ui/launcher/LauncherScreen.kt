package com.antiprocrastinacion.lock.ui.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
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
import com.antiprocrastinacion.lock.LauncherUtils
import com.antiprocrastinacion.lock.LockManager
import com.antiprocrastinacion.lock.MotivationalPhrases
import com.antiprocrastinacion.lock.ZenNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * V26: Pantalla principal del nuevo launcher.
 * - Barra superior con las apps propias (Notas, Ciclo de Sueño, Organizador).
 * - Inicio rápido de Modo Enfoque (25/45/90 min).
 * - Frases de motivación + widgets de tareas, hora de dormir y notas recientes.
 * - Aviso para establecer la app como inicio predeterminado (HOME real).
 * - Acceso al cajón de aplicaciones (solo logos) y a los ajustes.
 */
@Composable
fun LauncherScreen(
    lockManager: LockManager,
    onOpenNotes: () -> Unit,
    onOpenSleepCycle: () -> Unit,
    onOpenOrganizer: () -> Unit,
    onOpenAppDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartFocus: (Int) -> Unit
) {
    val currentPhrase = remember { MotivationalPhrases.getRandomPhrase() }
    var recentNotes by remember { mutableStateOf<List<ZenNote>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // V26: launcher como inicio real -> comprobar si es HOME predeterminado
    var isDefaultHome by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isDefaultHome = withContext(kotlinx.coroutines.Dispatchers.IO) {
            LauncherUtils.isDefaultHome(context)
        }
    }

    // V25: escuchar notas para el widget "Notas más recientes"
    LaunchedEffect(Unit) {
        lockManager.observeNotes { updated ->
            recentNotes = updated.take(3)
        }
    }

    // Frase cambia cada 20s para no aburrir
    var phraseIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            phraseIndex++
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LchBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp)) // Dynamic Island

            // Cabecera del launcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "antiprocrastinación",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = LchText,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Launcher sin distracciones",
                        fontSize = 12.sp,
                        color = LchMuted
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = LchMuted
                    )
                }
            }

            // Barra superior: apps propias de AntiProcrastinación
            LauncherQuickApps(
                onOpenNotes = onOpenNotes,
                onOpenSleepCycle = onOpenSleepCycle,
                onOpenOrganizer = onOpenOrganizer
            )

            // V26: aviso para convertir la app en el Inicio real del dispositivo
            if (!isDefaultHome) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = LchSurfaceHi),
                    border = BorderStroke(1.dp, LchBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = LchAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Hazme tu Inicio",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = LchText
                            )
                        }
                        Text(
                            text = "Pulsa Inicio en tu teléfono y este launcher (notas, frases y modos) aparecerá de inmediato, sin distracciones.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = LchMuted
                        )
                        Button(
                            onClick = { LauncherUtils.requestHomeRole(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LchAccent),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(
                                text = "Establecer como Inicio",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // V26: inicio rápido del Modo Enfoque
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LchSurface),
                border = BorderStroke(1.dp, LchBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = LchAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Modo Enfoque",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = LchText
                            )
                            Text(
                                text = "Inicia una sesión de concentración sin distracciones",
                                fontSize = 11.sp,
                                color = LchMuted
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FocusQuickButton(label = "25 min", duration = 25, onClick = onStartFocus)
                        FocusQuickButton(label = "45 min", duration = 45, onClick = onStartFocus)
                        FocusQuickButton(label = "90 min", duration = 90, onClick = onStartFocus)
                    }
                }
            }

            // Tarjeta de frase motivacional
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LchSurface),
                border = BorderStroke(1.dp, LchBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "●",
                        fontSize = 8.sp,
                        color = LchAccent
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AnimatedContent(
                        targetState = phraseIndex,
                        label = "PhraseLauncher",
                        transitionSpec = {
                            (fadeIn(tween(600)) + scaleIn(initialScale = 0.97f, animationSpec = tween(600)))
                                .togetherWith(
                                    fadeOut(tween(600)) + scaleOut(targetScale = 1.03f, animationSpec = tween(600))
                                )
                        }
                    ) { idx ->
                        Text(
                            text = MotivationalPhrases.PHRASES[idx % MotivationalPhrases.PHRASES.size],
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = LchText,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Widget: Tareas de hoy (Organizador)
            LauncherWidgetCard(
                icon = Icons.Default.Checklist,
                title = "Tareas de hoy",
                subtitle = "Abre tu organizador estilo Notion",
                onClick = onOpenOrganizer
            )

            // Widget: Hora recomendada para dormir (Ciclo de sueño)
            LauncherWidgetCard(
                icon = Icons.Default.Bedtime,
                title = "Ciclo de sueño",
                subtitle = "Calcula tus horas de descanso",
                onClick = onOpenSleepCycle
            )

            // Widget: Notas más recientes
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNotes),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LchSurface),
                border = BorderStroke(1.dp, LchBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = LchAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Notas recientes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LchText
                        )
                    }
                    if (recentNotes.isEmpty()) {
                        Text(
                            text = "Todavía no tienes notas. Escribe tu primera nota aquí.",
                            fontSize = 12.sp,
                            color = LchMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        recentNotes.forEach { note ->
                            Text(
                                text = note.content,
                                fontSize = 13.sp,
                                color = LchText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón de acceso al cajón de aplicaciones
            Button(
                onClick = onOpenAppDrawer,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LchAccent),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Aplicaciones",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** Atajos superiores a las apps propias de la suite. */
@Composable
private fun LauncherQuickApps(
    onOpenNotes: () -> Unit,
    onOpenSleepCycle: () -> Unit,
    onOpenOrganizer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickAppTile(icon = Icons.Default.EditNote, label = "Notas", onClick = onOpenNotes, weight = 1f)
        QuickAppTile(icon = Icons.Default.Bedtime, label = "Sueño", onClick = onOpenSleepCycle, weight = 1f)
        QuickAppTile(icon = Icons.Default.Checklist, label = "Tareas", onClick = onOpenOrganizer, weight = 1f)
    }
}

@Composable
private fun RowScope.QuickAppTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    weight: Float
) {
    Column(
        modifier = Modifier
            .weight(weight)
            .clip(RoundedCornerShape(16.dp))
            .background(LchSurface)
            .border(1.dp, LchBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(LchAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = LchAccent,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = LchText
        )
    }
}

/** Tarjeta-widget genérica del launcher. */
@Composable
private fun LauncherWidgetCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = LchAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LchText
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = LchMuted
                )
            }
        }
    }
}

/** Botón de inicio rápido de Modo Enfoque (25/45/90 min). */
@Composable
private fun RowScope.FocusQuickButton(
    label: String,
    duration: Int,
    onClick: (Int) -> Unit
) {
    Button(
        onClick = { onClick(duration) },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LchAccent),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

