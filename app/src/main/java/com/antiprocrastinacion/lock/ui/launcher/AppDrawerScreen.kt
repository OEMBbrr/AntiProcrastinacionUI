package com.antiprocrastinacion.lock.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiprocrastinacion.lock.AppInfo
import com.antiprocrastinacion.lock.LauncherUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * V25: Cajón de aplicaciones del nuevo launcher.
 * Muestra SOLO los logos de las apps de salud/utilitarias en una cuadrícula
 * con scroll vertical. Sin etiquetas de texto: solo iconos.
 */
@Composable
fun AppDrawerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = LauncherUtils.getHealthUtilityApps(context)
            apps = loaded
            // V26: precargar TODOS los iconos en un solo hilo de IO para que
            // el scroll del cajón sea fluido (sin decodificaciones por item).
            loaded.forEach { LauncherUtils.getAppIconBitmapCached(context, it.packageName) }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LchBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                        tint = LchText
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Aplicaciones",
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = LchText
                    )
                    Text(
                        text = "Salud y utilidades sin ruido",
                        fontSize = 11.sp,
                        color = LchMuted
                    )
                }
            }

            // Buscador
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar aplicación...", fontSize = 13.sp, color = LchMuted) },
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
                        text = "No hay aplicaciones que coincidan.\nSolo se muestran apps de salud y utilidades.",
                        fontSize = 13.sp,
                        color = LchMuted,
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
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppLogo(
                            app = app,
                            onClick = { LauncherUtils.launchApp(context, app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

/** Logo de la app en un círculo, sin etiqueta. */
@Composable
private fun AppLogo(
    app: AppInfo,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // V26: icono desde caché en memoria -> sin LaunchedEffect por item,
    // la cuadrícula scrollea fluida sin "golpes".
    val bitmap = remember(app.packageName) {
        LauncherUtils.getAppIconBitmapCached(context, app.packageName)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(LchSurface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(LchAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.label.take(1).uppercase(),
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = LchAccent
                    )
                }
            }
        }
    }
}
