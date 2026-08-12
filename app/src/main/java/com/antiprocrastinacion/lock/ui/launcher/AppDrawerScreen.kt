package com.antiprocrastinacion.lock.ui.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiprocrastinacion.lock.AppInfo
import com.antiprocrastinacion.lock.LauncherUtils
import com.antiprocrastinacion.lock.LockManager
import com.antiprocrastinacion.lock.PhraseGenerator
import com.antiprocrastinacion.lock.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * V28: Cajón de aplicaciones del launcher.
 * Muestra TODAS las apps de usuario. Cuando ningún modo está activo, las apps
 * distractoras se ven NORMALES y al tocarlas aparece un aviso personalizado
 * con opciones "Abrir" o "Cancelar" (nadie está obligado). Solo en el Modo
 * Escuela/Trabajo las apps distractoras se atenúan y quedan bloqueadas
 * (WhatsApp con límite y videollamadas libres).
 */
@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    lockManager: LockManager? = null,
    workModeActive: Boolean = false,
    noSocialActive: Boolean = false
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var restrictedApp by remember { mutableStateOf<AppInfo?>(null) }
    var nudgeApp by remember { mutableStateOf<AppInfo?>(null) }
    var nudgeMessage by remember { mutableStateOf("") }
    var lastNudgeMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = LauncherUtils.getLauncherApps(context)
            apps = loaded
            loaded.forEach { LauncherUtils.getAppIconBitmapCached(context, it.packageName) }
        }
    }

    Scaffold(
        containerColor = CreamBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp)) // Dynamic Island

            // Cabecera del cajón
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = ZenCharcoal
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Aplicaciones",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenCharcoal
                    )
                    Text(
                        text = when {
                            noSocialActive ->
                                "Modo Sin Redes activo: solo WhatsApp (30 min) y llamadas"
                            workModeActive ->
                                "Modo Escuela activo: WhatsApp y videollamadas disponibles"
                            else ->
                                "Al tocar una app que distrae verás un aviso; tú decides si abrirla"
                        },
                        fontSize = 11.sp,
                        color = ZenSage
                    )
                }
            }

            // Buscador (estilo tarjeta Zen)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar aplicación...", fontSize = 13.sp, color = ZenSage) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ZenSage) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZenOlive,
                    unfocusedBorderColor = ZenBorderLight,
                    focusedContainerColor = ZenWhite,
                    unfocusedContainerColor = ZenWhite,
                    cursorColor = ZenOlive,
                    focusedTextColor = ZenCharcoal,
                    unfocusedTextColor = ZenCharcoal
                )
            )

            val filtered = remember(apps, query) {
                if (query.isBlank()) apps
                else apps.filter { it.label.contains(query, ignoreCase = true) }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay aplicaciones que coincidan.",
                        fontSize = 13.sp,
                        color = ZenSage,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        // Bloqueo según el modo activo:
                        // - Escuela/Trabajo: distractoras atenuadas (WhatsApp/videollamadas libres).
                        // - Sin Redes: RRSS/juegos/navegadores bloqueados, WhatsApp con límite.
                        val workBlocked = app.isDistraction &&
                            workModeActive &&
                            !LauncherUtils.isWorkModeAppAllowed(app.packageName)
                        val noSocialBlocked = lockManager?.isNoSocialPackageBlockedNow(app.packageName) == true
                        val blocked = workBlocked || noSocialBlocked
                        AppLogo(
                            app = app,
                            isBlocked = blocked,
                            onClick = {
                                when {
                                    blocked -> restrictedApp = app
                                    workModeActive && LauncherUtils.isWhatsApp(app.packageName) -> {
                                        // WhatsApp exento pero con límite de minutos seguidos
                                        val lm = lockManager
                                        if (lm != null && !lm.isWorkWhatsAppAvailable()) {
                                            restrictedApp = app
                                        } else {
                                            lm?.startWorkWhatsAppSessionIfNeeded()
                                            LauncherUtils.launchApp(context, app.packageName)
                                        }
                                    }
                                    app.isDistraction -> {
                                        // Sin modo activo: aviso personalizado, el usuario decide
                                        nudgeMessage = PhraseGenerator.drawerNudge(app.label, lastNudgeMessage)
                                        lastNudgeMessage = nudgeMessage
                                        nudgeApp = app
                                    }
                                    else -> LauncherUtils.launchApp(context, app.packageName)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Diálogo Zen cuando se intenta abrir una app restringida
    restrictedApp?.let { app ->
        AlertDialog(
            onDismissRequest = { restrictedApp = null },
            containerColor = ZenWhite,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = ZenCoral,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "App restringida",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenCharcoal
                    )
                }
            },
            text = {
                val isWhatsAppTimeout = workModeActive && LauncherUtils.isWhatsApp(app.packageName)
                val isNoSocialBlock = lockManager?.isNoSocialPackageBlockedNow(app.packageName) == true
                Text(
                    text = when {
                        isWhatsAppTimeout ->
                            "Agotaste el tiempo de WhatsApp en el Modo Escuela/Trabajo.\n\nVuelve a tus clases o trabajo; podrás usarla de nuevo en el próximo bloque."
                        isNoSocialBlock ->
                            "${app.label} no está disponible durante el Modo Sin Redes.\n\nVuelve cuando termine, o pide una tregua de 5 minutos desde Modos."
                        else ->
                            "${app.label} está atenuada porque distrae.\n\n¿De verdad la necesitas ahora o solo buscas perder el tiempo?"
                    },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = ZenSage
                )
            },
            confirmButton = {
                Button(
                    onClick = { restrictedApp = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Entendido", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Diálogo de aviso personalizado al tocar una app distractora sin modo activo
    nudgeApp?.let { app ->
        AlertDialog(
            onDismissRequest = { nudgeApp = null },
            containerColor = ZenWhite,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = ZenOlive,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "¿Abrir ${app.label}?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZenCharcoal
                    )
                }
            },
            text = {
                Text(
                    text = nudgeMessage,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = ZenSage
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { nudgeApp = null },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ZenBorderLight)
                    ) {
                        Text("Cancelar", fontSize = 13.sp, color = ZenCharcoal)
                    }
                    Button(
                        onClick = {
                            nudgeApp = null
                            LauncherUtils.launchApp(context, app.packageName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Abrir", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
}

/** Logo de la app. Si `isBlocked` se muestra en grises, atenuado y con candado. */
@Composable
private fun AppLogo(
    app: AppInfo,
    isBlocked: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val bitmap = remember(app.packageName) {
        LauncherUtils.getAppIconBitmapCached(context, app.packageName)
    }

    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ZenWhite)
            .border(
                width = 1.dp,
                color = if (isBlocked) ZenSage.copy(alpha = 0.35f) else ZenBorderLight,
                shape = RoundedCornerShape(16.dp)
            )
            .alpha(if (isBlocked) 0.55f else 1f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                colorFilter = if (isBlocked) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else {
                    null
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isBlocked) ZenSage.copy(alpha = 0.18f) else ZenSoftBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.label.take(1).uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBlocked) ZenSage else ZenOlive
                )
            }
        }

        // Candado de restricción
        if (isBlocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(ZenCoral),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}
