package com.antiprocrastinacion.lock.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiprocrastinacion.lock.AppInfo
import com.antiprocrastinacion.lock.LauncherUtils
import com.antiprocrastinacion.lock.LockManager
import com.antiprocrastinacion.lock.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    lockManager: LockManager,
    onLockStarted: () -> Unit
) {
    val context = LocalContext.current
    
    // Selectores de tiempo personalizados (horas y minutos)
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(10) } // 10 minutos por defecto
    
    // Estados de permisos
    var hasUsageStats by remember { mutableStateOf(LauncherUtils.hasUsageStatsPermission(context)) }
    
    // Lista de aplicaciones instaladas
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf(lockManager.allowedPackages) }
    var showAppSelector by remember { mutableStateOf(true) }
    
    // Mensaje de advertencia si intenta seleccionar más de 4
    var showAppLimitWarning by remember { mutableStateOf(false) }

    // Carga de aplicaciones instaladas en hilo de background
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            installedApps = LauncherUtils.getInstalledApps(context)
        }
    }

    // Comprobación de estado al enfocar la pantalla
    LaunchedEffect(Unit) {
        hasUsageStats = LauncherUtils.hasUsageStatsPermission(context)
        if (selectedPackages.size > 4) {
            val truncated = selectedPackages.take(4).toSet()
            selectedPackages = truncated
            lockManager.allowedPackages = truncated
        }
    }

    Scaffold(
        containerColor = CreamBackground // Fondo crema Zen suave
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera Minimalista
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = com.antiprocrastinacion.lock.R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.dp, ZenSage.copy(alpha = 0.4f)), CircleShape)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "antiprocrastinación",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = ZenCharcoal,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enfoque profundo y autodisciplina",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZenSage
                )
            }

            // Contenedor Central Con Scroll
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta de estado de Acceso a Uso
                if (!hasUsageStats) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF5F2)),
                        border = BorderStroke(1.dp, ZenCoral.copy(alpha = 0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = ZenCoral
                                )
                                Text(
                                    text = "Permiso Requerido",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ZenCoral
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "Se requiere el permiso de 'Acceso a Uso' para poder monitorizar las aplicaciones permitidas durante el bloqueo temporal.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = ZenCharcoal,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { LauncherUtils.openUsageStatsSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = ZenCoral),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Conceder Acceso a Uso", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }

                // Tarjeta opcional de Máxima Seguridad (Launcher del Sistema)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F8)),
                    border = BorderStroke(1.dp, ZenOlive.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = ZenOlive)
                            Text(
                                text = "Reforzar Seguridad Antibloqueo",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZenCharcoal)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Establece AntiProcrastinación como Inicio/Launcher predeterminado para evitar que los botones de Inicio o Recientes permitan abrir menús.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ZenSage,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { LauncherUtils.openHomeSettings(context) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Establecer como Inicio Predeterminado", fontSize = 11.sp, color = ZenOlive)
                        }
                    }
                }

                // Selector de tiempo estilo Reloj (Horas y Minutos)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "TEMPORIZADOR DE ENFOQUE",
                        style = MaterialTheme.typography.labelLarge,
                        color = ZenSage,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ZenWhite),
                        border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Columna Horas
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("HORAS", fontSize = 11.sp, color = ZenSage, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (hours > 0) hours-- },
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = ZenCharcoal)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Bajar hora")
                                    }
                                    Text(
                                        text = String.format("%02d", hours),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Light,
                                        color = ZenCharcoal,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(
                                        onClick = { if (hours < 23) hours++ },
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = ZenCharcoal)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Subir hora")
                                    }
                                }
                            }

                            Text(":", fontSize = 32.sp, color = ZenSage, fontWeight = FontWeight.ExtraLight)

                            // Columna Minutos
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MINUTOS", fontSize = 11.sp, color = ZenSage, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { 
                                            if (minutes > 0) {
                                                minutes-- 
                                            } else if (hours > 0) {
                                                minutes = 59
                                                hours--
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = ZenCharcoal)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Bajar minuto")
                                    }
                                    Text(
                                        text = String.format("%02d", minutes),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Light,
                                        color = ZenCharcoal,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(
                                        onClick = { 
                                            if (minutes < 59) {
                                                minutes++ 
                                            } else if (hours < 23) {
                                                minutes = 0
                                                hours++
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = ZenCharcoal)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Subir minuto")
                                    }
                                }
                            }
                        }
                    }
                }

                // Selección de Apps Permitidas (Máximo 4)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAppSelector = !showAppSelector }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "APLICACIONES PERMITIDAS (${selectedPackages.size} de 4)",
                                style = MaterialTheme.typography.labelLarge,
                                color = ZenSage
                            )
                            if (showAppLimitWarning) {
                                Text(
                                    text = "Máximo 4 aplicaciones adicionales permitidas",
                                    color = ZenCoral,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showAppSelector) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = ZenSage
                        )
                    }

                    if (showAppSelector) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ZenWhite),
                            border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            if (!hasUsageStats) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize().padding(16.dp)
                                ) {
                                    Text(
                                        text = "Concede 'Acceso a Uso' para seleccionar aplicaciones.",
                                        color = ZenCoral,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else if (installedApps.isEmpty()) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    CircularProgressIndicator(color = ZenOlive)
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(installedApps) { app ->
                                        val isChecked = selectedPackages.contains(app.packageName)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    val updated = selectedPackages.toMutableSet()
                                                    if (isChecked) {
                                                        updated.remove(app.packageName)
                                                        selectedPackages = updated
                                                        lockManager.allowedPackages = updated
                                                        showAppLimitWarning = false
                                                    } else {
                                                        if (selectedPackages.size < 4) {
                                                            updated.add(app.packageName)
                                                            selectedPackages = updated
                                                            lockManager.allowedPackages = updated
                                                            showAppLimitWarning = false
                                                        } else {
                                                            showAppLimitWarning = true
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = null,
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = ZenOlive,
                                                    uncheckedColor = ZenSage.copy(alpha = 0.5f)
                                                ),
                                                enabled = isChecked || selectedPackages.size < 4
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = app.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isChecked || selectedPackages.size < 4) ZenCharcoal else ZenSage
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sincronización de Cuenta con Extensión de Chrome
                var syncKeyText by remember { mutableStateOf(lockManager.userSyncKey) }
                
                Column {
                    Text(
                        text = "VINCULACIÓN DE CUENTA CON CHROME EXTENSION",
                        style = MaterialTheme.typography.labelLarge,
                        color = ZenSage,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ZenWhite),
                        border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Ingresa tu email o código PIN de vinculación para sincronizar este teléfono con la Extensión de Chrome de tu PC:",
                                fontSize = 11.sp,
                                color = ZenCharcoal
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = syncKeyText,
                                    onValueChange = { syncKeyText = it },
                                    placeholder = { Text("ej. usuario@email.com", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Button(
                                    onClick = {
                                        val clean = syncKeyText.trim().lowercase().replace(" ", "_")
                                        if (clean.isNotEmpty()) {
                                            lockManager.userSyncKey = clean
                                            syncKeyText = clean
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Guardar", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Frase aleatoria de enfoque (Selección automática de la colección de 55+ frases)
                Column {
                    Text(
                        text = "FRASE PARA DESBLOQUEO ANTICIPADO",
                        style = MaterialTheme.typography.labelLarge,
                        color = ZenSage,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ZenWhite),
                        border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ZenOlive
                            )
                            Text(
                                text = "Al iniciar el bloqueo se elegirá automáticamente una frase motivacional aleatoria de la colección (55+ frases con acentos y puntuación completa) que deberás escribir si deseas desbloquear.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = ZenCharcoal
                            )
                        }
                    }
                }
            }

            // Botón de Bloqueo Final
            val totalDurationMinutes = hours * 60 + minutes
            val isTimeSelectionValid = totalDurationMinutes > 0
            
            Button(
                onClick = {
                    if (isTimeSelectionValid) {
                        lockManager.startLock(totalDurationMinutes)
                        onLockStarted()
                    }
                },
                enabled = isTimeSelectionValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZenOlive,
                    disabledContainerColor = ZenSage.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    Text(
                        text = if (!isTimeSelectionValid) {
                            "Elige un tiempo"
                        } else {
                            val timeLabel = if (hours > 0) "$hours h $minutes min" else "$minutes min"
                            "Iniciar Enfoque de $timeLabel"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
