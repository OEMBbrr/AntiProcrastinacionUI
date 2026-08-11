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
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiprocrastinacion.lock.ui.theme.*
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
    onTogglePaseo: () -> Unit
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
                defaultMinutes = 25
            ) { minutes -> onStartFocus(minutes) }

            ModeCard(
                icon = Icons.Default.VisibilityOff,
                name = "Modo Sin Redes",
                description = "Pantalla en escala de grises. Desactiva redes sociales.",
                accent = ZenCoral,
                cardColor = ZenSoftCoral,
                defaultMinutes = 30
            ) { minutes -> onStartFocus(minutes) }

            ModeToggleCard(
                icon = Icons.Default.DirectionsWalk,
                name = "Modo Paseo",
                description = "Se activa y desactiva a voluntad, sin temporizador. RRSS, juegos y apps dopamínicas con límite de 15 min seguidos (se bloquea solo esa app), WhatsApp 20 min, música bloqueada. Cámara, llamadas, SMS y maps siempre libres.",
                accent = ZenGreen,
                cardColor = ZenSoftGreen,
                active = paseoActive,
                onToggle = onTogglePaseo
            )

            ModeCard(
                icon = Icons.Default.School,
                name = "Modo Estudio",
                description = "Entorno de trabajo sin interrupciones.",
                accent = ZenSage,
                cardColor = ZenWhite,
                defaultMinutes = 45
            ) { minutes -> onStartFocus(minutes) }

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
    onStart: (Int) -> Unit
) {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(defaultMinutes) }

    val totalMinutes = hours * 60 + minutes

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
        }
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
