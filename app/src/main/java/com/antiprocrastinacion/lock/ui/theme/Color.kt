package com.antiprocrastinacion.lock.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// PALETA CLARO (Zen, estilo actual)
// ---------------------------------------------------------------------------
val LightCreamBackground = Color(0xFFF9F8F6) // Blanco roto Zen suave
val LightZenWhite = Color(0xFFFFFFFF)        // Tarjetas blancas con volumen
val LightZenCharcoal = Color(0xFF2C302E)     // Texto principal oscuro zen
val LightZenSage = Color(0xFF8C9B90)         // Texto secundario gris verdoso
val LightZenOlive = Color(0xFF5A6E61)        // Verde oliva zen
val LightZenGreen = Color(0xFF4E8752)        // Verde de exito
val LightZenCoral = Color(0xFFD97D75)        // Rojo coral de error

val LightPrimario = Color(0xFF5A6E61)
val LightSecundario = Color(0xFF7B8D82)
val LightTerciario = Color(0xFF8F7E7A)

// ---------------------------------------------------------------------------
// PALETA OSCURO (replica el modo oscuro de la página web: #0F171E, #F1F5F9)
// ---------------------------------------------------------------------------
val DarkCreamBackground = Color(0xFF0F171E) // --bg-dark de la web
val DarkZenWhite = Color(0xFF1B252E)        // Tarjetas: rgba(255,255,255,0.04) sobre fondo
val DarkZenCharcoal = Color(0xFFF1F5F9)     // --text-main de la web
val DarkZenSage = Color(0xFF94A3B8)         // --text-muted de la web
val DarkZenOlive = Color(0xFF5F7564)        // --zen-olive-bright de la web
val DarkZenGreen = Color(0xFF62A06D)        // Verde de exito mas claro para contraste
val DarkZenCoral = Color(0xFFE57373)        // --zen-coral de la web

val DarkPrimario = Color(0xFF5F7564)
val DarkSecundario = Color(0xFF7B8D82)
val DarkTerciario = Color(0xFF8F7E7A)

// Fondos suaves de tarjetas (claros) -> variantes oscuras
val DarkZenSoftCoral = Color(0xFF2A1D1B)
val DarkZenSoftBlue = Color(0xFF16202A)
val DarkZenSoftGreen = Color(0xFF16231A)

// ---------------------------------------------------------------------------
// PRESETS DE ACENTO (V25): el usuario elige el color del sistema en Ajustes
// ---------------------------------------------------------------------------
data class AccentPreset(
    val key: String,
    val name: String,
    val light: Color,
    val dark: Color
)

object AccentPresets {
    val OLIVA = AccentPreset("oliva", "Oliva", LightZenOlive, DarkZenOlive)
    val SAGE = AccentPreset("sage", "Salvia", Color(0xFF7B8D82), Color(0xFF94A3B8))
    val CORAL = AccentPreset("coral", "Coral", Color(0xFFD97D75), Color(0xFFE57373))
    val VERDE = AccentPreset("verde", "Verde", Color(0xFF4E8752), Color(0xFF62A06D))
    val GRIS = AccentPreset("gris", "Gris", Color(0xFF5E6159), Color(0xFF8F9488))
    val ARENA = AccentPreset("arena", "Arena", Color(0xFF8F7E7A), Color(0xFFB0A396))

    val all: List<AccentPreset> = listOf(OLIVA, SAGE, CORAL, VERDE, GRIS, ARENA)

    fun byKey(key: String?): AccentPreset = all.firstOrNull { it.key == key } ?: OLIVA
}

// ---------------------------------------------------------------------------
// Estado global del tema (modo claro / oscuro + acento del sistema)
// ---------------------------------------------------------------------------
object ZenTheme {
    var isDark by mutableStateOf(false)
    var accent by mutableStateOf(AccentPresets.OLIVA)
}

// ---------------------------------------------------------------------------
// Colores expuestos a las pantallas (mismos nombres de siempre)
// Se leen a traves del estado ZenTheme.isDark para que el modo oscuro
// se propague a todas las pantallas sin cambiar el codigo de cada una.
// ---------------------------------------------------------------------------
val CreamBackground: Color
    get() = if (ZenTheme.isDark) DarkCreamBackground else LightCreamBackground

val ZenWhite: Color
    get() = if (ZenTheme.isDark) DarkZenWhite else LightZenWhite

val ZenCharcoal: Color
    get() = if (ZenTheme.isDark) DarkZenCharcoal else LightZenCharcoal

val ZenSage: Color
    get() = if (ZenTheme.isDark) DarkZenSage else LightZenSage

val ZenOlive: Color
    get() = if (ZenTheme.isDark) ZenTheme.accent.dark else ZenTheme.accent.light

val ZenGreen: Color
    get() = if (ZenTheme.isDark) DarkZenGreen else LightZenGreen

val ZenCoral: Color
    get() = if (ZenTheme.isDark) DarkZenCoral else LightZenCoral

val Primario: Color
    get() = if (ZenTheme.isDark) ZenTheme.accent.dark else ZenTheme.accent.light

val Secundario: Color
    get() = if (ZenTheme.isDark) DarkSecundario else LightSecundario

val Terciario: Color
    get() = if (ZenTheme.isDark) DarkTerciario else LightTerciario

// Superficie elevada y bordes (para tarjetas del launcher y widgets)
val ZenSurfaceElevated: Color
    get() = if (ZenTheme.isDark) Color(0xFF212B35) else Color(0xFFFFFFFF)

val ZenBorderLight: Color
    get() = if (ZenTheme.isDark) Color(0xFF3A4654) else Color(0xFFE0DDD5)

// Fondos suaves de tarjetas según modo
val ZenSoftCoral: Color
    get() = if (ZenTheme.isDark) DarkZenSoftCoral else Color(0xFFFDF5F2)

val ZenSoftBlue: Color
    get() = if (ZenTheme.isDark) DarkZenSoftBlue else Color(0xFFF0F4F8)

val ZenSoftGreen: Color
    get() = if (ZenTheme.isDark) DarkZenSoftGreen else Color(0xFFE8F5E9)
