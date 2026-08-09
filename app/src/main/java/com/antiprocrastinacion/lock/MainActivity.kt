package com.antiprocrastinacion.lock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.runtime.*
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.antiprocrastinacion.lock.ui.screens.ConfigScreen
import com.antiprocrastinacion.lock.ui.screens.ZenScreen
import com.antiprocrastinacion.lock.ui.theme.AntiProcrastinacionTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var lockManager: LockManager
    private var isLockedState by mutableStateOf(false)
    private var isTempUnlockedState by mutableStateOf(false)

    // Web Client ID del google-services.json (client_type: 3)
    private val webClientId = "927134130052-3lmelvvnfuk1dg8o71vosjdkf8s8k3p5.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockManager = LockManager(this)

        onBackPressedDispatcher.addCallback(this) {
            if (lockManager.isLocked && !lockManager.isTempUnlocked) {
                // Bloqueado por completo: deshabilitar retroceso
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        isLockedState = lockManager.isLocked
        isTempUnlockedState = lockManager.isTempUnlocked

        if (isLockedState) {
            LockMonitoringService.startService(this)
        }

        setContent {
            AntiProcrastinacionTheme {
                var currentScreen by remember { mutableStateOf(if (isLockedState) "zen" else "config") }
                val scope = rememberCoroutineScope()

                LaunchedEffect(isLockedState) {
                    currentScreen = if (isLockedState) "zen" else "config"
                }

                when (currentScreen) {
                    "config" -> {
                        ConfigScreen(
                            lockManager = lockManager,
                            onLockStarted = {
                                isLockedState = true
                                isTempUnlockedState = false
                                LockMonitoringService.startService(this@MainActivity)
                            },
                            onGoogleSignIn = {
                                scope.launch {
                                    signInWithGoogle()
                                }
                            }
                        )
                    }
                    "zen" -> {
                        ZenScreen(
                            lockManager = lockManager,
                            onUnlocked = {
                                isLockedState = false
                                isTempUnlockedState = false
                                LockMonitoringService.stopService(this@MainActivity)
                                finish()
                            }
                        )
                    }
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
                        Log.d("Auth", "Firebase sign-in exitoso: ${FirebaseAuth.getInstance().currentUser?.email}")
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
            LockMonitoringService.startService(this)
        }

        // Si ya hay sesión activa, arrancar listener y heartbeat
        if (lockManager.firebaseUid != null) {
            lockManager.startExtensionPingListener()
            lockManager.pushDeviceHeartbeatToFirebase()
        }
    }
}
