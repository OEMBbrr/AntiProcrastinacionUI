package com.antiprocrastinacion.lock.ui.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiprocrastinacion.lock.FocusSegment
import com.antiprocrastinacion.lock.LockManager
import com.antiprocrastinacion.lock.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * V28: Pantalla de Modos — el lugar central donde se activan los distintos
 * modos de AntiProcrastinación. Cada modo tiene su propio stepper de horas y
 * minutos (sin presets fijos) y un botón de inicio, con tarjetas Zen.
 */
@Composable
fun ModosScreen(
    onBack: () -> Unit,
    onStartFocus: (Int) -> Unit,
    paseoActive: Boolean,
    onTogglePaseo: () -> Unit,
    lockManager: LockManager,
    onOpenSettings: () -> Unit,
    noSocialActive: Boolean = false,
    onStartNoSocial: (Int) -> Unit = {},
    onNoSocialTregua: () -> Unit = {}
) {
    Scaffold(
        containerColor = CreamBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // V32: plan de actividades del Modo Enfoque (persistido en LockManager)
            var focusPlan by remember { mutableStateOf(lockManager.focusActivityPlan) }
            // Cabecera estilo iOS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = ZenCharcoal
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Modos",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenCharcoal,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Elige un modo y configura su duración",
                        fontSize = 13.sp,
                        color = ZenSage
                    )
                }
            }

            ModeCard(
                icon = Icons.Default.Timer,
                name = "Modo Enfoque",
                description = "Bloquea distracciones y trabaja a fondo.",
                accent = ZenOlive,
                cardColor = ZenSoftBlue,
                defaultMinutes = 25,
                plan = focusPlan,
                onPlanChange = { plan ->
                    focusPlan = plan
                    lockManager.focusActivityPlan = plan
                }
            ) { minutes -> onStartFocus(minutes) }

            NoSocialModeCard(
                lockManager = lockManager,
                active = noSocialActive,
                onStart = onStartNoSocial,
                onTregua = onNoSocialTregua
            )

            ModeToggleCard(
                icon = Icons.Default.DirectionsWalk,
                name = "Modo Paseo",
                description = "Se activa y desactiva a voluntad, sin temporizador. RRSS, juegos y apps dopamínicas con límite de 15 min seguidos (se bloquea solo esa app), WhatsApp 20 min, música bloqueada. Cámara, llamadas, SMS y maps siempre libres.",
                accent = ZenGreen,
                cardColor = ZenSoftGreen,
                active = paseoActive,
                onToggle = onTogglePaseo
            )

            WorkModeCard(
                lockManager = lockManager,
                onOpenSettings = onOpenSettings
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Tarjeta de un modo con steppers de horas/minutos y botón de inicio. */
@Composable
private fun ModeCard(
    icon: ImageVector,
    name: String,
    description: String,
    accent: Color,
    cardColor: Color,
    defaultMinutes: Int,
    plan: List<FocusSegment>,
    onPlanChange: (List<FocusSegment>) -> Unit,
    onStart: (Int) -> Unit
) {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(defaultMinutes) }

    val totalMinutes = hours * 60 + minutes
    val hasPlan = plan.isNotEmpty()
    val planTotal = plan.sumOf { it.durationMinutes.coerceAtLeast(1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = name, tint = accent, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenCharcoal
                    )
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = ZenSage,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (hasPlan) {
                // V32: plan de actividades definido — el plan manda sobre los steppers
                FocusPlanEditor(
                    plan = plan,
                    planTotal = planTotal,
                    accent = accent,
                    onPlanChange = onPlanChange
                )
                Button(
                    onClick = { if (planTotal > 0) onStart(planTotal) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = if (accent.luminance() > 0.5f) Color(0xFF1B252E) else Color.White
                    ),
                    enabled = planTotal > 0
                ) {
                    Text(
                        text = "Iniciar plan ($planTotal min)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Steppers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeStepColumn(
                        label = "HORAS",
                        value = hours,
                        accent = accent,
                        onDec = { if (hours > 0) hours-- },
                        onInc = { if (hours < 23) hours++ }
                    )
                    Text(text = ":", fontSize = 34.sp, fontWeight = FontWeight.Light, color = ZenSage)
                    ModeStepColumn(
                        label = "MINUTOS",
                        value = minutes,
                        accent = accent,
                        onDec = {
                            if (minutes > 0) minutes-- else if (hours > 0) { minutes = 59; hours-- }
                        },
                        onInc = {
                            if (minutes < 59) minutes++ else if (hours < 23) { minutes = 0; hours++ }
                        }
                    )
                }

                Button(
                    onClick = { if (totalMinutes > 0) onStart(totalMinutes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = if (accent.luminance() > 0.5f) Color(0xFF1B252E) else Color.White
                    ),
                    enabled = totalMinutes > 0
                ) {
                    Text(
                        text = if (hours > 0) "Iniciar $hours h $minutes min" else "Iniciar $minutes min",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // V32: crear el plan a partir de la duración elegida en los steppers
                OutlinedButton(
                    onClick = {
                        onPlanChange(
                            listOf(
                                FocusSegment("work", "Actividad 1", totalMinutes.coerceAtLeast(1)),
                                FocusSegment("rest", "", 5)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Crear plan de actividades",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }
        }
    }
}

/**
 * V32: Editor del plan de actividades. Cada fila es una actividad (con título) o un
 * descanso; el usuario decide cuántos y dónde. Total = suma de todas las filas.
 */
@Composable
private fun FocusPlanEditor(
    plan: List<FocusSegment>,
    planTotal: Int,
    accent: Color,
    onPlanChange: (List<FocusSegment>) -> Unit
) {
    // V32: los descansos no pueden superar el 15% del total del plan
    val total = planTotal
    val breakMinutes = plan.filter { it.type == "rest" }.sumOf { it.durationMinutes.coerceAtLeast(1) }
    val maxBreakMinutes = (total * 15 / 100).coerceAtLeast(1)
    val breaksFull = breakMinutes >= maxBreakMinutes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PLAN DE ACTIVIDADES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Cada actividad y descanso suena una alarma de 5 s al comenzar. Total: $total min. Descansos: $breakMinutes de máx $maxBreakMinutes min (15%).",
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = ZenSage
                )
            }
            TextButton(onClick = { onPlanChange(emptyList()) }) {
                Text(text = "Quitar", fontSize = 12.sp, color = ZenSage)
            }
        }

        plan.forEachIndexed { index, seg ->
            FocusPlanRow(
                seg = seg,
                accent = accent,
                canIncreaseBreak = !breaksFull,
                onUpdate = { updated ->
                    onPlanChange(plan.toMutableList().also { it[index] = updated })
                },
                onDelete = {
                    onPlanChange(plan.toMutableList().also { it.removeAt(index) })
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val workCount = plan.count { it.type != "rest" }
            OutlinedButton(
                onClick = {
                    onPlanChange(plan + FocusSegment("work", "Actividad ${workCount + 1}", 25))
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(text = "Actividad", fontSize = 12.sp, color = accent)
            }
            OutlinedButton(
                onClick = {
                    val newBreakMin = (maxBreakMinutes - breakMinutes).coerceAtLeast(1)
                    onPlanChange(plan + FocusSegment("rest", "", newBreakMin))
                },
                enabled = !breaksFull,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(text = "Descanso", fontSize = 12.sp, color = accent)
            }
        }
        if (breaksFull) {
            Text(
                text = "Límite alcanzado: los descansos no pueden pasar del 15% del tiempo del plan.",
                fontSize = 10.sp,
                color = ZenCoral,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Fila del plan: actividad editable (título + duración) o descanso fijo (duración). */
@Composable
private fun FocusPlanRow(
    seg: FocusSegment,
    accent: Color,
    canIncreaseBreak: Boolean,
    onUpdate: (FocusSegment) -> Unit,
    onDelete: () -> Unit
) {
    val isWork = seg.type != "rest"
    val rowColor = if (isWork) accent else ZenGreen
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(rowColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            if (isWork) {
                OutlinedTextField(
                    value = seg.title,
                    onValueChange = { onUpdate(seg.copy(title = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    placeholder = { Text("Nombre de la actividad", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp)
                )
            } else {
                Text(
                    text = "Descanso",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZenCharcoal,
                    modifier = Modifier.padding(vertical = 13.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { onUpdate(seg.copy(durationMinutes = (seg.durationMinutes - 1).coerceAtLeast(1))) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Menos", tint = rowColor, modifier = Modifier.size(18.dp))
                }
                Text(text = "${seg.durationMinutes} min", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZenCharcoal)
                IconButton(
                    onClick = { onUpdate(seg.copy(durationMinutes = (seg.durationMinutes + 1).coerceAtMost(240))) },
                    enabled = isWork || canIncreaseBreak
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Más", tint = if (isWork || canIncreaseBreak) rowColor else ZenSage.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = ZenSage, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * V30: Tarjeta del Modo Sin Redes. Bloquea RRSS/juegos/navegadores salvo WhatsApp
 * (límite 30 min seguidos; al superarlo, cooldown de 5 min con TODAS las apps
 * bloqueadas y sin tregua). Tregua propia de 5 min (cooldown de 10 min entre
 * treguas). No se puede desactivar hasta que acabe el tiempo programado.
 */
@Composable
private fun NoSocialModeCard(
    lockManager: LockManager,
    active: Boolean,
    onStart: (Int) -> Unit,
    onTregua: () -> Unit
) {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(lockManager.noSocialDefaultMinutes) }
    val totalMinutes = hours * 60 + minutes
    var tick by remember { mutableIntStateOf(0) }

    // Refrescar cada segundo mientras el modo esté activo (cuenta atrás).
    LaunchedEffect(active) {
        while (active) {
            delay(1_000)
            tick++
        }
    }

    val remainingMs = lockManager.noSocialRemainingMs()
    val inCooldown = lockManager.isNoSocialAllBlocked()
    val inTregua = lockManager.isNoSocialTempUnlocked()
    val treguaCooldown = lockManager.isNoSocialTreguaCooldown()

    val accent = if (active) ZenCoral else ZenSage
    val cardColor = if (active) ZenSoftCoral else ZenWhite

    val statusText = when {
        !active -> "○ Inactivo — elige la duración y toca Iniciar"
        inCooldown ->
            "● Cooldown de ${LockManager.NOSOCIAL_ALL_BLOCKED_MINUTES} min: TODAS las apps bloqueadas (sin tregua). Superaste el límite de ${LockManager.NOSOCIAL_APP_LIMIT_MINUTES} min de WhatsApp."
        inTregua ->
            "● Tregua de ${LockManager.NOSOCIAL_TREGUA_MINUTES} min ACTIVA — todo abierto. Quedan ${formatDuration(lockManager.noSocialTreguaRemainingMs())}"
        else ->
            "● ACTIVO — RRSS, juegos y navegadores bloqueados. Quedan ${formatDuration(remainingMs)}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Modo Sin Redes",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenCharcoal
                    )
                    Text(
                        text = "Sin RRSS, juegos ni navegadores. WhatsApp permitido 30 min seguidos. No se puede desactivar hasta que acabe.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = ZenSage,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) ZenCoral else ZenSage
            )

            if (active) {
                if (!inCooldown) {
                    Button(
                        onClick = onTregua,
                        enabled = !treguaCooldown,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZenCoral,
                            contentColor = Color.White,
                            disabledContainerColor = ZenCoral.copy(alpha = 0.3f),
                            disabledContentColor = ZenSage
                        )
                    ) {
                        Text(
                            text = when {
                                inTregua -> "Tregua activa (${formatDuration(lockManager.noSocialTreguaRemainingMs())})"
                                treguaCooldown -> "Tregua en cooldown (10 min entre treguas)"
                                else -> "Pedir tregua (${LockManager.NOSOCIAL_TREGUA_MINUTES} min)"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeStepColumn(
                        label = "HORAS",
                        value = hours,
                        accent = ZenCoral,
                        onDec = { if (hours > 0) hours-- },
                        onInc = { if (hours < 23) hours++ }
                    )
                    Text(text = ":", fontSize = 34.sp, fontWeight = FontWeight.Light, color = ZenSage)
                    ModeStepColumn(
                        label = "MINUTOS",
                        value = minutes,
                        accent = ZenCoral,
                        onDec = {
                            if (minutes > 0) minutes-- else if (hours > 0) { minutes = 59; hours-- }
                        },
                        onInc = {
                            if (minutes < 59) minutes++ else if (hours < 23) { minutes = 0; hours++ }
                        }
                    )
                }

                Button(
                    onClick = { if (totalMinutes > 0) onStart(totalMinutes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZenCoral,
                        contentColor = Color.White
                    ),
                    enabled = totalMinutes > 0
                ) {
                    Text(
                        text = if (hours > 0) "Iniciar $hours h $minutes min" else "Iniciar $minutes min",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Formatea milisegundos como "1 h 5 min" / "35 min". */
private fun formatDuration(millis: Long): String {
    val totalMin = (millis / 60_000L).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "$m min"
    }
}

@Composable
private fun ModeToggleCard(
    icon: ImageVector,
    name: String,
    description: String,
    accent: Color,
    cardColor: Color,
    active: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = name, tint = accent, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenCharcoal
                    )
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = ZenSage,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = active,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accent,
                        uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                        checkedThumbColor = Color.White
                    )
                )
            }

            Text(
                text = if (active) "● Modo Paseo ACTIVO — se desactiva a voluntad" else "○ Inactivo — toca el interruptor para activarlo",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) accent else ZenSage
            )
        }
    }
}

/** Tarjeta del Modo Escuela/Trabajo: automático según el horario L-V, sin botón físico. */
@Composable
private fun WorkModeCard(
    lockManager: LockManager,
    onOpenSettings: () -> Unit
) {
    var workActiveNow by remember { mutableStateOf(lockManager.isWorkModeActive()) }
    val configuredDays = lockManager.workSchedule.size

    LaunchedEffect(Unit) {
        while (true) {
            workActiveNow = lockManager.isWorkModeActive()
            delay(1_000)
        }
    }

    val accent = if (workActiveNow) ZenGreen else ZenSage
    val cardColor = if (workActiveNow) ZenSoftGreen else ZenWhite

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.School, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Modo Escuela/Trabajo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenCharcoal
                    )
                    Text(
                        text = "Se activa y desactiva SOLO por horario (Lunes a Viernes). Sin botón físico: mientras estés en clase o trabajo, las apps que distraen quedan bloqueadas automáticamente.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = ZenSage,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = when {
                    workActiveNow -> "● ACTIVO ahora — distracciones bloqueadas"
                    configuredDays == 0 -> "○ Sin horario configurado"
                    else -> "○ Automático según tu horario ($configuredDays días activos)"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (workActiveNow) ZenGreen else ZenSage
            )

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ZenOlive.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = ZenOlive, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Configurar horario en Ajustes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZenOlive)
            }
        }
    }
}

/** Columna de stepper para los modos con temporizador. */
@Composable
private fun ModeStepColumn(
    label: String,
    value: Int,
    accent: Color,
    onDec: () -> Unit,
    onInc: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ZenSage,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeStepButton(icon = Icons.Default.Remove, accent = accent, onClick = onDec)
            Text(
                text = String.format(Locale.getDefault(), "%02d", value),
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                color = ZenCharcoal,
                modifier = Modifier.width(60.dp)
            )
            ModeStepButton(icon = Icons.Default.Add, accent = accent, onClick = onInc)
        }
    }
}

@Composable
private fun ModeStepButton(icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
    }
}
