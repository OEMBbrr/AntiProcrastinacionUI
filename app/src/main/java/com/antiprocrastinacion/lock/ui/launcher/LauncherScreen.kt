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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * V27: Home del launcher rediseñado (arquitectura visual tipo iOS).
 * - Reloj real en vivo.
 * - Widgets con aspecto translúcido de material (esquinas redondeadas, cabecera de widget).
 * - Widget de Modo Enfoque con steppers de horas y minutos (sin presets fijos).
 * - Grid de apps propias + Dock inferior.
 * - Acceso a la pantalla de Modos.
 */
@Composable
fun LauncherScreen(
    lockManager: LockManager,
    onOpenNotes: () -> Unit,
    onOpenSleepCycle: () -> Unit,
    onOpenOrganizer: () -> Unit,
    onOpenAppDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModes: () -> Unit,
    onStartFocus: (Int) -> Unit,
    paseoActive: Boolean,
    onTogglePaseo: () -> Unit,
    noSocialActive: Boolean = false,
    onStartNoSocial: (Int) -> Unit = {},
    onNoSocialTregua: () -> Unit = {},
    appGrayscaleManualEnabled: Boolean = false,
    onToggleAppGrayscale: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentPhrase = remember { MotivationalPhrases.getRandomPhrase() }
    var recentNotes by remember { mutableStateOf<List<ZenNote>>(emptyList()) }

    var isDefaultHome by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isDefaultHome = withContext(kotlinx.coroutines.Dispatchers.IO) {
            LauncherUtils.isDefaultHome(context)
        }
    }

    LaunchedEffect(Unit) {
        lockManager.observeNotes { updated ->
            recentNotes = updated.take(3)
        }
    }

    // Reloj en vivo (se actualiza cada segundo)
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }

    // Frase cambia cada 20s
    var phraseIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            phraseIndex++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(LchGradientStart, LchGradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp)) // Dynamic Island

            // ---- RELOJ (widget principal del home) ----
            ClockWidget(now = now)

            Spacer(modifier = Modifier.height(12.dp))

            // ---- V30: Toggle manual de la escala de grises PROPIA de la app ----
            // No se puede apagar mientras haya un modo activo que la fuerce.
            val grayscaleForcedByMode = paseoActive || noSocialActive || lockManager.isWorkModeActive()
            AppGrayscalePill(
                enabled = appGrayscaleManualEnabled || grayscaleForcedByMode,
                lockedOn = grayscaleForcedByMode,
                onToggle = onToggleAppGrayscale
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Widget: Modo Enfoque (steppers horas/minutos) ----
            FocusModeWidget(onStartFocus = onStartFocus, onOpenModes = onOpenModes)

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Widget: Modo Paseo (activa/desactiva a voluntad) ----
            PaseoModeWidget(paseoActive = paseoActive, onTogglePaseo = onTogglePaseo)

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Widget: Modo Sin Redes (bloqueo de RRSS con límites) ----
            NoSocialModeWidget(
                lockManager = lockManager,
                active = noSocialActive,
                onStartNoSocial = onStartNoSocial,
                onNoSocialTregua = onNoSocialTregua,
                onOpenModes = onOpenModes
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Widget: Frase motivacional ----
            ZenWidgetCard(icon = Icons.Default.CenterFocusStrong, title = "En foco") {
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
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Fila de widgets pequeños: Notas + Tareas ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ZenWidgetCard(
                    icon = Icons.Default.EditNote,
                    title = "Notas",
                    onClick = onOpenNotes,
                    modifier = Modifier.weight(1f)
                ) {
                    if (recentNotes.isEmpty()) {
                        Text(
                            text = "Tu primera nota aparece aquí.",
                            fontSize = 12.sp,
                            color = LchMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            recentNotes.forEach { note ->
                                Text(
                                    text = note.content,
                                    fontSize = 12.sp,
                                    color = LchText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                ZenWidgetCard(
                    icon = Icons.Default.Checklist,
                    title = "Tareas",
                    onClick = onOpenOrganizer,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Organizador\nestilo Notion",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = LchMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Fila de widgets: Sueño + Modos ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ZenWidgetCard(
                    icon = Icons.Default.Bedtime,
                    title = "Sueño",
                    onClick = onOpenSleepCycle,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Ciclos de descanso",
                        fontSize = 12.sp,
                        color = LchMuted
                    )
                }
                ZenWidgetCard(
                    icon = Icons.Default.Tune,
                    title = "Modos",
                    onClick = onOpenModes,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Enfoque, paseo,\nsin redes",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = LchMuted
                    )
                }
            }

            // ---- Aviso: convertir en Inicio real ----
            if (!isDefaultHome) {
                Spacer(modifier = Modifier.height(16.dp))
                HomeRoleCard(onRequest = { LauncherUtils.requestHomeRole(context) })
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Grid de apps propias ----
            AppGrid(
                onOpenNotes = onOpenNotes,
                onOpenSleepCycle = onOpenSleepCycle,
                onOpenOrganizer = onOpenOrganizer,
                onOpenModes = onOpenModes
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Dock ----
            Dock(onOpenAppDrawer = onOpenAppDrawer, onOpenSettings = onOpenSettings)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Reloj gigante del home con fecha en español. */
@Composable
private fun ClockWidget(now: LocalTime) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())),
            fontSize = 76.sp,
            lineHeight = 78.sp,
            fontWeight = FontWeight.Light,
            color = LchText,
            letterSpacing = (-2).sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        val dayLabel = java.time.LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.getDefault()))
        Text(
            text = dayLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
            fontSize = 15.sp,
            color = LchMuted,
            fontWeight = FontWeight.Medium
        )
    }
}

/** V30: píldora compacta para activar a voluntad la escala de grises PROPIA de la app
 *  (desatura solo la interfaz del launcher). `lockedOn` bloquea el switch: no se puede
 *  apagar mientras haya un modo activo que fuerce el gris. */
@Composable
private fun AppGrayscalePill(
    enabled: Boolean,
    lockedOn: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) LchAccent.copy(alpha = 0.14f) else LchWidgetBg)
            .border(1.dp, if (enabled) LchAccent.copy(alpha = 0.5f) else LchWidgetBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(LchAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = LchAccent,
                modifier = Modifier.size(17.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Escala de grises",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LchText
            )
            Text(
                text = when {
                    lockedOn -> "Forzado por un modo activo"
                    enabled -> "Solo la interfaz de la app"
                    else -> "Solo la interfaz de la app"
                },
                fontSize = 10.sp,
                color = if (enabled) LchAccent else LchMuted
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { if (!lockedOn) onToggle() },
            enabled = !lockedOn,
            modifier = Modifier.scale(0.8f)
        )
    }
}

/** Widget de Modo Enfoque: selector de modo + steppers de horas y minutos. */
@Composable
private fun FocusModeWidget(
    onStartFocus: (Int) -> Unit,
    onOpenModes: () -> Unit
) {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(25) }

    val totalMinutes = hours * 60 + minutes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LchWidgetBg)
            .border(1.dp, LchWidgetBorder, RoundedCornerShape(28.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cabecera del widget
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LchAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = LchAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MODO ENFOQUE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LchAccent,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Tiempo de concentración sin distracciones",
                    fontSize = 11.sp,
                    color = LchMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOpenModes) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Ver todos los modos",
                    tint = LchMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Steppers: horas | minutos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeStepColumn(
                label = "HORAS",
                value = hours,
                onDec = { if (hours > 0) hours-- },
                onInc = { if (hours < 23) hours++ }
            )
            Text(
                text = ":",
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                color = LchMuted
            )
            TimeStepColumn(
                label = "MINUTOS",
                value = minutes,
                onDec = {
                    if (minutes > 0) minutes-- else if (hours > 0) { minutes = 59; hours-- }
                },
                onInc = {
                    if (minutes < 59) minutes++ else if (hours < 23) { minutes = 0; hours++ }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón iniciar
        Button(
            onClick = { if (totalMinutes > 0) onStartFocus(totalMinutes) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LchAccent,
                contentColor = LchOnAccent
            ),
            enabled = totalMinutes > 0
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hours > 0) "Iniciar · $hours h $minutes min" else "Iniciar · $minutes min",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Widget de Modo Paseo: interruptor de activación voluntaria sin temporizador. */
@Composable
private fun PaseoModeWidget(
    paseoActive: Boolean,
    onTogglePaseo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (paseoActive) LchAccent.copy(alpha = 0.14f) else LchWidgetBg)
            .border(1.dp, if (paseoActive) LchAccent.copy(alpha = 0.5f) else LchWidgetBorder, RoundedCornerShape(28.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LchAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsWalk,
                    contentDescription = null,
                    tint = LchAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MODO PASEO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LchAccent,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = if (paseoActive) "Activo: vive el momento" else "RRSS 15 min · WhatsApp 20 min · música bloqueada",
                    fontSize = 11.sp,
                    color = LchMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = paseoActive,
                onCheckedChange = { onTogglePaseo() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = LchAccent,
                    uncheckedTrackColor = LchMuted.copy(alpha = 0.3f),
                    checkedThumbColor = LchOnAccent
                )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (paseoActive)
                "● ACTIVO — se desactiva a voluntad"
            else
                "Sin temporizador. Al pasear, solo las apps con límite se bloquearán al alcanzarlo.",
            fontSize = 11.sp,
            fontWeight = if (paseoActive) FontWeight.Bold else FontWeight.Normal,
            color = if (paseoActive) LchAccent else LchMuted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * V30: Widget del Modo Sin Redes. Muestra el estado en vivo (activo, cooldown,
 * tregua) con un botón de tregua. Cuando está inactivo, abre la pantalla de
 * Modos para configurar la duración.
 */
@Composable
private fun NoSocialModeWidget(
    lockManager: LockManager,
    active: Boolean,
    onStartNoSocial: (Int) -> Unit,
    onNoSocialTregua: () -> Unit,
    onOpenModes: () -> Unit
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        while (active) {
            delay(1_000)
            tick++
        }
    }

    val inCooldown = lockManager.isNoSocialAllBlocked()
    val inTregua = lockManager.isNoSocialTempUnlocked()
    val treguaCooldown = lockManager.isNoSocialTreguaCooldown()

    val subtitle = when {
        !active -> "Sin RRSS 30 min · tregua 5 min"
        inCooldown -> "Cooldown ${LockManager.NOSOCIAL_ALL_BLOCKED_MINUTES} min: todo bloqueado"
        inTregua -> "Tregua activa: quedan ${(lockManager.noSocialTreguaRemainingMs() / 60_000L).coerceAtLeast(1)} min"
        else -> "Activo: quedan ${(lockManager.noSocialRemainingMs() / 60_000L).coerceAtLeast(1)} min"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (active) LchWarm.copy(alpha = 0.14f) else LchWidgetBg)
            .border(1.dp, if (active) LchWarm.copy(alpha = 0.5f) else LchWidgetBorder, RoundedCornerShape(28.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LchWarm.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = LchWarm,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MODO SIN REDES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LchWarm,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = LchMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (active) {
            Text(
                text = when {
                    inCooldown -> "Superaste el límite de 30 min de WhatsApp. Sin tregua disponible."
                    else -> "WhatsApp permitido (30 min). RRSS, juegos y navegadores bloqueados."
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = LchWarm,
                textAlign = TextAlign.Center
            )
            if (!inCooldown) {
                Button(
                    onClick = onNoSocialTregua,
                    enabled = !treguaCooldown,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LchWarm,
                        contentColor = Color.White,
                        disabledContainerColor = LchWarm.copy(alpha = 0.3f),
                        disabledContentColor = LchMuted
                    )
                ) {
                    Text(
                        text = when {
                            inTregua -> "Tregua activa"
                            treguaCooldown -> "Tregua en cooldown"
                            else -> "Pedir tregua (5 min)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Text(
                text = "No se puede desactivar una vez iniciado. Toca para configurar la duración.",
                fontSize = 11.sp,
                color = LchMuted,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onOpenModes,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LchWidgetBg,
                    contentColor = LchText
                )
            ) {
                Text("Configurar en Modos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Columna de stepper (label + botones −/valor/+) estilo contador de iOS. */
@Composable
private fun TimeStepColumn(
    label: String,
    value: Int,
    onDec: () -> Unit,
    onInc: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = LchMuted,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StepButton(icon = Icons.Default.Remove, onClick = onDec)
            Text(
                text = String.format(Locale.getDefault(), "%02d", value),
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                color = LchText,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp)
            )
            StepButton(icon = Icons.Default.Add, onClick = onInc)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(LchAccent.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LchAccent,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Tarjeta-widget translúcida tipo iOS con cabecera (icono + título). */
@Composable
private fun ZenWidgetCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickModifier = if (onClick != null) modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick) else modifier
    Column(
        modifier = clickModifier
            .clip(RoundedCornerShape(24.dp))
            .background(LchWidgetBg)
            .border(1.dp, LchWidgetBorder, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LchAccent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title.uppercase(Locale.getDefault()),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = LchMuted,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        content()
    }
}

/** Tarjeta para establecer la app como Inicio real del dispositivo. */
@Composable
private fun HomeRoleCard(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LchWidgetBg)
            .border(1.dp, LchWidgetBorder, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = LchAccent, modifier = Modifier.size(18.dp))
            Text(text = "Hazme tu Inicio", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LchText)
        }
        Text(
            text = "Pulsa Inicio en tu teléfono y este launcher (reloj, notas, frases y modos) aparecerá de inmediato, sin distracciones.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = LchMuted
        )
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LchAccent),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(text = "Establecer como Inicio", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Grid de iconos de apps propias con etiqueta (estilo home de iOS). */
@Composable
private fun AppGrid(
    onOpenNotes: () -> Unit,
    onOpenSleepCycle: () -> Unit,
    onOpenOrganizer: () -> Unit,
    onOpenModes: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AppIconTile(icon = Icons.Default.EditNote, label = "Notas", onClick = onOpenNotes)
        AppIconTile(icon = Icons.Default.Bedtime, label = "Sueño", onClick = onOpenSleepCycle)
        AppIconTile(icon = Icons.Default.Checklist, label = "Tareas", onClick = onOpenOrganizer)
        AppIconTile(icon = Icons.Default.Tune, label = "Modos", onClick = onOpenModes)
    }
}

@Composable
private fun AppIconTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(LchAccent, LchAccent.copy(alpha = 0.78f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = LchOnAccent,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = LchText,
            maxLines = 1
        )
    }
}

/** Dock inferior con acceso al cajón de apps y a los ajustes. */
@Composable
private fun Dock(onOpenAppDrawer: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LchWidgetBg)
            .border(1.dp, LchWidgetBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockTile(icon = Icons.Default.Apps, label = "Aplicaciones", onClick = onOpenAppDrawer)
        DockTile(icon = Icons.Default.Settings, label = "Ajustes", onClick = onOpenSettings)
    }
}

@Composable
private fun DockTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LchAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = LchAccent, modifier = Modifier.size(24.dp))
        }
        Text(text = label, fontSize = 10.sp, color = LchMuted, fontWeight = FontWeight.Medium)
    }
}
