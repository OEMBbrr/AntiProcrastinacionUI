package com.antiprocrastinacion.lock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val label: String,
    val packageName: String
)

object LauncherUtils {

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        return resolveInfos.map { resolveInfo ->
            AppInfo(
                label = resolveInfo.loadLabel(pm).toString(),
                packageName = resolveInfo.activityInfo.packageName
            )
        }.filter { it.packageName != context.packageName } // Excluir nuestra app
         .distinctBy { it.packageName }
         .sortedBy { it.label }
    }

    fun getSystemHomePackages(context: Context): Set<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolveInfos.map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .toSet()
    }

    fun getDefaultDialerPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_DIAL)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, 0)
        }
        return resolveInfo?.activityInfo?.packageName
    }

    fun getDefaultSmsPackage(context: Context): String? {
        return try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (e: Exception) {
            null
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openHomeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * V26: pedir al sistema convertirse en el inicio predeterminado.
     * Android 10+ usa RoleManager (diálogo oficial "Usar siempre/Una vez");
     * en versiones anteriores se abre la configuración del launcher.
     */
    fun requestHomeRole(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
            context.startActivity(intent)
        } else {
            openHomeSettings(context)
        }
    }

    /** ¿Es nuestra app el inicio predeterminado del sistema (HOME)? */
    fun isDefaultHome(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
        } else {
            @Suppress("DEPRECATION")
            val resolveInfo = context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }

    fun getForegroundPackage(context: Context): String? {
        if (!hasUsageStatsPermission(context)) return null
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        // Consultar eventos de uso de los últimos 3 segundos
        val events = usageStatsManager.queryEvents(time - 3000, time)
        var fgPackage: String? = null
        val event = UsageEvents.Event()
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                fgPackage = event.packageName
            }
        }
        return fgPackage
    }

    fun isAppPausedOrStopped(context: Context, pkg: String): Boolean {
        if (!hasUsageStatsPermission(context)) return false
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 2000, time)
        var lastEventType = -1
        val event = UsageEvents.Event()
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == pkg) {
                lastEventType = event.eventType
            }
        }
        return lastEventType == UsageEvents.Event.ACTIVITY_PAUSED ||
               lastEventType == UsageEvents.Event.ACTIVITY_STOPPED
    }

    // ---------------------------------------------------------------------
    // V25: Nuevo Launcher — Apps salud/utilitarias sin bloatware
    // ---------------------------------------------------------------------

    // Prefijos de ruido del sistema / bloatware que nunca se usan.
    private val BLOAT_PREFIXES = listOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.printspooler",
        "com.android.shell",
        "com.android.providers.telephony",
        "com.android.providers.media",
        "com.android.theme",
        "com.transsion",
        "com.google.android.as",
        "com.google.android.apps.messaging.launcher",
        "com.google.android.googlequicksearchbox"
    )

    // Paquetes bloat preinstalados comunes (apps "ruido" que el usuario nunca usa).
    private val BLOAT_PACKAGES = setOf(
        "com.facebook.katana",
        "com.facebook.orca",
        "com.google.android.apps.magazines",
        "com.opera.browser",
        "com.opera.browser.beta",
        "com.android.hotwordenrollment",
        "com.android.dreams.phototable",
        "com.android.bookmarkprovider",
        "com.android.email"
    )

    /**
     * Devuelve SOLO las apps de salud y utilitarias para el nuevo launcher.
     * Filtra: nuestra app, otros launchers, bloatware de sistema y apps de
     * juegos/redes sociales/dopamina. Oculta el ruido preinstalado.
     */
    fun getHealthUtilityApps(context: Context): List<AppInfo> {
        val systemHomes = getSystemHomePackages(context)
        return getInstalledApps(context)
            .filter { app ->
                val pkg = app.packageName
                val label = app.label.lowercase()
                if (pkg == context.packageName) return@filter false      // nuestra app
                if (pkg in systemHomes) return@filter false              // otros launchers
                if (pkg in BLOAT_PACKAGES) return@filter false           // bloat conocido
                if (BLOAT_PREFIXES.any { pkg.startsWith(it) }) return@filter false
                // Solo apps de usuario (no sistema) que NO sean dopamina/juego/red social
                isUserApp(context, pkg) && !isDistractionApp(pkg, label)
            }
            .sortedBy { it.label }
    }

    /** Una app instalada por el usuario (no viene de fábrica). */
    private fun isUserApp(context: Context, pkg: String): Boolean {
        return try {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        } catch (e: Exception) {
            false
        }
    }

    /** Detecta apps de juego, redes sociales o alto consumo de pantalla (a ocultar). */
    private fun isDistractionApp(pkg: String, label: String): Boolean {
        val lower = pkg.lowercase()
        val social = listOf("facebook", "instagram", "tiktok", "snapchat", "twitter", "x.com",
            "whatsapp", "telegram", "twitch", "reddit", "pinterest", "linkedin")
        val gaming = listOf("game", "games", "candy", "royale", "pubg", "codm", "genshin", "roblox")
        if (social.any { lower.contains(it) }) return true
        if (gaming.any { lower.contains(it) }) return true
        if (gaming.any { label.contains(it) }) return true
        // Navegadores web son dopamínicos según la visión (se ocultan del cajón)
        val browsers = listOf("chrome", "firefox", "edge", "samsung internet", "opera", "browser")
        return browsers.any { lower.contains(it) }
    }

    /** Icono de una app en formato ImageBitmap (para mostrar solo logos). */
    fun getAppIconBitmap(context: Context, packageName: String): ImageBitmap? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap(128, 128)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    // V26: caché en memoria de iconos para eliminar el jank del cajón.
    // Cada icono se decodifica UNA sola vez y se reutiliza en recomposiciones/scrolls.
    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

    fun getAppIconBitmapCached(context: Context, packageName: String): androidx.compose.ui.graphics.ImageBitmap? {
        return iconCache.getOrPut(packageName) { getAppIconBitmap(context, packageName) }
    }

    fun clearIconCache() {
        iconCache.clear()
    }

    /** Lanza la app por su packageName. */
    fun launchApp(context: Context, packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Launcher: ignorar fallos de lanzamiento silenciosamente
        }
    }
}
