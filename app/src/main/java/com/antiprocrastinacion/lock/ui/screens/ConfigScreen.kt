package com.antiprocrastinacion.lock.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.antiprocrastinacion.lock.AppInfo
import com.antiprocrastinacion.lock.LauncherUtils
import com.antiprocrastinacion.lock.LockManager
import com.antiprocrastinacion.lock.ui.theme.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    lockManager: LockManager,
    onLockStarted: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    darkTheme: Boolean = false,
    onDarkThemeChange: (Boolean) -> Unit = {},
    configVersion: Int = 0,
    onBackToLauncher: (() -> Unit)? = null,
    onAccentChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    
    // Selectores de tiempo personalizados (horas y minutos)
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(10) } // 10 minutos por defecto
    
    // V24 (Propuesta 1): Ajustes Pomodoro sincronizados
    var pomodoroEnabled by remember { mutableStateOf(false) }
    var pomodoroWorkMinutes by remember { mutableIntStateOf(25) }
    var pomodoroRestMinutes by remember { mutableIntStateOf(5) }
    var pomodoroRestCount by remember { mutableIntStateOf(1) }
    // Cargar desde prefs al iniciar
    LaunchedEffect(Unit) {
        pomodoroEnabled = lockManager.pomodoroEnabled
        pomodoroWorkMinutes = lockManager.pomodoroWorkMinutes
        pomodoroRestMinutes = lockManager.pomodoroRestMinutes
        pomodoroRestCount = lockManager.pomodoroRestCount
    }
    
    // Estados de permisos
    var hasUsageStats by remember { mutableStateOf(LauncherUtils.hasUsageStatsPermission(context)) }
    
    // Lista de aplicaciones instaladas
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf(lockManager.allowedPackages) }
    var showAppSelector by remember { mutableStateOf(true) }
    
    // Mensaje de advertencia si intenta seleccionar más de 4
    var showAppLimitWarning by remember { mutableStateOf(false) }

    // V20: estado local del toggle de bloqueo cruzado (para recomponer la UI)
    var crossDeviceLockEnabled by remember { mutableStateOf(lockManager.crossDeviceLockEnabled) }

    // V20.2: si la configuración cambió desde la extensión (modo oscuro/bloqueo cruzado),
    // refrescar el estado local para reflejar el valor remoto en la UI
    LaunchedEffect(configVersion) {
        crossDeviceLockEnabled = lockManager.crossDeviceLockEnabled
        // V24 (Propuesta 1): refrescar ajustes Pomodoro llegados desde la extensión
        pomodoroEnabled = lockManager.pomodoroEnabled
        pomodoroWorkMinutes = lockManager.pomodoroWorkMinutes
        pomodoroRestMinutes = lockManager.pomodoroRestMinutes
        pomodoroRestCount = lockManager.pomodoroRestCount
    }

    // V24 (Propuesta 5): proteger TODOS los ajustes durante sesión activa (local o remota)
    // usando biometría o PIN del dispositivo.
    fun requireBiometricAuth(onSuccess: () -> Unit) {
        val fragmentActivity = context as? androidx.fragment.app.FragmentActivity ?: return
        val executor: Executor = ContextCompat.getMainExecutor(fragmentActivity)
        val prompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    android.util.Log.w("ZEN_SECURITY", "Autenticación cancelada/fallida: $errString")
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirmar seguridad")
            .setSubtitle("Verifica tu identidad (biometría o PIN) para modificar ajustes durante el enfoque")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    // Helper: ejecutar acción con biometría si hay sesión activa, sino directa
    fun executeWithAuthIfNeeded(action: () -> Unit) {
        if (lockManager.isFocusSessionActive) {
            requireBiometricAuth(action)
        } else {
            action()
        }
    }

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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            var showSyncModal by remember { mutableStateOf(false) }
            var showSettingsModal by remember { mutableStateOf(false) }
            var showAccountMenu by remember { mutableStateOf(false) }
            val syncTestScope = rememberCoroutineScope()
            var isTestingSync by remember { mutableStateOf(false) }
            var syncTestResult by remember { mutableStateOf<String?>(null) }

            // Dialogo Modal de Sincronización de Cuenta Estilo Web Pro
            if (showSyncModal) {
                Dialog(onDismissRequest = { showSyncModal = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = ZenWhite),
                        border = BorderStroke(1.dp, ZenOlive.copy(alpha = 0.3f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(ZenOlive.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = ZenOlive,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = "Mi Cuenta",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ZenCharcoal
                                    )
                                }
                                IconButton(onClick = { showSyncModal = false }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = ZenSage)
                                }
                            }

                            HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                            // CÓDIGO ÚNICO DEL TELÉFONO
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CreamBackground, RoundedCornerShape(16.dp))
                                    .border(1.dp, ZenOlive.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "🔑 CÓDIGO ÚNICO DE ESTE TELÉFONO:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ZenSage,
                                    letterSpacing = 0.5.sp
                                )
                                SelectionContainer {
                                    Text(
                                        text = lockManager.devicePin,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ZenOlive,
                                        letterSpacing = 2.sp
                                    )
                                }
                                Text(
                                    text = "Ingresa este código en tu Extensión de Chrome para conectar este teléfono al instante.",
                                    fontSize = 11.sp,
                                    color = ZenCharcoal,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // BOTÓN DE INICIO DE SESIÓN CON GOOGLE
                            val currentEmail = lockManager.googleUserEmail
                            // V24 (Bug 4): solo se considera "sesión activa" con cuenta de
                            // Google real (la auth anónima es solo respaldo para RTDB)
                            val isSignedIn = lockManager.isGoogleSignedIn
                            
                            Button(
                                onClick = {
                                    if (!isSignedIn) {
                                        onGoogleSignIn()
                                        showSyncModal = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSignedIn) Color(0xFF34A853) else Color(0xFF4285F4)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("G", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    Text(
                                        text = if (isSignedIn) currentEmail else "Iniciar Sesión con Google",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            
                            // BOTÓN DE PRUEBA DE CONEXIÓN (LAN + NUBE + EXTENSIÓN)
                            Button(
                                onClick = {
                                    isTestingSync = true
                                    syncTestResult = null
                                    syncTestScope.launch {
                                        val lanActive = lockManager.lanServer.isLanActive
                                        val heartbeatDone = CompletableDeferred<Boolean>()
                                        lockManager.pushDeviceHeartbeatToFirebase { extConnected ->
                                            heartbeatDone.complete(extConnected)
                                        }
                                        // Timeout de 8s: si Firebase no responde, no dejar el botón colgado
                                        val extConnected = withTimeoutOrNull(8000) {
                                            heartbeatDone.await()
                                        }
                                        isTestingSync = false
                                        syncTestResult = buildString {
                                            append(if (lanActive) "✅ LAN local: ACTIVA (Wi-Fi)" else "❌ LAN local: sin dispositivo")
                                            append("\n")
                                            if (extConnected == null) {
                                                append("⚠️ Nube Firebase: sin respuesta en 8s (revisa el internet del teléfono)")
                                            } else {
                                                append(if (extConnected) "✅ Extensión Chrome: CONECTADA" else "❌ Extensión Chrome: sin ping reciente")
                                            }
                                        }
                                    }
                                },
                                enabled = !isTestingSync,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ZenSage,
                                    disabledContainerColor = ZenSage.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Text(
                                    text = if (isTestingSync) "Probando conexión..." else "🔍 Probar Conexión",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            if (syncTestResult != null) {
                                Text(
                                    text = syncTestResult.orEmpty(),
                                    fontSize = 11.sp,
                                    color = ZenCharcoal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CreamBackground, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                )
                            }

                            // VINCULACIÓN DE CLAVE O CORREO
                            var manualKeyInput by remember { mutableStateOf(lockManager.userSyncKey) }
                            OutlinedTextField(
                                value = manualKeyInput,
                                onValueChange = { manualKeyInput = it },
                                label = { Text("Clave o Correo de Sincronización", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                trailingIcon = {
                                    Button(
                                        onClick = {
                                            if (manualKeyInput.isNotBlank()) {
                                                lockManager.userSyncKey = manualKeyInput.trim()
                                                lockManager.pushDeviceHeartbeatToFirebase()
                                                lockManager.startExtensionPingListener()
                                                lockManager.startLockStateListener()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ZenOlive)
                                    ) {
                                        Text("Guardar", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            )

                            // VINCULACIÓN: mostrar UID de Firebase si está autenticado
                            if (isSignedIn) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "✅ SESIÓN ACTIVA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF22C55E)
                                    )
                                    Text(
                                        text = "UID: ${lockManager.firebaseUid ?: ""}",
                                        fontSize = 10.sp,
                                        color = ZenSage
                                    )
                                    Text(
                                        text = "La extensión de Chrome debe iniciar sesión con la misma cuenta de Google para conectarse.",
                                        fontSize = 11.sp,
                                        color = ZenCharcoal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                        }
                    }
                }
            }

            var showNotesModal by remember { mutableStateOf(false) }

            // Cabecera Minimalista
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                // Botón de NOTAS ZEN en la esquina superior izquierda
                Box(
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Surface(
                        onClick = { showNotesModal = true },
                        shape = RoundedCornerShape(16.dp),
                        color = ZenSoftBlue,
                        border = BorderStroke(1.dp, ZenOlive.copy(alpha = 0.3f)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "📝", fontSize = 14.sp)
                            Text(
                                text = "Notas",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZenOlive
                            )
                        }
                    }
                }

                // Dialogo Modal de Notas Zen Sincronizadas
                if (showNotesModal) {
                    ZenNotesModal(
                        lockManager = lockManager,
                        onDismiss = { showNotesModal = false }
                    )
                }

                // Dialogo Modal de Ajustes (Modo Oscuro & Bloqueo Cruzado)
                if (showSettingsModal) {
                    Dialog(onDismissRequest = { showSettingsModal = false }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = ZenWhite),
                            border = BorderStroke(1.dp, ZenOlive.copy(alpha = 0.3f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(ZenOlive.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = ZenOlive,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Ajustes",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ZenCharcoal
                                            )
                                            Text(
                                                text = "Personalización de la aplicación",
                                                fontSize = 11.sp,
                                                color = ZenSage
                                            )
                                        }
                                    }
                                    IconButton(onClick = { showSettingsModal = false }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = ZenSage)
                                    }
                                }

                                HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                                // Toggle Modo Oscuro
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = ZenOlive)
                                        Column {
                                            Text(
                                                text = "Modo Oscuro",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ZenCharcoal)
                                            )
                                            Text(
                                                text = if (darkTheme) "Oscuro" else "Claro",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = ZenSage
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = darkTheme,
                                        onCheckedChange = { newValue ->
                                            executeWithAuthIfNeeded {
                                                onDarkThemeChange(newValue)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = ZenOlive,
                                            uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                                            checkedThumbColor = ZenWhite
                                        )
                                    )
                                }

                                HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                                // Toggle Bloqueo Cruzado
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, tint = ZenOlive)
                                        Column {
                                            Text(
                                                text = "Bloqueo Cruzado",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ZenCharcoal)
                                            )
                                            Text(
                                                text = "Sincroniza el bloqueo en tiempo real entre PC y teléfono.",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = ZenSage
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = crossDeviceLockEnabled,
                                        onCheckedChange = { newValue ->
                                            requireBiometricAuth {
                                                crossDeviceLockEnabled = newValue
                                                lockManager.crossDeviceLockEnabled = newValue
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = ZenOlive,
                                            uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                                            checkedThumbColor = ZenWhite
                                        )
                                    )
                                }

                                HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                                // V24 (Propuesta 1): Pomodoro sincronizado (trabajo/descanso)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Timer, contentDescription = null, tint = ZenOlive)
                                        Column {
                                            Text(
                                                text = "Descansos",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ZenCharcoal)
                                            )
                                            Text(
                                                text = "Configura ciclos de trabajo y descansos.",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = ZenSage
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = pomodoroEnabled,
                                        onCheckedChange = { newValue ->
                                            executeWithAuthIfNeeded {
                                                pomodoroEnabled = newValue
                                                lockManager.pomodoroEnabled = newValue
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = ZenOlive,
                                            uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                                            checkedThumbColor = ZenWhite
                                        )
                                    )
                                }

                                // Selectores de duración Pomodoro (trabajo y descanso) se configuran
                                // en la pantalla de inicio del enfoque (no aquí).
                            }
                        }
                    }
                }

                // Menú desplegable de Perfil y Ajustes en la esquina superior derecha
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Surface(
                        onClick = { showAccountMenu = true },
                        shape = CircleShape,
                        color = ZenOlive.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ZenOlive.copy(alpha = 0.3f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Menú de Perfil y Ajustes",
                                tint = ZenOlive,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = { showAccountMenu = false },
                        modifier = Modifier
                            .background(ZenWhite)
                            .border(1.dp, ZenSage.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = ZenOlive, modifier = Modifier.size(18.dp))
                                    Text("Mi Cuenta", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZenCharcoal)
                                }
                            },
                            onClick = {
                                showAccountMenu = false
                                showSyncModal = true
                            }
                        )

                        HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = ZenOlive, modifier = Modifier.size(18.dp))
                                    Text("Ajustes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZenCharcoal)
                                }
                            },
                            onClick = {
                                showAccountMenu = false
                                showSettingsModal = true
                            }
                        )
                    }
                }

                // V25: volver al launcher desde Ajustes
                if (onBackToLauncher != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TextButton(onClick = { onBackToLauncher() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver al launcher",
                                tint = ZenSage,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Launcher", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZenSage)
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
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
                        colors = CardDefaults.cardColors(containerColor = ZenSoftCoral),
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
                    colors = CardDefaults.cardColors(containerColor = ZenSoftBlue),
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

                // V26: Colores del sistema (presets de acento que cambian TODO)
                val currentAccent = com.antiprocrastinacion.lock.ui.theme.ZenTheme.accent
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = ZenOlive)
                            Column {
                                Text(
                                    text = "COLORES DEL SISTEMA",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZenOlive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "El acento se aplica a todo: launcher, notas, organizador y modo enfoque.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = ZenSage
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            com.antiprocrastinacion.lock.ui.theme.AccentPresets.all.forEach { preset ->
                                val selected = preset.key == currentAccent.key
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        executeWithAuthIfNeeded {
                                            lockManager.accentPresetKey = preset.key
                                            com.antiprocrastinacion.lock.ui.theme.ZenTheme.accent = preset
                                            onAccentChange(preset.key)
                                        }
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (selected) 44.dp else 40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (darkTheme) preset.dark else preset.light,
                                                CircleShape
                                            )
                                            .border(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) ZenCharcoal else ZenSage.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = preset.name,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) ZenCharcoal else ZenSage
                                    )
                                }
                            }
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

                // V24: Configuración Pomodoro en la pantalla de inicio del enfoque
                if (pomodoroEnabled) {
                    val focusTotalMinutes = hours * 60 + minutes
                    val (pomodoroMaxRest, pomodoroMaxRestCount) = lockManager.computePomodoroLimits(focusTotalMinutes, pomodoroRestCount, pomodoroRestMinutes)
                    var showPomodoroConfig by remember { mutableStateOf(true) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ZenSoftGreen),
                        border = BorderStroke(1.dp, ZenGreen.copy(alpha = 0.35f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPomodoroConfig = !showPomodoroConfig },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = ZenGreen)
                                    Column {
                                        Text(
                                            text = "DESCANSOS",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ZenGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Comparte ciclos de trabajo y descanso con la extensión.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = ZenSage
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (showPomodoroConfig) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = ZenGreen
                                )
                            }

                            if (showPomodoroConfig) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(10.dp))

                                if (focusTotalMinutes < 10) {
                                    Text(
                                        text = "Los descansos solo están disponibles para enfoques de 10 minutos o más. Aumenta el tiempo para configurar los descansos.",
                                        fontSize = 12.sp,
                                        color = ZenCoral,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    // V24.1 (T4): steppers compactos con el VALOR ACTUAL destacado
                                    // (estilo de la extensión): "Descansos: N" / "Descanso (máx M): X min".
                                    HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                                    // Número de descansos
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Descansos: ",
                                                fontSize = 13.sp,
                                                color = ZenCharcoal,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "$pomodoroRestCount",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ZenGreen
                                            )
                                            Text(
                                                text = " ${if (pomodoroRestCount == 1) "descanso" else "descansos"}",
                                                fontSize = 13.sp,
                                                color = ZenCharcoal,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            IconButton(
                                                onClick = {
                                                    pomodoroRestCount = (pomodoroRestCount - 1).coerceAtLeast(1)
                                                    lockManager.pomodoroRestCount = pomodoroRestCount
                                                },
                                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                                enabled = pomodoroRestCount > 1
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Reducir descansos", tint = ZenGreen)
                                            }
                                            IconButton(
                                                onClick = {
                                                    pomodoroRestCount = (pomodoroRestCount + 1).coerceAtMost(pomodoroMaxRestCount)
                                                    lockManager.pomodoroRestCount = pomodoroRestCount
                                                },
                                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                                enabled = pomodoroRestCount < pomodoroMaxRestCount
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Aumentar descansos", tint = ZenGreen)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                                    // Duración de cada descanso
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Descanso (máx $pomodoroMaxRest): ",
                                                fontSize = 13.sp,
                                                color = ZenCharcoal,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "$pomodoroRestMinutes min",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ZenGreen
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            IconButton(
                                                onClick = {
                                                    pomodoroRestMinutes = (pomodoroRestMinutes - 1).coerceAtLeast(1)
                                                    lockManager.pomodoroRestMinutes = pomodoroRestMinutes
                                                },
                                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                                enabled = pomodoroRestMinutes > 1
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Reducir descanso", tint = ZenGreen)
                                            }
                                            IconButton(
                                                onClick = {
                                                    pomodoroRestMinutes = (pomodoroRestMinutes + 1).coerceAtMost(pomodoroMaxRest)
                                                    lockManager.pomodoroRestMinutes = pomodoroRestMinutes
                                                },
                                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                                enabled = pomodoroRestMinutes < pomodoroMaxRest
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Aumentar descanso", tint = ZenGreen)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Vista previa del reparto de bloques
                                    val workPerBlockMin = (focusTotalMinutes - pomodoroRestCount * pomodoroRestMinutes) / (pomodoroRestCount + 1)
                                    Text(
                                        text = "Tu enfoque de $focusTotalMinutes min se repartirá en ${pomodoroRestCount + 1} bloques de trabajo de ~${workPerBlockMin.coerceAtLeast(1)} min con $pomodoroRestCount descansos de $pomodoroRestMinutes min.",
                                        fontSize = 11.sp,
                                        color = ZenSage,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (workPerBlockMin < 10) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Con esta configuración los bloques de trabajo quedan muy cortos. Reduce los descansos o su duración.",
                                            fontSize = 11.sp,
                                            color = ZenCoral,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
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
                        // V24.1 (T3): altura con límite (heightIn) para que la lista de apps
                        // scrollee por sí sola dentro del límite y el padre scrollee aparte.
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
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
                                text = "Al iniciar el bloqueo se elegirá automáticamente una frase motivacional aleatoria de la colección (150 frases de reflexión y motivación) que podrás revisar durante tu sesión de enfoque.",
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

            Text(
                text = "Versión v24.0.0",
                fontSize = 11.sp,
                color = ZenSage.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
