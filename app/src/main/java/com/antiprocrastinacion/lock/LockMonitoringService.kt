package com.antiprocrastinacion.lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class LockMonitoringService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var lockManager: LockManager
    private var lastRelaunchTime = 0L

    companion object {
        const val CHANNEL_ID = "anti_proc_lock_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, LockMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LockMonitoringService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        lockManager = LockManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!lockManager.isLocked) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Modo Enfoque AntiProcrastinación",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoreo ininterrumpido de alta seguridad"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Modo Enfoque Máxima Seguridad 🧘")
        .setContentText("Protección ativa ininterrumpida contra distracciones")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun startMonitoringLoop() {
        serviceScope.launch {
            var wasTempUnlocked = lockManager.isTempUnlocked

            while (isActive) {
                if (lockManager.isLocked) {
                    val remaining = lockManager.timeRemaining
                    if (remaining <= 0) {
                        lockManager.stopLock()
                        stopSelf()
                        break
                    }

                    val isTempUnlocked = lockManager.isTempUnlocked

                    // 1. Detección instantánea de vencimiento de tregua
                    if (wasTempUnlocked && !isTempUnlocked) {
                        wasTempUnlocked = false
                        relaunchLockScreenInstant()
                    } else {
                        wasTempUnlocked = isTempUnlocked
                    }

                    val fgPackage = LauncherUtils.getForegroundPackage(applicationContext)
                    val systemHomePackages = LauncherUtils.getSystemHomePackages(applicationContext)

                    // 2. Si se detecta el escritorio o launcher del sistema (ej. HiLauncher, Launcher3): REFORZAR BLOQUEO INSTANTÁNEO
                    if (fgPackage != null && systemHomePackages.contains(fgPackage)) {
                        relaunchLockScreenInstant()
                    } else if (fgPackage == null && !isTempUnlocked) {
                        relaunchLockScreenInstant()
                    } else if (fgPackage != null && fgPackage != packageName) {
                        val isEmergencyApp = isEmergencyPackage(fgPackage)

                        if (!isTempUnlocked) {
                            // Cuando NO hay tregua activa:
                            if (!isEmergencyApp) {
                                relaunchLockScreenInstant()
                            } else {
                                // Si estaba en Teléfono o SMS y se pausó/minimizó: BLOQUEAR DE INMEDIATO
                                if (LauncherUtils.isAppPausedOrStopped(applicationContext, fgPackage)) {
                                    relaunchLockScreenInstant()
                                }
                            }
                        } else {
                            // Cuando SÍ hay tregua activa (5 min):
                            val isAllowed = isEmergencyApp || lockManager.allowedPackages.contains(fgPackage)
                            if (!isAllowed) {
                                relaunchLockScreenInstant()
                            } else {
                                if (LauncherUtils.isAppPausedOrStopped(applicationContext, fgPackage)) {
                                    relaunchLockScreenInstant()
                                }
                            }
                        }
                    }
                } else {
                    stopSelf()
                    break
                }
                delay(80) // Chequeo ultra rápido cada 80ms
            }
        }
    }

    private fun relaunchLockScreenInstant() {
        val now = System.currentTimeMillis()
        // Debounce ultra corto de 80ms para respuesta inmediata sin lag
        if (now - lastRelaunchTime > 80) {
            lastRelaunchTime = now
            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            startActivity(launchIntent)
        }
    }

    private fun isEmergencyPackage(pkg: String): Boolean {
        val dialer = LauncherUtils.getDefaultDialerPackage(this)
        if (dialer != null && pkg == dialer) return true
        val sms = LauncherUtils.getDefaultSmsPackage(this)
        if (sms != null && pkg == sms) return true
        return false
    }
}
