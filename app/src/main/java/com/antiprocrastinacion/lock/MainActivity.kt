package com.antiprocrastinacion.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.runtime.*
import com.antiprocrastinacion.lock.ui.screens.ConfigScreen
import com.antiprocrastinacion.lock.ui.screens.ZenScreen
import com.antiprocrastinacion.lock.ui.theme.AntiProcrastinacionTheme

class MainActivity : ComponentActivity() {

    private lateinit var lockManager: LockManager
    private var isLockedState by mutableStateOf(false)
    private var isTempUnlockedState by mutableStateOf(false)

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

    override fun onResume() {
        super.onResume()
        isLockedState = lockManager.isLocked
        isTempUnlockedState = lockManager.isTempUnlocked
        if (isLockedState) {
            LockMonitoringService.startService(this)
        }
    }
}
