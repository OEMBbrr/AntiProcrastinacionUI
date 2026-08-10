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
        const val AUTH_CHANNEL_ID = "anti_proc_auth_channel"
        const val AUTH_NOTIFICATION_ID = 2001

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
        lockManager.startPeriodicHeartbeat(serviceScope)
        // V20: escuchar bloqueo cruzado desde la extensión de Chrome aunque la app
        // esté en segundo plano o cerrada
        lockManager.startLockStateListener { locked ->
            if (locked) {
                relaunchLockScreenInstant()
            }
        }
        // V24 (Propuesta 2): si el PC pide una tregua, traer la app al frente
        // para que el usuario pueda aprobarla o denegarla.
        lockManager.startTreguaRequestListener { _, _ ->
            relaunchLockScreenInstant()
        }
        // V24: notificar el código 2FA que el PC solicita para el Bloqueo Cruzado
        lockManager.startAuthRequestListener { _, code ->
            showAuthCodeNotification(code)
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(lockManager.isLocked))
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // V20: el servicio permanece activo para escuchar el bloqueo cruzado remoto
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

            // V24: canal de alta importancia para el código de verificación 2FA
            val authChannel = NotificationChannel(
                AUTH_CHANNEL_ID,
                "Códigos de verificación",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Códigos que llegan cuando la extensión de Chrome solicita confirmación"
            }
            manager.createNotificationChannel(authChannel)
        }
    }

    private fun showAuthCodeNotification(code: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, AUTH_CHANNEL_ID)
                .setContentTitle("Código de verificación del PC")
                .setContentText("Introduce este código en la extensión de Chrome: $code")
                .setStyle(NotificationCompat.BigTextStyle().bigText("La extensión de Chrome (Bloqueo Cruzado) pide confirmación.\n\nCódigo: $code\n\nIntrodúcelo en el PC para completar la verificación."))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setFullScreenIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    ),
                    true
                )
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
            manager.notify(AUTH_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Notificación no crítica
        }
    }

    private fun buildNotification(isLocked: Boolean) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(if (isLocked) "Modo Enfoque Máxima Seguridad 🧘" else "AntiProcrastinación 🧘")
        .setContentText(
            if (isLocked) "Protección activa ininterrumpida contra distracciones"
            else "Sincronización activa. Esperando modo enfoque..."
        )
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

    private fun updateNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(lockManager.isLocked))
        } catch (e: Exception) {
            // Notificación no crítica
        }
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            var wasTempUnlocked = lockManager.isTempUnlocked
            var wasLocked = lockManager.isLocked

            while (isActive) {
                if (!lockManager.isLocked) {
                    // V20: modo escucha — el servicio permanece activo esperando
                    // un posible bloqueo cruzado desde la extensión de Chrome
                    if (wasLocked) {
                        wasLocked = false
                        updateNotification()
                    }
                    wasTempUnlocked = false
                    delay(500)
                    continue
                }

                if (!wasLocked) {
                    wasLocked = true
                    updateNotification()
                }

                val remaining = lockManager.timeRemaining
                if (remaining <= 0) {
                    lockManager.stopLock()
                    delay(500)
                    continue
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
