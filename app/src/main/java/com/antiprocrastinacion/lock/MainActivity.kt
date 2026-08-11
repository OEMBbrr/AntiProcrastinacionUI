package com.antiprocrastinacion.lock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.FragmentActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.antiprocrastinacion.lock.ui.screens.ConfigScreen
import com.antiprocrastinacion.lock.ui.screens.ZenScreen
import com.antiprocrastinacion.lock.ui.launcher.AppDrawerScreen
import com.antiprocrastinacion.lock.ui.launcher.LauncherScreen
import com.antiprocrastinacion.lock.ui.launcher.NotesAppScreen
import com.antiprocrastinacion.lock.ui.launcher.OrganizerScreen
import com.antiprocrastinacion.lock.ui.launcher.SleepCycleScreen
import com.antiprocrastinacion.lock.ui.theme.AntiProcrastinacionTheme
import com.antiprocrastinacion.lock.ui.theme.AccentPresets
import com.antiprocrastinacion.lock.ui.theme.ZenTheme
import kotlinx.coroutines.launch

// V24 (Propuesta 4): FragmentActivity para poder usar BiometricPrompt (biometría/PIN)
class MainActivity : FragmentActivity() {

    private lateinit var lockManager: LockManager
    private var isLockedState by mutableStateOf(false)
    private var isTempUnlockedState by mutableStateOf(false)
    private var darkTheme by mutableStateOf(false)
    private var configVersion by mutableStateOf(0)
    // V25: pantalla actual del launcher ("launcher" | "drawer" | "notes" | "sleep" | "organizer" | "config" | "zen")
    private var currentScreen by mutableStateOf("launcher")
    // V24 (Propuesta 2): solicitud de tregua entrante desde el PC (null = ninguna)
    private var treguaRequest by mutableStateOf<String?>(null)
    // V24: código 2FA que el PC solicita para el Bloqueo Cruzado (null = ninguno)
    private var authCodeRequest by mutableStateOf<String?>(null)

    // Web Client ID del google-services.json (Encriptado en Runtime)
    private val webClientId by lazy {
        String(android.util.Base64.decode("OTI3MTM0MTMwMDUyLTNsbWVsdnZuZnVrMWRnOG83MXZvc2pka2Y4czhrM3A1LmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29t", android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
    }

    // V26: acciones del widget de escritorio (Modo Enfoque y Notas)
    private fun handleWidgetIntent(intent: Intent?) {
        when (intent?.action) {
            ZenWidgetProvider.ACTION_START_FOCUS -> {
                val minutes = intent.getIntExtra(ZenWidgetProvider.EXTRA_DURATION, 25)
                lockManager.startLock(minutes)
                isLockedState = true
                isTempUnlockedState = false
                LockMonitoringService.startService(this)
                currentScreen = "zen"
            }
            ZenWidgetProvider.ACTION_OPEN_NOTES -> {
                currentScreen = "notes"
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // V26: al pulsar Inicio estando en otra pantalla (Notas, Organizador...),
        // el launcher debe volver a su pantalla principal.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            currentScreen =
                if (lockManager.isLocked && !lockManager.isTempUnlocked) "zen" else "launcher"
            return
        }
        handleWidgetIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockManager = LockManager(this)
        // V26: acciones lanzadas desde el widget de escritorio
        handleWidgetIntent(intent)

        onBackPressedDispatcher.addCallback(this) {
            if (lockManager.isLocked && !lockManager.isTempUnlocked) {
                // Bloqueado por completo: deshabilitar retroceso
            } else if (currentScreen != "launcher" && currentScreen != "zen") {
                // V25: desde cualquier pantalla interna del launcher volver al home
                currentScreen = "launcher"
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        isLockedState = lockManager.isLocked
        isTempUnlockedState = lockManager.isTempUnlocked
        darkTheme = lockManager.darkModeEnabled
        // V26: cargar el acento del sistema elegido en Ajustes -> Colores
        ZenTheme.accent = AccentPresets.byKey(lockManager.accentPresetKey)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // V20: el servicio se arranca siempre (modo escucha) para recibir
        // bloqueo cruzado desde la extensión de Chrome aunque la app esté
        // en segundo plano o cerrada
        LockMonitoringService.startService(this)

        // V20: escuchar bloqueo cruzado desde la extensión de Chrome
        lockManager.startLockStateListener { locked ->
            isLockedState = lockManager.isLocked
            isTempUnlockedState = lockManager.isTempUnlocked
            if (locked) {
                LockMonitoringService.startService(this)
            } else {
                finish()
            }
        }

        // V20.2: escuchar cambios de configuración remotos (desde la extensión)
        lockManager.startConfigListener { dark, _ ->
            darkTheme = dark
            configVersion++
        }

        // V24 (Propuesta 2): escuchar solicitudes de tregua desde la extensión de Chrome
        lockManager.startTreguaRequestListener { reqId, _ ->
            treguaRequest = reqId
        }

        // V24: escuchar el código 2FA que el PC solicita para el Bloqueo Cruzado
        lockManager.startAuthRequestListener { _, code ->
            authCodeRequest = code
        }

        setContent {
            AntiProcrastinacionTheme(darkTheme = darkTheme) {
                val scope = rememberCoroutineScope()

                // V25: el launcher se propaga al tema desaturado según modo claro/oscuro
                LaunchedEffect(darkTheme) {
                    com.antiprocrastinacion.lock.ui.launcher.LauncherTheme.isDark = darkTheme
                }

                LaunchedEffect(isLockedState) {
                    currentScreen = if (isLockedState) "zen" else "launcher"
                }

                when (currentScreen) {
                    "launcher" -> {
                        LauncherScreen(
                            lockManager = lockManager,
                            onOpenNotes = { currentScreen = "notes" },
                            onOpenSleepCycle = { currentScreen = "sleep" },
                            onOpenOrganizer = { currentScreen = "organizer" },
                            onOpenAppDrawer = { currentScreen = "drawer" },
                            onOpenSettings = { currentScreen = "config" },
                            onStartFocus = { minutes ->
                                lockManager.startLock(minutes)
                                isLockedState = true
                                isTempUnlockedState = false
                                LockMonitoringService.startService(this@MainActivity)
                            }
                        )
                    }
                    "drawer" -> {
                        AppDrawerScreen(
                            onBack = { currentScreen = "launcher" }
                        )
                    }
                    "notes" -> {
                        NotesAppScreen(
                            lockManager = lockManager,
                            onBack = { currentScreen = "launcher" }
                        )
                    }
                    "sleep" -> {
                        SleepCycleScreen(
                            onBack = { currentScreen = "launcher" }
                        )
                    }
                    "organizer" -> {
                        OrganizerScreen(
                            onBack = { currentScreen = "launcher" }
                        )
                    }
                    "config" -> {
                        ConfigScreen(
                            lockManager = lockManager,
                            configVersion = configVersion,
                            onLockStarted = {
                                isLockedState = true
                                isTempUnlockedState = false
                                LockMonitoringService.startService(this@MainActivity)
                            },
                            onGoogleSignIn = {
                                scope.launch {
                                    signInWithGoogle()
                                }
                            },
                            darkTheme = darkTheme,
                            onDarkThemeChange = { newValue ->
                                darkTheme = newValue
                                lockManager.darkModeEnabled = newValue
                            },
                            onAccentChange = { key ->
                                lockManager.accentPresetKey = key
                                ZenTheme.accent = AccentPresets.byKey(key)
                            },
                            onBackToLauncher = { currentScreen = "launcher" }
                        )
                    }
                    "zen" -> {
                        ZenScreen(
                            lockManager = lockManager,
                            onUnlocked = {
                                isLockedState = false
                                isTempUnlockedState = false
                                // V20: el servicio sigue en modo escucha para bloqueos cruzados
                                finish()
                            }
                        )
                    }
                }

                // V24: diálogo con el código de verificación que el PC debe introducir
                authCodeRequest?.let { code ->
                    AlertDialog(
                        onDismissRequest = { authCodeRequest = null },
                        title = { Text("Código de verificación") },
                        text = { Text("La extensión de Chrome solicita confirmación para el Bloqueo Cruzado.\n\nIntroduce este código en el PC:") },
                        confirmButton = {
                            Button(onClick = { authCodeRequest = null }) {
                                Text(code, style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { authCodeRequest = null }) { Text("Entendido") }
                        }
                    )
                }

                // V24 (Propuesta 2): diálogo de aprobación de tregua solicitada desde el PC
                treguaRequest?.let { _ ->
                    AlertDialog(
                        onDismissRequest = { treguaRequest = null },
                        title = { Text("Solicitud de tregua") },
                        text = { Text("El PC solicita 5 minutos de tregua. ¿Aprobar desde este teléfono?") },
                        confirmButton = {
                            Button(onClick = {
                                lockManager.respondTreguaRequest(true)
                                treguaRequest = null
                            }) { Text("Aprobar") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                lockManager.respondTreguaRequest(false)
                                treguaRequest = null
                            }) { Text("Denegar") }
                        }
                    )
                }
            }
        }
    }

    /**
     * Flujo de Google Sign-In con Credential Manager → Firebase Auth
     */
    private suspend fun signInWithGoogle() {
        try {
            val credentialManager = CredentialManager.create(this)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = this
            )

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = googleIdTokenCredential.idToken

            // Autenticar con Firebase usando el ID Token de Google
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
                        Log.d("Auth", "Firebase sign-in exitoso: $email")
                        if (email.isNotEmpty()) {
                            getSharedPreferences("anti_procrastinacion_prefs", MODE_PRIVATE)
                                .edit().putString("google_user_email", email).apply()
                        }
                        // Escribir perfil y arrancar listener de extensión
                        lockManager.pushUserProfile()
                        lockManager.pushDeviceHeartbeatToFirebase()
                        lockManager.startExtensionPingListener()
                    } else {
                        Log.e("Auth", "Firebase sign-in falló", task.exception)
                    }
                }
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Google Sign-In falló: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("Auth", "Error inesperado en sign-in: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        isLockedState = lockManager.isLocked
        isTempUnlockedState = lockManager.isTempUnlocked
        if (isLockedState) {
            enforceImmersiveLock()
        }

        // Arrancar siempre el servicio (modo escucha), el latido y el listener
        LockMonitoringService.startService(this)
        lockManager.startExtensionPingListener()
        lockManager.pushDeviceHeartbeatToFirebase()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && lockManager.isLocked && !lockManager.isTempUnlocked) {
            try {
                @Suppress("DEPRECATION")
                val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(closeDialogs)
            } catch (e: Exception) {
                Log.w("ZEN_LOCK", "Acción ACTION_CLOSE_SYSTEM_DIALOGS omitida por restricciones del sistema: ${e.message}")
            }
            enforceImmersiveLock()
        }
    }

    private fun enforceImmersiveLock() {
        if (lockManager.isLocked && !lockManager.isTempUnlocked) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
}
