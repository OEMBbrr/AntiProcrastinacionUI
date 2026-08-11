package com.antiprocrastinacion.lock.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiprocrastinacion.lock.LockManager
import com.antiprocrastinacion.lock.ui.theme.*
import kotlinx.coroutines.delay

import com.antiprocrastinacion.lock.MotivationalPhrases

@Composable
fun ZenScreen(
    lockManager: LockManager,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var timeRemaining by remember { mutableStateOf(lockManager.timeRemaining) }
    var tempUnlockTimeRemaining by remember { mutableStateOf(if (lockManager.isTempUnlocked) lockManager.tempUnlockEndTime - System.currentTimeMillis() else 0L) }
    var cooldownTimeRemaining by remember { mutableStateOf(lockManager.cooldownTimeRemaining) }
    // V24: cuenta regresiva de la fase de descanso Pomodoro (tregua libre)
    var restTimeRemaining by remember { mutableStateOf(0L) }
    var isRestPhase by remember { mutableStateOf(lockManager.isPomodoroRestPhase) }

    // Índice para carrusel de 150 frases motivacionales y de reflexión (60 segundos por frase)
    var phraseIndex by remember { mutableIntStateOf((0..149).random()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60000) // 60 segundos por cada frase
            phraseIndex = (phraseIndex + 1) % MotivationalPhrases.PHRASES.size
        }
    }

    // Modal para "Ya terminé mi actividad" (Frase LARGA 150-200 palabras)
    var showLongChallengeModal by remember { mutableStateOf(false) }
    var longUserInput by remember { mutableStateOf("") }
    val targetLongPhrase = lockManager.longPhrase

    // Modal para la Tregua Temporal (2 Opciones: Matemáticas o Frase Mediana de 100)
    var showTempChallengeModal by remember { mutableStateOf(false) }
    var tempChallengeType by remember { mutableStateOf("") } // "math" o "phrase"
    
    // Desafío matemático
    var mathProblemText by remember { mutableStateOf("") }
    var mathCorrectAnswer by remember { mutableIntStateOf(0) }
    var mathUserInput by remember { mutableStateOf("") }

    // Desafío de Frase Mediana
    var mediumPhraseText by remember { mutableStateOf("") }
    var mediumPhraseUserInput by remember { mutableStateOf("") }

    // Desafío cuando el tiempo expiró (Frase CORTA 1 oración)
    var shortUserInput by remember { mutableStateOf("") }
    val targetShortPhrase = lockManager.shortPhrase

    // Hilo de actualización de temporizadores
    LaunchedEffect(Unit) {
        while (lockManager.isLocked) {
            timeRemaining = lockManager.timeRemaining
            cooldownTimeRemaining = lockManager.cooldownTimeRemaining
            tempUnlockTimeRemaining = if (lockManager.isTempUnlocked) {
                lockManager.tempUnlockEndTime - System.currentTimeMillis()
            } else {
                0L
            }
            // V24: fase de descanso Pomodoro = tregua libre automática
            val now = System.currentTimeMillis()
            val phase = lockManager.currentPomodoroPhase(now)
            isRestPhase = phase?.type == "rest"
            if (isRestPhase) {
                restTimeRemaining = phase?.endTime?.minus(now) ?: 0L
                lockManager.unlockForRest()
            } else {
                restTimeRemaining = 0L
            }
            if (timeRemaining <= 0) {
                lockManager.stopLock()
                onUnlocked()
                break
            }
            delay(1000)
        }
        timeRemaining = 0
    }

    // Formateador de tiempo (HH:MM:SS)
    fun formatDuration(ms: Long): String {
        val totalSecs = ms / 1000
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    // Contar palabras
    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split("\\s+".toRegex()).size
    }

    // Generar desafío matemático
    fun prepareMathChallenge() {
        val type = (1..2).random()
        if (type == 1) {
            val a = (12..49).random()
            val b = (11..35).random()
            mathProblemText = "$a × $b"
            mathCorrectAnswer = a * b
        } else {
            val a = (200..899).random()
            val b = (100..499).random()
            val c = (50..299).random()
            mathProblemText = "$a + $b − $c"
            mathCorrectAnswer = a + b - c
        }
        mathUserInput = ""
        tempChallengeType = "math"
    }

    // Generar desafío de Frase Mediana (1 de 100)
    fun preparePhraseChallenge() {
        mediumPhraseText = LockManager.MEDIUM_TEMP_UNLOCK_PHRASES.random()
        mediumPhraseUserInput = ""
        tempChallengeType = "phrase"
    }

    // Resaltador de texto para verificación de errores
    @Composable
    fun buildHighlightedText(userInput: String, targetPhrase: String): AnnotatedString {
        return remember(userInput, targetPhrase) {
            buildAnnotatedString {
                for (i in targetPhrase.indices) {
                    val expectedChar = targetPhrase[i]
                    if (i < userInput.length) {
                        val actualChar = userInput[i]
                        if (actualChar == expectedChar) {
                            withStyle(style = SpanStyle(color = ZenGreen, fontWeight = FontWeight.Medium)) {
                                append(expectedChar)
                            }
                        } else {
                            withStyle(style = SpanStyle(color = ZenCoral, fontWeight = FontWeight.Bold)) {
                                append(expectedChar)
                            }
                        }
                    } else {
                        withStyle(style = SpanStyle(color = ZenSage.copy(alpha = 0.5f))) {
                            append(expectedChar)
                        }
                    }
                }
            }
        }
    }

    var showNotesModal by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CreamBackground
    ) { innerPadding ->
        // V24.1 (T3): scroll en la pantalla principal para que todo el contenido
        // (cabecera, frase, temporizador, botones) se pueda ver en pantallas pequeñas.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera Zen
            // V24.1 (T5): padding superior amplio para que el logo, "MODO ENFOQUE" y la
            // frase motivadora no queden ocultos bajo la cámara perforada / Dynamic Island.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 56.dp)
            ) {
                Image(
                    painter = painterResource(id = com.antiprocrastinacion.lock.R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.dp, ZenSage.copy(alpha = 0.4f)), CircleShape)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (timeRemaining > 0) "MODO ENFOQUE" else "¡TIEMPO COMPLETADO!",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 2.sp,
                        color = if (timeRemaining > 0) ZenSage else ZenGreen,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                // Carrusel Animado de 150 Frases de Reflexión y Motivación (Duración 60s, Intocable)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenOlive.copy(alpha = 0.18f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = phraseIndex,
                            transitionSpec = {
                                (slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(800)) { fullWidth -> fullWidth } + fadeIn(animationSpec = androidx.compose.animation.core.tween(800)))
                                    .togetherWith(slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(800)) { fullWidth -> -fullWidth } + fadeOut(animationSpec = androidx.compose.animation.core.tween(800)))
                            },
                            label = "MotivationalPhraseCarousel"
                        ) { targetIdx ->
                            val currentPhrase = MotivationalPhrases.PHRASES[targetIdx % MotivationalPhrases.PHRASES.size]
                            Text(
                                text = currentPhrase,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ZenCharcoal,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (showNotesModal) {
                ZenNotesModal(
                    lockManager = lockManager,
                    onDismiss = { showNotesModal = false }
                )
            }

            // Bloque Central: Temporizador o Desafío Final
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (timeRemaining > 0) {
                    // V24: Banner de Descanso Pomodoro (Tregua libre durante la fase de descanso)
                    if (isRestPhase) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = ZenSoftGreen),
                            border = BorderStroke(1.dp, ZenGreen.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.FreeBreakfast, contentDescription = null, tint = ZenGreen)
                                    Text(
                                        text = "DESCANSO POMODORO",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = ZenGreen,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tregua libre activa: ${formatDuration(restTimeRemaining)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ZenCharcoal
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Accede a tus aplicaciones permitidas sin escribir nada. Al terminar el descanso volverás a estar bloqueado.",
                                    fontSize = 12.sp,
                                    color = ZenSage,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Puedes terminar el descanso anticipadamente con \"Comenzar bloqueo\".",
                                    fontSize = 11.sp,
                                    color = ZenSage,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        lockManager.endPomodoroRestNow()
                                        isRestPhase = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Terminar descanso (volver a trabajo)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Temporizador Gigante en Tiempo Real
                    Text(
                        text = formatDuration(timeRemaining),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 64.sp,
                            fontWeight = FontWeight.ExtraLight,
                            color = ZenCharcoal
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mantén tu mente centrada en tu actividad actual.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZenSage,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    // Botón Único de Notas Zen (Diseño sobrio y elegante alineado a la paleta Zen)
                    Button(
                        onClick = { showNotesModal = true },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = com.antiprocrastinacion.lock.R.drawable.memo_3d),
                                contentDescription = "Notas 3D",
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Mis Notas (Anotar idea o pendiente)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Estado y Launcher de Apps Permitidas durante Tregua Temporal
                    if (lockManager.isTempUnlocked) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ZenSoftGreen),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ZenGreen.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ZenGreen)
                                    Text(
                                        text = "Tregua activa: ${formatDuration(tempUnlockTimeRemaining)}",
                                        fontSize = 14.sp,
                                        color = ZenGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Acceso a tus aplicaciones permitidas:",
                                    fontSize = 12.sp,
                                    color = ZenCharcoal
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val allowedApps = lockManager.allowedPackages.toList()
                                if (allowedApps.isEmpty()) {
                                    Text(
                                        text = "No has agregado apps en el menú de configuración.",
                                        fontSize = 11.sp,
                                        color = ZenSage
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        allowedApps.forEach { pkg ->
                                            val pm = context.packageManager
                                            val appName = try {
                                                val info = pm.getApplicationInfo(pkg, 0)
                                                pm.getApplicationLabel(info).toString()
                                            } catch (e: Exception) {
                                                pkg
                                            }

                                            Button(
                                                onClick = {
                                                    val intent = pm.getLaunchIntentForPackage(pkg)
                                                    if (intent != null) {
                                                        context.startActivity(intent)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = appName,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TIEMPO FINALIZADO (Desbloqueo inmediato sin escribir nada)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = ZenWhite),
                        border = BorderStroke(1.dp, ZenGreen.copy(alpha = 0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = ZenGreen)
                                Text(
                                    text = "¡Felicidades! Has completado tu tiempo",
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, color = ZenGreen)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    lockManager.stopLock()
                                    onUnlocked()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ZenGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("🔓 Finalizar y Volver a Inicio", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Botones de acción durante el tiempo de bloqueo
            if (timeRemaining > 0) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Botón 1: "Ya terminé mi actividad" (Frase LARGA 150-200 palabras)
                    OutlinedButton(
                        onClick = { showLongChallengeModal = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ZenOlive),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZenOlive)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.TaskAlt, contentDescription = null)
                            Text("Ya terminé mi actividad", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        }
                    }

                    // Botón 2: "Tregua temporal (5 min)" (2 opciones: Matemáticas o Frase de 100)
                    val canRequestTempUnlock = !lockManager.isTempUnlocked && !lockManager.isCooldownActive
                    
                    Button(
                        onClick = { 
                            tempChallengeType = ""
                            showTempChallengeModal = true 
                        },
                        enabled = canRequestTempUnlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZenCoral,
                            disabledContainerColor = ZenSage.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null)
                            Text(
                                text = if (lockManager.isTempUnlocked) {
                                    "Tregua en curso"
                                } else if (lockManager.isCooldownActive) {
                                    "Cooldown: ${formatDuration(cooldownTimeRemaining)}"
                                } else {
                                    "Tregua temporal (5 min)"
                                },
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Botones de Emergencia (Llamadas y SMS)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ACCESO DE EMERGENCIA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZenSage,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Botón Llamadas de Emergencia
                            OutlinedButton(
                                onClick = {
                                    val dialerIntent = Intent(Intent.ACTION_DIAL).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(dialerIntent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ZenCharcoal)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = ZenGreen, modifier = Modifier.size(18.dp))
                                    Text("Teléfono", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            // Botón Mensajes de Emergencia (SMS)
                            OutlinedButton(
                                onClick = {
                                    val smsPackage = com.antiprocrastinacion.lock.LauncherUtils.getDefaultSmsPackage(context)
                                    val smsIntent = if (smsPackage != null) {
                                        context.packageManager.getLaunchIntentForPackage(smsPackage)
                                    } else {
                                        Intent(Intent.ACTION_MAIN).apply {
                                            addCategory(Intent.CATEGORY_APP_MESSAGING)
                                        }
                                    }
                                    if (smsIntent != null) {
                                        smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(smsIntent)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ZenCharcoal)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Sms, contentDescription = null, tint = ZenOlive, modifier = Modifier.size(18.dp))
                                    Text("Mensajes", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MODAL 1: RETO LARGO (150 - 200 palabras) para "Ya terminé mi actividad"
    if (showLongChallengeModal) {
        AlertDialog(
            onDismissRequest = { showLongChallengeModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (longUserInput == targetLongPhrase) {
                            lockManager.stopLock()
                            showLongChallengeModal = false
                            onUnlocked()
                        }
                    },
                    enabled = longUserInput == targetLongPhrase,
                    colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Desbloquear Ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLongChallengeModal = false }) {
                    Text("Volver al Temporizador", color = ZenSage)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = ZenOlive)
                    Text("Verificación de Finalización", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Escribe el siguiente texto exactamente (con acentos, comas y puntos). Consta de ${countWords(targetLongPhrase)} palabras:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = ZenSage
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CreamBackground),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = buildHighlightedText(longUserInput, targetLongPhrase),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = longUserInput,
                        onValueChange = { longUserInput = it },
                        placeholder = { Text("Empieza a escribir el texto aquí...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = ZenWhite
        )
    }

    // MODAL 2: TREGUA TEMPORAL (Con selector de 2 Opciones: Matemáticas o Frase de 100)
    if (showTempChallengeModal) {
        AlertDialog(
            onDismissRequest = { showTempChallengeModal = false },
            confirmButton = {
                if (tempChallengeType == "math") {
                    Button(
                        onClick = {
                            if (mathUserInput.trim() == mathCorrectAnswer.toString()) {
                                lockManager.startTempUnlock()
                                showTempChallengeModal = false
                            }
                        },
                        enabled = mathUserInput.trim() == mathCorrectAnswer.toString(),
                        colors = ButtonDefaults.buttonColors(containerColor = ZenCoral),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Verificar Respuesta")
                    }
                } else if (tempChallengeType == "phrase") {
                    Button(
                        onClick = {
                            if (mediumPhraseUserInput == mediumPhraseText) {
                                lockManager.startTempUnlock()
                                showTempChallengeModal = false
                            }
                        },
                        enabled = mediumPhraseUserInput == mediumPhraseText,
                        colors = ButtonDefaults.buttonColors(containerColor = ZenCoral),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Obtener 5 min de Tregua")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTempChallengeModal = false }) {
                    Text("Cancelar", color = ZenSage)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = ZenCoral)
                    Text("Desafío de Tregua (5 Minutos)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (tempChallengeType == "") {
                        Text(
                            text = "Elige el tipo de desafío que deseas resolver para obtener 5 minutos de acceso a tus aplicaciones permitidas:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = ZenSage
                        )

                        // Opción 1: Problema Matemático
                        Button(
                            onClick = { prepareMathChallenge() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Calculate, contentDescription = null)
                                Text("Problema Matemático Complejo")
                            }
                        }

                        // Opción 2: Frase de Enfoque (1 de 100)
                        Button(
                            onClick = { preparePhraseChallenge() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ZenCoral),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.FormatQuote, contentDescription = null)
                                Text("Frase de Enfoque (100 frases)")
                            }
                        }
                    } else if (tempChallengeType == "math") {
                        Text(
                            text = "Resuelve la siguiente operación matemática:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = ZenSage
                        )

                        Text(
                            text = mathProblemText,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZenCharcoal
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )

                        OutlinedTextField(
                            value = mathUserInput,
                            onValueChange = { mathUserInput = it },
                            placeholder = { Text("Ingresa el resultado numérico", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        TextButton(
                            onClick = { tempChallengeType = "" },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("← Cambiar tipo de desafío", fontSize = 12.sp, color = ZenSage)
                        }
                    } else if (tempChallengeType == "phrase") {
                        Text(
                            text = "Escribe la siguiente frase (${countWords(mediumPhraseText)} palabras) con acentos y comas:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = ZenSage
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = CreamBackground),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = buildHighlightedText(mediumPhraseUserInput, mediumPhraseText),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = mediumPhraseUserInput,
                            onValueChange = { mediumPhraseUserInput = it },
                            placeholder = { Text("Escribe la frase aquí...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        TextButton(
                            onClick = { tempChallengeType = "" },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("← Cambiar tipo de desafío", fontSize = 12.sp, color = ZenSage)
                        }
                    }
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = ZenWhite
        )
    }
}
