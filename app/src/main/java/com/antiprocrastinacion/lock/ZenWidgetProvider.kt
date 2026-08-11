package com.antiprocrastinacion.lock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * V27: Widget de escritorio rediseñado.
 * - Selector de modo (Enfoque / Sin Redes / Paseo / Estudio) con ‹ ›.
 * - Steppers de HORAS y MINUTOS (− / +) con estado persistente por widget.
 * - Botón INICIAR que lanza el bloqueo con la duración configurada.
 * - Acceso a las notas.
 */
class ZenWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val prefs = context.getSharedPreferences("zen_widget_prefs", Context.MODE_PRIVATE)
        var mode = prefs.getInt("mode_$appWidgetId", 0)
        var hours = prefs.getInt("hours_$appWidgetId", 0)
        var minutes = prefs.getInt("minutes_$appWidgetId", 25)

        when (intent.action) {
            ACTION_MODE_PREV -> {
                mode = (mode - 1 + MODES.size) % MODES.size
                prefs.edit().putInt("mode_$appWidgetId", mode).apply()
            }
            ACTION_MODE_NEXT -> {
                mode = (mode + 1) % MODES.size
                prefs.edit().putInt("mode_$appWidgetId", mode).apply()
            }
            ACTION_HOUR_UP -> {
                if (hours < 23) hours++
                prefs.edit().putInt("hours_$appWidgetId", hours).apply()
            }
            ACTION_HOUR_DOWN -> {
                if (hours > 0) hours--
                prefs.edit().putInt("hours_$appWidgetId", hours).apply()
            }
            ACTION_MIN_UP -> {
                if (minutes < 59) minutes++ else if (hours < 23) { minutes = 0; hours++ }
                prefs.edit().putInt("minutes_$appWidgetId", minutes).putInt("hours_$appWidgetId", hours).apply()
            }
            ACTION_MIN_DOWN -> {
                if (minutes > 0) minutes-- else if (hours > 0) { minutes = 59; hours-- }
                prefs.edit().putInt("minutes_$appWidgetId", minutes).putInt("hours_$appWidgetId", hours).apply()
            }
            ACTION_START -> {
                val total = hours * 60 + minutes
                if (total > 0) {
                    val launch = Intent(context, MainActivity::class.java).apply {
                        action = ACTION_START_FOCUS
                        putExtra(EXTRA_DURATION, total)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(launch)
                }
            }
        }

        val manager = AppWidgetManager.getInstance(context)
        updateWidget(context, manager, appWidgetId)
    }

    companion object {
        const val ACTION_START_FOCUS = "com.antiprocrastinacion.lock.START_FOCUS"
        const val ACTION_OPEN_NOTES = "com.antiprocrastinacion.lock.OPEN_NOTES"
        const val EXTRA_DURATION = "extra_duration"

        const val ACTION_MODE_PREV = "com.antiprocrastinacion.lock.WIDGET_MODE_PREV"
        const val ACTION_MODE_NEXT = "com.antiprocrastinacion.lock.WIDGET_MODE_NEXT"
        const val ACTION_HOUR_UP = "com.antiprocrastinacion.lock.WIDGET_HOUR_UP"
        const val ACTION_HOUR_DOWN = "com.antiprocrastinacion.lock.WIDGET_HOUR_DOWN"
        const val ACTION_MIN_UP = "com.antiprocrastinacion.lock.WIDGET_MIN_UP"
        const val ACTION_MIN_DOWN = "com.antiprocrastinacion.lock.WIDGET_MIN_DOWN"
        const val ACTION_START = "com.antiprocrastinacion.lock.WIDGET_START"

        private val MODES = listOf("Enfoque", "Sin Redes", "Paseo", "Estudio")

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("zen_widget_prefs", Context.MODE_PRIVATE)
            val mode = prefs.getInt("mode_$appWidgetId", 0)
            val hours = prefs.getInt("hours_$appWidgetId", 0)
            val minutes = prefs.getInt("minutes_$appWidgetId", 25)

            val views = RemoteViews(context.packageName, R.layout.zen_widget)
            views.setTextViewText(R.id.widget_title, MODES[mode])
            views.setTextViewText(
                R.id.widget_subtitle,
                when (mode) {
                    0 -> "Modo concentración"
                    1 -> "Pantalla en escala de grises"
                    2 -> "Bloqueo para conectar contigo"
                    else -> "Entorno sin interrupciones"
                }
            )
            views.setTextViewText(R.id.widget_hour_value, String.format("%02d", hours))
            views.setTextViewText(R.id.widget_min_value, String.format("%02d", minutes))

            fun broadcastPend(action: String, viewId: Int, requestCode: Int) {
                val intent = Intent(context, ZenWidgetProvider::class.java).apply {
                    this.action = action
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(viewId, pi)
            }

            broadcastPend(ACTION_MODE_PREV, R.id.widget_mode_prev, 10)
            broadcastPend(ACTION_MODE_NEXT, R.id.widget_mode_next, 11)
            broadcastPend(ACTION_HOUR_UP, R.id.widget_hour_inc, 20)
            broadcastPend(ACTION_HOUR_DOWN, R.id.widget_hour_dec, 21)
            broadcastPend(ACTION_MIN_UP, R.id.widget_min_inc, 30)
            broadcastPend(ACTION_MIN_DOWN, R.id.widget_min_dec, 31)
            broadcastPend(ACTION_START, R.id.widget_btn_start, 40)

            val notesIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_NOTES
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val notesPi = PendingIntent.getActivity(
                context,
                1001,
                notesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_open_notes, notesPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
