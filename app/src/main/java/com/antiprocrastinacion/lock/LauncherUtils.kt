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
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val isDistraction: Boolean = false
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

    /**
     * V28: Lista COMPLETA del cajón de aplicaciones. A diferencia de
     * getHealthUtilityApps, aquí TAMBIÉN aparecen WhatsApp, redes sociales,
     * juegos y navegadores, pero marcados con `isDistraction` para que la UI
     * los muestre oscuros/opacos y NO permita abrirlos.
     * Solo se ocultan: nuestra app, otros launchers y el ruido de sistema.
     */
    fun getLauncherApps(context: Context): List<AppInfo> {
        val systemHomes = getSystemHomePackages(context)
        return getInstalledApps(context)
            .filter { app ->
                val pkg = app.packageName
                if (pkg == context.packageName) return@filter false      // nuestra app
                if (pkg in systemHomes) return@filter false              // otros launchers
                isUserApp(context, pkg)                                   // solo apps de usuario
            }
            .map { app ->
                AppInfo(
                    label = app.label,
                    packageName = app.packageName,
                    isDistraction = isDistractionApp(app.packageName, app.label.lowercase())
                )
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

    /** True si el paquete es una app considerada distracción (sin depender del label). */
    fun isDistractionPackage(pkg: String): Boolean = isDistractionApp(pkg, "")

    /** WhatsApp siempre se identifica por su paquete oficial. */
    fun isWhatsApp(pkg: String): Boolean =
        pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b" || pkg.startsWith("com.whatsapp")

    /** Apps de videollamadas/teletrabajo que en Modo Escuela/Trabajo quedan libres. */
    fun isVideoCallPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.google.android.apps.meetings" ||
            lower.startsWith("com.google.android.apps.meetings") ||
            lower.contains("zoom") || lower.contains("teams") ||
            lower.contains("meet") || lower.contains("skype") ||
            lower.contains("duo") || lower.contains("webex") ||
            lower.contains("discord")
    }

    /**
     * En Modo Escuela/Trabajo: WhatsApp y apps de videollamada (Meet, Zoom, Teams)
     * quedan libres; el resto de distractoras se bloquean.
     */
    fun isWorkModeAppAllowed(pkg: String): Boolean {
        if (isWhatsApp(pkg)) return true
        if (isVideoCallPackage(pkg)) return true
        return !isDistractionPackage(pkg)
    }

    // ---------------------------------------------------------------------
    // V29: MODO PASEO — clasificación de apps según la visión del usuario
    // (RRSS/juegos/dopamínicas: 15 min; WhatsApp: 20 min; música: bloqueada;
    // cámara, llamadas, SMS, maps y útiles: libres; el resto: libre).
    // ---------------------------------------------------------------------

    /** Categoría de una app dentro del Modo Paseo. */
    enum class WalkAppCategory { ESSENTIAL, WHATSAPP, MUSIC, DOPAMINE, FREE }

    /** Apps de música/reproducción que el Modo Paseo bloquea por completo. */
    fun isMusicPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        val explicit = setOf(
            "com.spotify.music", "com.spotify.tv", "com.google.android.apps.youtube.music",
            "com.apple.android.music", "com.pandora.android", "com.soundcloud.android",
            "com.deezer.android.app", "com.amazon.mp3", "com.tidal.main", "com.audible.application",
            "com.clearchannel.iheartradio", "com.maxmpz.audioplayer", "com.nullsoft.winamp",
            "com.bambuna.podcastaddict", "com.samsung.android.music", "com.mi.globalmusic",
            "com.huawei.music", "com.sonyericsson.music", "com.xiaomi.music"
        )
        if (explicit.contains(pkg)) return true
        return lower.contains("music") || lower.contains("spotify") || lower.contains("pandora") ||
            lower.contains("soundcloud") || lower.contains("deezer") || lower.contains("podcast") ||
            lower.contains("winamp") || lower.contains("iheartradio") || lower.contains("mp3") ||
            lower.contains("tidal")
    }

    private var cameraPackagesCache: Set<String>? = null

    /** Apps de cámara (la oficial y las que responden al intent de captura). */
    fun getCameraPackages(context: Context): Set<String> {
        cameraPackagesCache?.let { return it }
        val set = mutableSetOf(
            "org.codeaurora.snapcam", "com.sec.android.app.camera", "com.android.camera",
            "com.android.camera2", "com.miui.camera", "com.huawei.camera", "com.oppo.camera",
            "com.vivo.camera", "com.oplus.camera", "com.transsion.camera"
        )
        try {
            val pm = context.packageManager
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            infos.forEach { set.add(it.activityInfo.packageName) }
        } catch (_: Exception) {
        }
        cameraPackagesCache = set
        return set
    }

    /** Apps esenciales que NUNCA se bloquean en Modo Paseo (cámara, llamadas, SMS, maps...). */
    fun isEssentialWalkApp(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName) return true
        getDefaultDialerPackage(context)?.let { if (pkg == it) return true }
        getDefaultSmsPackage(context)?.let { if (pkg == it) return true }
        if (pkg in getCameraPackages(context)) return true
        val lower = pkg.lowercase()
        if (lower == "com.google.android.apps.maps" || lower.contains("maps")) return true
        if (lower == "com.google.android.contacts" || lower == "com.android.contacts" || lower.contains("contacts")) return true
        return false
    }

    /** RRSS, juegos, navegadores y apps dopamínicas que llevan límite de uso en Modo Paseo. */
    fun isDopamineWalkPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        val social = listOf(
            "facebook", "instagram", "tiktok", "snapchat", "twitter", "twitch", "reddit",
            "pinterest", "linkedin", "telegram", "youtube", "discord", "threads"
        )
        val gaming = listOf(
            "game", "games", "candy", "royale", "pubg", "codm", "genshin", "roblox",
            "fortnite", "minecraft", "clash", "brawl", "among"
        )
        val browsers = listOf(
            "chrome", "firefox", "edge", "samsung internet", "samsungbrowser", "opera",
            "miui.browser", "ucbrowser", "brave"
        )
        val casino = listOf("casino", "bet", "slot", "loto", "poker", "bingo")
        if (social.any { lower.contains(it) }) return true
        if (gaming.any { lower.contains(it) }) return true
        if (browsers.any { lower.contains(it) }) return true
        if (casino.any { lower.contains(it) }) return true
        if (lower.contains("browser")) return true
        return false
    }

    /** Clasifica una app dentro del Modo Paseo (orden de prioridad ESSENTIAL > WHATSAPP > MUSIC > DOPAMINE). */
    fun classifyWalkApp(context: Context, pkg: String): WalkAppCategory {
        if (isEssentialWalkApp(context, pkg)) return WalkAppCategory.ESSENTIAL
        if (isWhatsApp(pkg)) return WalkAppCategory.WHATSAPP
        if (isMusicPackage(pkg)) return WalkAppCategory.MUSIC
        if (isDopamineWalkPackage(pkg)) return WalkAppCategory.DOPAMINE
        return WalkAppCategory.FREE
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
