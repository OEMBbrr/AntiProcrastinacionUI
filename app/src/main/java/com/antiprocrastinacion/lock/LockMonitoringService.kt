package com.antiprocrastinacion.lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class LockMonitoringService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var lockManager: LockManager
    private var lastRelaunchTime = 0L
    // V29: Modo Paseo — seguimiento de la app en primer plano para medir uso continuo
    private var paseoTrackedPkg: String? = null
    private var paseoTrackedSince = 0L
    private var lastPaseoBlockTime = 0L
    // V28: Modo Escuela/Trabajo — expulsión de apps distractoras (debounce)
    private var lastWorkBlockTime = 0L
    // V30: Modo Sin Redes — seguimiento de uso de WhatsApp (límite 30 min) y debounce
    private var noSocialTrackedPkg: String? = null
    private var noSocialTrackedSince = 0L
    private var lastNoSocialBlockTime = 0L

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
        lockManager = LockManager.getInstance(this)
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
        // V31: al arrancar, alinear ahorro de batería y barrer notificaciones bloqueables
        lockManager.syncBatterySaver()
        ZenNotificationListenerService.requestRefresh(this)
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

    private fun buildNotification(isLocked: Boolean): android.app.Notification {
        val noSocial = lockManager.isNoSocialModeActive() && !isLocked
        val workActive = lockManager.isWorkModeActive() && !isLocked
        val paseo = lockManager.paseoModeEnabled && !isLocked
        val contentText: String = when {
            isLocked -> "Protección activa ininterrumpida contra distracciones"
            noSocial -> {
                val mins = (lockManager.noSocialRemainingMs() / 60_000L).coerceAtLeast(1)
                when {
                    lockManager.isNoSocialAllBlocked() ->
                        "Cooldown de ${LockManager.NOSOCIAL_ALL_BLOCKED_MINUTES} min: TODAS las apps bloqueadas."
                    lockManager.isNoSocialTempUnlocked() ->
                        "Tregua activa (${LockManager.NOSOCIAL_TREGUA_MINUTES} min): todo abierto."
                    else ->
                        "RRSS, juegos y navegadores bloqueados. Termina en ~$mins min."
                }
            }
            workActive -> {
                val end = lockManager.workModeEndMillis()
                val mins = if (end > 0) ((end - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1) else 0
                "Distracciones bloqueadas automáticamente. Termina en ~$mins min."
            }
            paseo -> "Vive el momento: RRSS y juegos con límite, WhatsApp 20 min, música bloqueada."
            else -> "Sincronización activa. Esperando modo enfoque..."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                when {
                    isLocked -> "Modo Enfoque Máxima Seguridad 🧘"
                    noSocial -> "Modo Sin Redes 📵"
                    workActive -> "Modo Escuela/Trabajo activo 🏫"
                    paseo -> "Modo Paseo activo 🚶"
                    else -> "AntiProcrastinación 🧘"
                }
            )
            .setContentText(contentText)
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
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(lockManager.isLocked))
        } catch (e: Exception) {
            // Notificación no crítica
        }
    }

    /** V31: tras un cambio de modo, refrescar la notificación Y el bloqueo de notificaciones. */
    private fun refreshUi() {
        updateNotification()
        ZenNotificationListenerService.requestRefresh(this)
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            var wasTempUnlocked = lockManager.isTempUnlocked
            var wasLocked = lockManager.isLocked
            var wasPaseoOn = lockManager.paseoModeEnabled
            var wasWorkOn = lockManager.isWorkModeActive()
            var wasNoSocialOn = lockManager.isNoSocialModeActive()

            while (isActive) {
                // V31: AHORRO DE BATERÍA — con cualquier modo activo se enciende solo;
                // solo escribe cuando cambia el estado (barato llamarlo cada pasada).
                lockManager.syncBatterySaver()

                // V32: alarma de cambio de segmento del plan de actividades (solo en enfoque)
                lockManager.pollSegmentAlarm()

                // V30: MODO SIN REDES — prioridad máxima. Bloquea RRSS/juegos/navegadores
                // salvo WhatsApp (límite 30 min seguidos). Tregua propia de 5 min.
                val noSocialOn = lockManager.isNoSocialModeActive()
                if (noSocialOn && !lockManager.isLocked) {
                    if (!wasNoSocialOn) {
                        wasNoSocialOn = true
                        wasWorkOn = false
                        wasPaseoOn = false
                        wasLocked = false
                        refreshUi()
                    }
                    monitorNoSocialLoop()
                    delay(500)
                    continue
                }
                if (wasNoSocialOn) {
                    wasNoSocialOn = false
                    // Al terminar el tiempo, limpiar el estado del Modo Sin Redes.
                    lockManager.stopNoSocialMode()
                    refreshUi()
                }

                // V28: MODO ESCUELA/TRABAJO — bloqueo automático dentro del horario
                // (L-V, con colchón de 5 min tras la hora de salida). Sin botón físico.
                val workOn = lockManager.isWorkModeActive()
                if (workOn && !lockManager.isLocked) {
                    if (!wasWorkOn) {
                        wasWorkOn = true
                        wasPaseoOn = false
                        wasLocked = false
                        refreshUi()
                    }
                    monitorWorkModeLoop()
                    delay(500)
                    continue
                }
                if (wasWorkOn) {
                    wasWorkOn = false
                    // Al salir del horario, reiniciar el límite de WhatsApp para el próximo bloque
                    lockManager.clearWorkWhatsAppSession()
                    refreshUi()
                }

                // V29: MODO PASEO — vigilancia de uso continuo (RRSS/juegos 15 min, WhatsApp 20 min,
                // música bloqueada). Solo bloquea la app en uso; el resto queda disponible.
                if (lockManager.paseoModeEnabled && !lockManager.isLocked) {
                    if (!wasPaseoOn) {
                        wasPaseoOn = true
                        wasLocked = false
                        refreshUi()
                    }
                    monitorPaseoLoop()
                    delay(500)
                    continue
                }
                if (wasPaseoOn) {
                    wasPaseoOn = false
                    refreshUi()
                }

                if (!lockManager.isLocked) {
                    // V20: modo escucha — el servicio permanece activo esperando
                    // un posible bloqueo cruzado desde la extensión de Chrome
                    if (wasLocked) {
                        wasLocked = false
                        refreshUi()
                    }
                    wasTempUnlocked = false
                    delay(500)
                    continue
                }

                if (!wasLocked) {
                    wasLocked = true
                    refreshUi()
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
                    // V28: limpiar tregua_until en Firebase para que la extensión re-bloquee
                    lockManager.pushLockStateToFirebase(lockManager.isLocked, lockManager.lockEndTime)
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

    /**
     * V29: una pasada del vigilante del Modo Paseo. Mide el tiempo en primer plano
     * de las apps con límite (RRSS/juegos/dopamínicas y WhatsApp) y expulsa de la
     * app al superar el límite. Solo bloquea la app en uso; el resto sigue libre.
     */
    private fun monitorPaseoLoop() {
        val fg = LauncherUtils.getForegroundPackage(applicationContext)
        if (fg == null || fg == packageName) {
            paseoTrackedPkg = null
            paseoTrackedSince = 0L
            return
        }

        val category = LauncherUtils.classifyWalkApp(applicationContext, fg)
        when (category) {
            LauncherUtils.WalkAppCategory.MUSIC -> {
                if (lockManager.paseoMusicBlocked) {
                    if (!lockManager.isPaseoBlocked(fg)) lockManager.blockPaseoApp(fg)
                    relaunchPaseoBlock(fg, "Música bloqueada durante el paseo. Vive el momento.")
                }
                paseoTrackedPkg = null
                paseoTrackedSince = 0L
            }
            LauncherUtils.WalkAppCategory.WHATSAPP,
            LauncherUtils.WalkAppCategory.DOPAMINE -> {
                if (lockManager.isPaseoBlocked(fg)) {
                    relaunchPaseoBlock(
                        fg,
                        "Límite de ${lockManager.paseoLimitMs(fg) / 60_000L} min alcanzado en esta app."
                    )
                    paseoTrackedPkg = null
                    paseoTrackedSince = 0L
                    return
                }
                val now = System.currentTimeMillis()
                if (fg == paseoTrackedPkg && paseoTrackedSince > 0L) {
                    lockManager.addPaseoUsage(fg, now - paseoTrackedSince)
                    if (lockManager.paseoUsageMs(fg) >= lockManager.paseoLimitMs(fg)) {
                        lockManager.blockPaseoApp(fg)
                        relaunchPaseoBlock(
                            fg,
                            "Límite de ${lockManager.paseoLimitMs(fg) / 60_000L} min alcanzado en esta app."
                        )
                        paseoTrackedPkg = null
                        paseoTrackedSince = 0L
                        return
                    }
                }
                paseoTrackedPkg = fg
                paseoTrackedSince = now
            }
            else -> {
                // Apps esenciales o libres: sin límite, pausar el contador.
                paseoTrackedPkg = null
                paseoTrackedSince = 0L
            }
        }
    }

    /** Expulsa al usuario de la app bloqueada y avisa con un Toast (debounce anti-spam). */
    private fun relaunchPaseoBlock(pkg: String, message: String) {
        val now = System.currentTimeMillis()
        if (now - lastPaseoBlockTime < 1500) return
        lastPaseoBlockTime = now
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
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

    /**
     * V28: una pasada del vigilante del Modo Escuela/Trabajo.
     * Dentro del horario configurado (con colchón de 5 min tras la salida):
     * - Llamadas/SMS y videollamadas (Meet, Zoom, Teams...) quedan libres.
     * - WhatsApp queda exento pero con límite de minutos seguidos.
     * - Cualquier otra app distractora (RRSS, juegos, navegadores...) se bloquea de inmediato.
     */
    private fun monitorWorkModeLoop() {
        val fg = LauncherUtils.getForegroundPackage(applicationContext)
        if (fg == null || fg == packageName) return

        // Llamadas y mensajes de texto: siempre disponibles.
        if (isEmergencyPackage(fg)) return

        // WhatsApp exento con límite de minutos seguidos (el inicio de sesión lo registra LockManager).
        if (LauncherUtils.isWhatsApp(fg)) {
            lockManager.startWorkWhatsAppSessionIfNeeded()
            if (!lockManager.isWorkWhatsAppAvailable()) {
                relaunchWorkBlock("Agotaste el tiempo de WhatsApp en el Modo Escuela/Trabajo.")
            }
            return
        }

        // Apps distractoras no exentas (incluye navegadores y juegos): bloqueo automático.
        if (LauncherUtils.isDistractionPackage(fg) && !LauncherUtils.isWorkModeAppAllowed(fg)) {
            relaunchWorkBlock("App bloqueada durante el Modo Escuela/Trabajo.")
        }
    }

    /** Expulsa al usuario de la app bloqueada y avisa con un Toast (debounce anti-spam). */
    private fun relaunchWorkBlock(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastWorkBlockTime < 1500) return
        lastWorkBlockTime = now
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
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

    /**
     * V30: una pasada del vigilante del Modo Sin Redes.
     * - Llamadas/SMS: siempre libres.
     * - Tregua (5 min): todo abierto (con cooldown de 10 min entre treguas).
     * - Cooldown de bloqueo total (5 min): TODAS las apps bloqueadas, sin tregua.
     * - WhatsApp: permitida pero con límite de 30 min seguidos; al superarlo,
     *   se activa el cooldown de bloqueo total.
     * - RRSS, juegos, navegadores y apps de dopamina: expulsión inmediata.
     */
    private fun monitorNoSocialLoop() {
        val fg = LauncherUtils.getForegroundPackage(applicationContext)
        if (fg == null || fg == packageName) {
            // Al volver al launcher se corta el "uso seguido" de WhatsApp.
            noSocialTrackedPkg?.let { lockManager.resetNoSocialUsage() }
            noSocialTrackedPkg = null
            noSocialTrackedSince = 0L
            return
        }

        // Al cambiar a otra app, el límite "seguido" de WhatsApp se reinicia.
        if (noSocialTrackedPkg != null && fg != noSocialTrackedPkg) {
            lockManager.resetNoSocialUsage()
        }

        // Llamadas y mensajes de texto: siempre disponibles.
        if (isEmergencyPackage(fg)) return

        // Tregua propia: todo lo bloqueado queda abierto durante 5 min.
        if (lockManager.isNoSocialTempUnlocked()) {
            noSocialTrackedPkg = null
            noSocialTrackedSince = 0L
            return
        }

        // Cooldown de bloqueo total: ni siquiera WhatsApp está disponible.
        if (lockManager.isNoSocialAllBlocked()) {
            relaunchNoSocialBlock(
                "Cooldown de ${LockManager.NOSOCIAL_ALL_BLOCKED_MINUTES} min: TODAS las apps bloqueadas por exceder el límite de 30 min."
            )
            noSocialTrackedPkg = null
            noSocialTrackedSince = 0L
            return
        }

        // Apps libres (no sociales): pausar el contador.
        if (!lockManager.isNoSocialAppBlocked(fg)) {
            noSocialTrackedPkg = null
            noSocialTrackedSince = 0L
            return
        }

        // WhatsApp: permitida con límite de 30 min seguidos.
        if (LauncherUtils.isWhatsApp(fg)) {
            val now = System.currentTimeMillis()
            if (fg == noSocialTrackedPkg && noSocialTrackedSince > 0L) {
                lockManager.addNoSocialUsage(fg, now - noSocialTrackedSince)
                if (lockManager.noSocialUsageMs(fg) >= LockManager.NOSOCIAL_APP_LIMIT_MINUTES * 60_000L) {
                    lockManager.activateNoSocialAllBlocked()
                    relaunchNoSocialBlock(
                        "Límite de ${LockManager.NOSOCIAL_APP_LIMIT_MINUTES} min de WhatsApp alcanzado. Cooldown de 5 min con todas las apps bloqueadas."
                    )
                    noSocialTrackedPkg = null
                    noSocialTrackedSince = 0L
                    return
                }
            }
            noSocialTrackedPkg = fg
            noSocialTrackedSince = now
            return
        }

        // Cualquier otra app bloqueada (RRSS, juegos, navegadores, dopamina): expulsión inmediata.
        relaunchNoSocialBlock("App no disponible durante el Modo Sin Redes.")
        noSocialTrackedPkg = null
        noSocialTrackedSince = 0L
    }

    /** Expulsa al usuario de la app bloqueada en el Modo Sin Redes (debounce anti-spam). */
    private fun relaunchNoSocialBlock(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastNoSocialBlockTime < 1500) return
        lastNoSocialBlockTime = now
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
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

    private fun isEmergencyPackage(pkg: String): Boolean {
        val dialer = LauncherUtils.getDefaultDialerPackage(this)
        if (dialer != null && pkg == dialer) return true
        val sms = LauncherUtils.getDefaultSmsPackage(this)
        if (sms != null && pkg == sms) return true
        return false
    }
}
