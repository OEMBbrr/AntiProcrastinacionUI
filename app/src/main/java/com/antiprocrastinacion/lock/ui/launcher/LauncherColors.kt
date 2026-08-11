package com.antiprocrastinacion.lock.ui.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// PALETA DESATURADA DEL NUEVO LAUNCHER (V25)
// Colores apagados, fríos y cálidos cercanos a la escala de grises para evitar
// la estimulación dopamínica por color. Sin colores saturados.
// ---------------------------------------------------------------------------

// Fondo principal (gris cálido apagado)
val LchBgLight = Color(0xFFE9E8E5)
val LchBgDark = Color(0xFF121212)

// Superficies / tarjetas (apenas un paso sobre el fondo)
val LchSurfaceLight = Color(0xFFF3F2EF)
val LchSurfaceDark = Color(0xFF1D1D1C)

// Superficie elevada / header
val LchSurfaceHiLight = Color(0xFFFAF9F7)
val LchSurfaceHiDark = Color(0xFF262626)

// Texto principal y secundario (grises neutros)
val LchTextLight = Color(0xFF33302E)
val LchTextDark = Color(0xFFEDEDEA)

val LchMutedLight = Color(0xFF7A7874)
val LchMutedDark = Color(0xFFA3A29E)

// Acento desaturado (verde oliva/gris frío, único tono ligeramente cálido)
val LchAccentLight = Color(0xFF5E6159)
val LchAccentDark = Color(0xFF8F9488)

// Acento secundario (gris cálido tipo coral apagado para énfasis suave)
val LchWarmLight = Color(0xFF7A6F66)
val LchWarmDark = Color(0xFFB0A396)

// Bordes
val LchBorderLight = Color(0xFFD6D4D0)
val LchBorderDark = Color(0xFF343432)

// Estado global del launcher (claro / oscuro)
object LauncherTheme {
    var isDark by mutableStateOf(false)
}

// Colores expuestos a las pantallas del launcher según el tema
val LchBg: Color
    get() = if (LauncherTheme.isDark) LchBgDark else LchBgLight

val LchSurface: Color
    get() = if (LauncherTheme.isDark) LchSurfaceDark else LchSurfaceLight

val LchSurfaceHi: Color
    get() = if (LauncherTheme.isDark) LchSurfaceHiDark else LchSurfaceHiLight

val LchText: Color
    get() = if (LauncherTheme.isDark) LchTextDark else LchTextLight

val LchMuted: Color
    get() = if (LauncherTheme.isDark) LchMutedDark else LchMutedLight

val LchAccent: Color
    get() = if (LauncherTheme.isDark) LchAccentDark else LchAccentLight

val LchWarm: Color
    get() = if (LauncherTheme.isDark) LchWarmDark else LchWarmLight

val LchBorder: Color
    get() = if (LauncherTheme.isDark) LchBorderDark else LchBorderLight
