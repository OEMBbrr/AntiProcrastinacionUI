package com.antiprocrastinacion.lock

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * V31: BLOQUEO DE NOTIFICACIONES.
 *
 * Mientras hay un modo activo (Enfoque, Sin Redes, Escuela/Trabajo o Paseo) este
 * servicio oculta las notificaciones de las apps que distraen (RRSS, juegos,
 * navegadores, dopamina...). Las llamadas y los mensajes de texto SIEMPRE pasan,
 * igual que las notificaciones propias de AntiProcrastinación (temporizador y 2FA).
 *
 * Requiere que el usuario active "Acceso a notificaciones" en Ajustes del sistema
 * (Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS). La tarjeta de configuración lo
 * gestiona y muestra su estado.
 */
class ZenNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: ZenNotificationListenerService? = null
            private set

        /** ¿Está activado nuestro listener en los ajustes del sistema? */
        fun isEnabled(context: Context): Boolean {
            return try {
                val cn = ComponentName(context, ZenNotificationListenerService::class.java)
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                flat.split(":").any { it.trim().equals(cn.flattenToString(), ignoreCase = true) }
            } catch (e: Exception) {
                false
            }
        }

        /** Pide al listener activo que re-escudriñe las notificaciones ya publicadas
         *  (se llama cuando cambia el estado de un modo). No-op si no está conectado. */
        fun requestRefresh(context: Context) {
            instance?.reScanNow()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d("ZEN_NOTIF_BLOCK", "Listener de notificaciones conectado")
        // Al conectar (p.ej. justo tras activar un modo) barrer lo ya publicado.
        reScanNow()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            Log.d("ZEN_NOTIF_BLOCK", "Recibida notificación de ${sbn.packageName} (key=${sbn.key})")
            if (LockManager.getInstance(applicationContext).shouldBlockNotification(sbn.packageName)) {
                cancelNotification(sbn.key)
                Log.d("ZEN_NOTIF_BLOCK", "Bloqueada notificación de ${sbn.packageName}")
            }
        } catch (e: Exception) {
            Log.e("ZEN_NOTIF_BLOCK", "Error en onNotificationPosted: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No hacemos nada cuando una notificación se retira.
    }

    /** Re-evalúa las notificaciones ya publicadas (al activarse/desactivarse un modo). */
    fun reScanNow() {
        try {
            for (sbn in activeNotifications) {
                if (LockManager.getInstance(applicationContext).shouldBlockNotification(sbn.packageName)) {
                    cancelNotification(sbn.key)
                }
            }
        } catch (e: Exception) {
            Log.e("ZEN_NOTIF_BLOCK", "reScanNow falló: ${e.message}")
        }
    }
}
