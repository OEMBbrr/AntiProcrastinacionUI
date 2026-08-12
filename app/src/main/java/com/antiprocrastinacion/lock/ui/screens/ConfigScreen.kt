package com.antiprocrastinacion.lock.ui.screens

import android.content.pm.PackageManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.antiprocrastinacion.lock.ZenNotificationListenerService
import com.antiprocrastinacion.lock.ui.theme.*
import kotlinx.coroutines.*
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
    onAccentChange: (String) -> Unit = {},
    paseoActive: Boolean = false,
    onTogglePaseo: () -> Unit = {}
) {
    val context = LocalContext.current

    // V28: versión REAL del build (vía PackageManager), no hardcodeada.
    val appVersionName = remember {
        try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "26.0.0"
        } catch (e: Exception) {
            "26.0.0"
        }
    }

    // Mi Cuenta / Sincronización
    val syncTestScope = rememberCoroutineScope()
    var isTestingSync by remember { mutableStateOf(false) }
    var syncTestResult by remember { mutableStateOf<String?>(null) }
    var manualKeyInput by remember { mutableStateOf(lockManager.userSyncKey) }

    // Estados de permisos
    var hasUsageStats by remember { mutableStateOf(LauncherUtils.hasUsageStatsPermission(context)) }

    // Lista de aplicaciones instaladas
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf(lockManager.allowedPackages) }
    var showAppSelector by remember { mutableStateOf(true) }

    // Mensaje de advertencia si intenta seleccionar más de 4
    var showAppLimitWarning by remember { mutableStateOf(false) }

    // V20: estado local del toggle de bloqueo cruzado
    var crossDeviceLockEnabled by remember { mutableStateOf(lockManager.crossDeviceLockEnabled) }

    // V28: Modo Escuela/Trabajo + horarios
    var workModeEnabled by remember { mutableStateOf(lockManager.workModeEnabled) }
    var workWhatsAppMinutes by remember { mutableIntStateOf(lockManager.workWhatsAppMinutes) }
    var scheduleVersion by remember { mutableIntStateOf(0) }
    var showWorkSchedule by remember { mutableStateOf(true) }

    // V28: configuración de los modos (Enfoque / Sin Redes / Paseo)
    var focusDefaultMinutes by remember { mutableIntStateOf(lockManager.focusDefaultMinutes) }
    var noSocialDefaultMinutes by remember { mutableIntStateOf(lockManager.noSocialDefaultMinutes) }
    var noSocialGrayScale by remember { mutableStateOf(lockManager.noSocialGrayScale) }
    var paseoDopamineMinutes by remember { mutableIntStateOf(lockManager.paseoDopamineMinutes) }
    var paseoWhatsAppMinutes by remember { mutableIntStateOf(lockManager.paseoWhatsAppMinutes) }
    var paseoMusicBlocked by remember { mutableStateOf(lockManager.paseoMusicBlocked) }
    // V31: estado de permisos de sistema (ahorro de batería y bloqueo de notificaciones)
    var canWriteSettings by remember { mutableStateOf(lockManager.canWriteSystemSettings()) }
    var notifListenerEnabled by remember { mutableStateOf(ZenNotificationListenerService.isEnabled(context)) }
    var notifBlockingEnabled by remember { mutableStateOf(lockManager.notifBlockingEnabled) }
    var notifBlockingAlways by remember { mutableStateOf(lockManager.notifBlockingAlways) }
    var paseoNotificationsAllowed by remember { mutableStateOf(lockManager.paseoNotificationsAllowed) }

    // Carga inicial
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            installedApps = LauncherUtils.getInstalledApps(context)
        }
        hasUsageStats = LauncherUtils.hasUsageStatsPermission(context)
        if (selectedPackages.size > 4) {
            val truncated = selectedPackages.take(4).toSet()
            selectedPackages = truncated
            lockManager.allowedPackages = truncated
        }
    }

    // V20.2: refrescar ajustes si cambiaron desde la extensión / otro dispositivo
    LaunchedEffect(configVersion) {
        crossDeviceLockEnabled = lockManager.crossDeviceLockEnabled
        workModeEnabled = lockManager.workModeEnabled
        focusDefaultMinutes = lockManager.focusDefaultMinutes
        noSocialDefaultMinutes = lockManager.noSocialDefaultMinutes
        noSocialGrayScale = lockManager.noSocialGrayScale
        paseoDopamineMinutes = lockManager.paseoDopamineMinutes
        paseoWhatsAppMinutes = lockManager.paseoWhatsAppMinutes
        paseoMusicBlocked = lockManager.paseoMusicBlocked
    }

    // V31: mantener al día los permisos de sistema al volver de Ajustes
    LaunchedEffect(Unit) {
        while (true) {
            canWriteSettings = lockManager.canWriteSystemSettings()
            notifListenerEnabled = ZenNotificationListenerService.isEnabled(context)
            notifBlockingEnabled = lockManager.notifBlockingEnabled
            notifBlockingAlways = lockManager.notifBlockingAlways
            paseoNotificationsAllowed = lockManager.paseoNotificationsAllowed
            delay(1000)
        }
    }

    // V24 (Propuesta 5): proteger TODOS los ajustes durante sesión activa (local o remota)
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

    // Selector de hora nativo (reloj del sistema) para el horario L-V
    fun pickMinuteOfDay(current: Int, onPicked: (Int) -> Unit) {
        val h = (current / 60).coerceIn(0, 23)
        val m = (current % 60).coerceIn(0, 59)
        android.app.TimePickerDialog(context, { _, hour, minute -> onPicked(hour * 60 + minute) }, h, m, true).show()
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
            // V28: volver desde Ajustes
            if (onBackToLauncher != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = { onBackToLauncher() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = ZenSage,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Volver", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZenSage)
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

            // Contenedor Central (scroll completo)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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

                // Tarjeta de Reforzar Seguridad / Establecer como Inicio Predeterminado
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

                // MI CUENTA (sincronización con la extensión de Chrome)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(ZenOlive.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = ZenOlive,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "MI CUENTA",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZenOlive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sincroniza con tu extensión de Chrome",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = ZenSage
                                )
                            }
                        }

                        // CÓDIGO ÚNICO DEL TELÉFONO
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CreamBackground, RoundedCornerShape(12.dp))
                                .border(1.dp, ZenOlive.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
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
                                    fontSize = 20.sp,
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
                        val isSignedIn = lockManager.isGoogleSignedIn

                        Button(
                            onClick = {
                                if (!isSignedIn) {
                                    onGoogleSignIn()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSignedIn) Color(0xFF34A853) else Color(0xFF4285F4)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
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
                            modifier = Modifier.fillMaxWidth().height(42.dp),
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

                // ============================================================
                // V28: MODOS — configuración de Escuela/Trabajo, Enfoque,
                // Sin Redes y Paseo dentro de la parte de ajustes.
                // ============================================================

                // Modo Escuela/Trabajo (activación + horario L-V)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, tint = ZenOlive)
                                Column {
                                    Text(
                                        text = "MODOS",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ZenOlive,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Modo Escuela/Trabajo",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                        color = ZenCharcoal
                                    )
                                }
                            }
                            Switch(
                                checked = workModeEnabled,
                                onCheckedChange = { newValue ->
                                    executeWithAuthIfNeeded {
                                        workModeEnabled = newValue
                                        lockManager.workModeEnabled = newValue
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = ZenOlive,
                                    uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                                    checkedThumbColor = ZenWhite
                                )
                            )
                        }

                        Text(
                            text = "Bloquea automáticamente las apps distractoras mientras estés dentro de tu horario de clase o trabajo (Lunes a Viernes).",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ZenSage
                        )

                        val workActiveNow = lockManager.isWorkModeActive()
                        Text(
                            text = if (workActiveNow) "● Modo Escuela/Trabajo ACTIVO ahora" else "○ Sin actividad en este momento",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (workActiveNow) ZenGreen else ZenSage
                        )

                        if (workModeEnabled) {
                            HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showWorkSchedule = !showWorkSchedule }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "HORARIO (LUNES A VIERNES)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZenOlive,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (showWorkSchedule) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = ZenOlive
                                )
                            }

                            if (showWorkSchedule) {
                                @Suppress("UNUSED_VARIABLE")
                                val scheduleTick = scheduleVersion

                                WorkMonthCalendar(
                                    lockManager = lockManager,
                                    withAuth = { action -> executeWithAuthIfNeeded(action) },
                                    onChanged = { scheduleVersion++ }
                                )

                                // Horarios de los días activos (ajuste fino de horas)
                                val activeDays = lockManager.workSchedule.sortedBy { it.day }
                                if (activeDays.isNotEmpty()) {
                                    HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))
                                    Text(
                                        text = "HORARIOS ACTIVOS",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ZenOlive,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val dayNames = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")
                                    activeDays.forEach { sched ->
                                        val name = dayNames.getOrElse(sched.day - 1) { "Día ${sched.day}" }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ZenCharcoal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        pickMinuteOfDay(sched.startMinute) { newStart ->
                                                            val newEnd = if (newStart >= sched.endMinute)
                                                                (newStart + 60).coerceAtMost(1439)
                                                            else sched.endMinute
                                                            executeWithAuthIfNeeded {
                                                                lockManager.setWorkSchedule(sched.day, newStart, newEnd)
                                                                scheduleVersion++
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(formatMinuteOfDay(sched.startMinute), fontSize = 11.sp, color = ZenOlive)
                                                }
                                                Text("-", fontSize = 11.sp, color = ZenSage)
                                                OutlinedButton(
                                                    onClick = {
                                                        pickMinuteOfDay(sched.endMinute) { newEnd ->
                                                            val newStart = if (newEnd <= sched.startMinute)
                                                                (newEnd - 60).coerceAtLeast(0)
                                                            else sched.startMinute
                                                            executeWithAuthIfNeeded {
                                                                lockManager.setWorkSchedule(sched.day, newStart, newEnd)
                                                                scheduleVersion++
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(formatMinuteOfDay(sched.endMinute), fontSize = 11.sp, color = ZenOlive)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        executeWithAuthIfNeeded {
                                                            lockManager.setWorkSchedule(sched.day, -1, -1)
                                                            scheduleVersion++
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Quitar horario",
                                                        tint = ZenSage,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = ZenSage.copy(alpha = 0.15f))

                                Text(
                                    text = "WhatsApp queda exento, con límite de uso seguido.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = ZenSage
                                )
                                SettingStepper(
                                    label = "WhatsApp (límite)",
                                    value = workWhatsAppMinutes,
                                    unit = "min",
                                    step = 5,
                                    onChanged = { newVal ->
                                        executeWithAuthIfNeeded {
                                            workWhatsAppMinutes = newVal.coerceIn(1, 60)
                                            lockManager.workWhatsAppMinutes = workWhatsAppMinutes
                                        }
                                    }
                                )

                                Text(
                                    text = "Las videollamadas (Meet, Zoom, Teams, Discord) y las apps de trabajo quedan disponibles. El resto de apps distractoras se bloquean mientras el modo esté activo.",
                                    fontSize = 10.sp,
                                    color = ZenSage,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Modo Enfoque
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = ZenOlive)
                            Column {
                                Text(
                                    text = "MODOS",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZenOlive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Modo Enfoque",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = ZenCharcoal
                                )
                            }
                        }
                        Text(
                            text = "Duración por defecto del bloqueo de enfoque.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ZenSage
                        )
                        SettingStepper(
                            label = "Duración",
                            value = focusDefaultMinutes,
                            unit = "min",
                            step = 5,
                            onChanged = { newVal ->
                                executeWithAuthIfNeeded {
                                    focusDefaultMinutes = newVal.coerceIn(5, 480)
                                    lockManager.focusDefaultMinutes = focusDefaultMinutes
                                }
                            }
                        )
                    }
                }

                // Modo Sin Redes
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = ZenOlive)
                            Column {
                                Text(
                                    text = "MODOS",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZenOlive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Modo Sin Redes",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = ZenCharcoal
                                )
                            }
                        }
                        Text(
                            text = "Duración por defecto y aspecto del modo sin redes / dopamina.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ZenSage
                        )
                        SettingStepper(
                            label = "Duración",
                            value = noSocialDefaultMinutes,
                            unit = "min",
                            step = 15,
                            onChanged = { newVal ->
                                executeWithAuthIfNeeded {
                                    noSocialDefaultMinutes = newVal.coerceIn(15, 240)
                                    lockManager.noSocialDefaultMinutes = noSocialDefaultMinutes
                                }
                            }
                        )
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
                                Column {
                                    Text(
                                        text = "Escala de grises",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                        color = ZenCharcoal
                                    )
                                    Text(
                                        text = "La pantalla se desatura durante el modo.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = ZenSage
                                    )
                                }
                            }
                            Switch(
                                checked = noSocialGrayScale,
                                onCheckedChange = { newValue ->
                                    executeWithAuthIfNeeded {
                                        noSocialGrayScale = newValue
                                        lockManager.noSocialGrayScale = newValue
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = ZenOlive,
                                    uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                                    checkedThumbColor = ZenWhite
                                )
                            )
                        }
                    }
                }

                // Modo Paseo
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = ZenOlive)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MODOS",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZenOlive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Modo Paseo",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = ZenCharcoal
                                )
                            }
                            Switch(
                                checked = paseoActive,
                                onCheckedChange = { onTogglePaseo() },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = ZenOlive,
                                    uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                                    checkedThumbColor = ZenWhite
                                )
                            )
                        }
                        Text(
                            text = if (paseoActive)
                                "● Modo Paseo ACTIVO — se desactiva a voluntad, sin temporizador."
                            else
                                "Límites de uso mientras estás de paseo. Se activa/desactiva a voluntad, sin temporizador.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = if (paseoActive) ZenGreen else ZenSage
                        )
                        Text(
                            text = "RRSS, juegos y apps dopamínicas: límite de uso seguido. Al superarlo solo se bloquea la app en uso. WhatsApp y música aplican sus propias reglas.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ZenSage
                        )
                        SettingStepper(
                            label = "Apps dopamina",
                            value = paseoDopamineMinutes,
                            unit = "min",
                            step = 5,
                            onChanged = { newVal ->
                                executeWithAuthIfNeeded {
                                    paseoDopamineMinutes = newVal.coerceIn(5, 120)
                                    lockManager.paseoDopamineMinutes = paseoDopamineMinutes
                                }
                            }
                        )
                        SettingStepper(
                            label = "WhatsApp",
                            value = paseoWhatsAppMinutes,
                            unit = "min",
                            step = 5,
                            onChanged = { newVal ->
                                executeWithAuthIfNeeded {
                                    paseoWhatsAppMinutes = newVal.coerceIn(5, 120)
                                    lockManager.paseoWhatsAppMinutes = paseoWhatsAppMinutes
                                }
                            }
                        )
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
                                Column {
                                    Text(
                                        text = "Bloquear música",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                        color = ZenCharcoal
                                    )
                                    Text(
                                        text = "La música queda bloqueada durante el paseo.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = ZenSage
                                    )
                                }
                            }
                            Switch(
                                checked = paseoMusicBlocked,
                                onCheckedChange = { newValue ->
                                    executeWithAuthIfNeeded {
                                        paseoMusicBlocked = newValue
                                        lockManager.paseoMusicBlocked = newValue
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = ZenOlive,
                                    uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                                    checkedThumbColor = ZenWhite
                                )
                            )
                        }
                    }
                }

                // V31: AHORRO DE BATERÍA AUTOMÁTICO
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.BatterySaver, contentDescription = null, tint = ZenOlive)
                            Column {
                                Text(
                                    text = "SISTEMA",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZenOlive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ahorro de batería automático",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = ZenCharcoal
                                )
                            }
                        }
                        Text(
                            text = "Al activar cualquier modo (Enfoque, Sin Redes, Escuela/Trabajo o Paseo) se enciende el ahorro de batería del sistema para reducir el consumo. Se apaga solo cuando terminan todos los modos.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ZenSage
                        )
                        if (canWriteSettings) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = ZenGreen)
                                Text(
                                    text = "Permiso concedido: el ahorro se activa solo con cada modo.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = ZenGreen
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(lockManager.openWriteSettingsIntent())
                                    } catch (e: Exception) {
                                        // Ajustes de sistema no disponibles
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Conceder permiso: Modificar ajustes del sistema", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Necesario para poder activar el ahorro de batería automáticamente. Sin él, los modos funcionan igual pero sin ahorro de energía.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = ZenCoral
                            )
                        }
                    }
                }

                // V31: BLOQUEO DE NOTIFICACIONES
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = ZenOlive)
                            Column {
                                Text(
                                    text = "SISTEMA",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZenOlive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Bloqueo de notificaciones",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = ZenCharcoal
                                )
                            }
                        }
                        Text(
                            text = "Se ocultan las notificaciones al activarse Enfoque, Sin Redes o Escuela/Trabajo. Llamadas y SMS siempre llegan. Puedes activarlo siempre o solo con los modos, y elegir si la caminata deja pasar notificaciones.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ZenSage
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bloquear notificaciones",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                    color = ZenCharcoal
                                )
                            }
                            Switch(
                                checked = notifBlockingEnabled,
                                onCheckedChange = { newValue ->
                                    notifBlockingEnabled = newValue
                                    lockManager.notifBlockingEnabled = newValue
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = ZenOlive,
                                    uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                                    checkedThumbColor = ZenWhite
                                )
                            )
                        }
                        Text(
                            text = "Alcance del bloqueo",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            color = ZenCharcoal,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                false to "Solo con modos",
                                true to "Siempre"
                            ).forEach { (always, label) ->
                                val sel = notifBlockingAlways == always
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) ZenOlive else ZenSage.copy(alpha = 0.12f))
                                        .clickable {
                                            notifBlockingAlways = always
                                            lockManager.notifBlockingAlways = always
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (sel) ZenWhite else ZenCharcoal
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Permitir notificaciones en caminata",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                    color = ZenCharcoal
                                )
                                Text(
                                    text = "El modo caminata/salida/cita bloquea por defecto; actívalo para dejar pasar notificaciones ahí.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = ZenSage
                                )
                            }
                            Switch(
                                checked = paseoNotificationsAllowed,
                                onCheckedChange = { newValue ->
                                    paseoNotificationsAllowed = newValue
                                    lockManager.paseoNotificationsAllowed = newValue
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = ZenOlive,
                                    uncheckedTrackColor = ZenSage.copy(alpha = 0.3f),
                                    checkedThumbColor = ZenWhite
                                )
                            )
                        }
                        if (notifListenerEnabled) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = ZenGreen)
                                Text(
                                    text = "Activo: el bloqueo de notificaciones está funcionando.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = ZenGreen
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        )
                                    } catch (e: Exception) {
                                        // Ajustes no disponibles
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ZenOlive),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Activar Acceso a notificaciones", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "AntiProcrastinación necesita este permiso de sistema para ocultar las notificaciones mientras hay un modo activo.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = ZenCoral
                            )
                        }
                    }
                }

                // AJUSTES GENERALES (Modo Oscuro / Bloqueo Cruzado)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZenWhite),
                    border = BorderStroke(1.dp, ZenSage.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = ZenOlive)
                            Column {
                                Text(
                                    text = "AJUSTES GENERALES",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZenOlive,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Personalización de la aplicación",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = ZenSage
                                )
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
                    }
                }

                // V32: los descansos ya se configuran en el plan de actividades
                // (Modo Enfoque) — la sección Pomodoro de ajustes se eliminó.

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

                // Frase aleatoria de enfoque
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
                                text = "Al iniciar el bloqueo se elegirá automáticamente una frase motivacional aleatoria de una colección de más de 10.000 frases de reflexión y motivación que podrás revisar durante tu sesión de enfoque.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = ZenCharcoal
                            )
                        }
                    }
                }
            }

            // Versión real del build
            Text(
                text = "Versión $appVersionName",
                fontSize = 11.sp,
                color = ZenSage.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Stepper compacto de ajustes: "Label: VALOR unit" con botones - / +. */
@Composable
private fun SettingStepper(
    label: String,
    value: Int,
    unit: String,
    step: Int,
    onChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label: ",
                fontSize = 13.sp,
                color = ZenCharcoal,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$value",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ZenOlive
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    fontSize = 13.sp,
                    color = ZenCharcoal,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(
                onClick = { onChanged(value - step) },
                modifier = Modifier.size(32.dp).clip(CircleShape),
                colors = IconButtonDefaults.iconButtonColors(contentColor = ZenOlive)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Reducir")
            }
            IconButton(
                onClick = { onChanged(value + step) },
                modifier = Modifier.size(32.dp).clip(CircleShape),
                colors = IconButtonDefaults.iconButtonColors(contentColor = ZenOlive)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aumentar")
            }
        }
    }
}

/** Formatea un minuto del día (0-1439) como "HH:mm". */
private fun formatMinuteOfDay(minuteOfDay: Int?): String =
    if (minuteOfDay == null) "--:--" else String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

/** Celda del calendario de mes. day=0 significa hueco vacío (fuera del mes). */
private data class MonthDay(val day: Int, val isoDay: Int, val isWeekend: Boolean, val active: Boolean)

/**
 * V28: Calendario de mes completo para el Modo Escuela/Trabajo.
 * Tocar un día de lunes a viernes lo activa (horario por defecto 08:00-17:00)
 * o lo desactiva. Los fines de semana y días de otros meses no se tocan.
 */
@Composable
private fun WorkMonthCalendar(
    lockManager: LockManager,
    withAuth: (() -> Unit) -> Unit,
    onChanged: () -> Unit
) {
    val calendarNow = remember { java.util.Calendar.getInstance() }
    var viewYear by remember { mutableIntStateOf(calendarNow.get(java.util.Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(calendarNow.get(java.util.Calendar.MONTH)) }

    val schedule = lockManager.workSchedule

    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    val dayHeaders = listOf("L", "M", "X", "J", "V", "S", "D")

    // Cabecera: nombre del mes + navegación anterior/siguiente
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = {
            viewMonth--
            if (viewMonth < 0) { viewMonth = 11; viewYear-- }
        }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Mes anterior",
                tint = ZenOlive
            )
        }
        Text(
            text = "${monthNames[viewMonth]} $viewYear",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = ZenCharcoal
        )
        IconButton(onClick = {
            viewMonth++
            if (viewMonth > 11) { viewMonth = 0; viewYear++ }
        }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Mes siguiente",
                tint = ZenOlive
            )
        }
    }

    // Encabezado de días de la semana
    Row(modifier = Modifier.fillMaxWidth()) {
        dayHeaders.forEach { d ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = d, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ZenSage)
            }
        }
    }

    // Celdas del mes (la semana empieza en lunes)
    val monthDays = remember(viewYear, viewMonth, schedule) {
        val c = java.util.Calendar.getInstance()
        c.set(viewYear, viewMonth, 1)
        val offset = (c.get(java.util.Calendar.DAY_OF_WEEK) - 2 + 7) % 7
        val dim = c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        buildList {
            repeat(offset) { add(MonthDay(0, 0, false, false)) }
            for (d in 1..dim) {
                val cc = java.util.Calendar.getInstance()
                cc.set(viewYear, viewMonth, d)
                val dow = cc.get(java.util.Calendar.DAY_OF_WEEK)
                val iso = if (dow == java.util.Calendar.SUNDAY) 7 else dow - 1
                add(MonthDay(d, iso, iso == 6 || iso == 7, schedule.any { it.day == iso }))
            }
        }
    }

    val today = calendarNow.get(java.util.Calendar.DAY_OF_MONTH)
    val todayMonth = calendarNow.get(java.util.Calendar.MONTH)
    val todayYear = calendarNow.get(java.util.Calendar.YEAR)

    monthDays.chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { cell ->
                if (cell.day == 0) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                } else {
                    val isToday = cell.day == today && viewMonth == todayMonth && viewYear == todayYear
                    val bg = when {
                        cell.isWeekend -> Color.Transparent
                        cell.active -> ZenSoftGreen
                        else -> ZenSoftBlue
                    }
                    val borderColor = when {
                        isToday -> ZenOlive
                        cell.active -> ZenOlive.copy(alpha = 0.5f)
                        cell.isWeekend -> Color.Transparent
                        else -> ZenBorderLight.copy(alpha = 0.6f)
                    }
                    val textColor = when {
                        cell.isWeekend -> ZenSage.copy(alpha = 0.4f)
                        cell.active -> ZenCharcoal
                        else -> ZenSage
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .clickable(enabled = !cell.isWeekend) {
                                withAuth {
                                    if (cell.active) {
                                        lockManager.setWorkSchedule(cell.isoDay, -1, -1)
                                    } else {
                                        lockManager.setWorkSchedule(cell.isoDay, 8 * 60, 17 * 60)
                                    }
                                    onChanged()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cell.day.toString(),
                            fontSize = 12.sp,
                            fontWeight = if (cell.active || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }
            repeat(7 - week.size) {
                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
            }
        }
    }

    Spacer(modifier = Modifier.height(2.dp))

    // Leyenda
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Toca un día (L-V) para activar o quitar el horario.",
            fontSize = 10.sp,
            color = ZenSage
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ZenSoftGreen)
                    .border(1.dp, ZenOlive.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            )
            Text(text = "Activo", fontSize = 10.sp, color = ZenSage)
        }
    }
}
