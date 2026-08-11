package com.antiprocrastinacion.lock.ui.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.antiprocrastinacion.lock.ui.theme.CreamBackground
import com.antiprocrastinacion.lock.ui.theme.ZenBorderLight
import com.antiprocrastinacion.lock.ui.theme.ZenCharcoal
import com.antiprocrastinacion.lock.ui.theme.ZenCoral
import com.antiprocrastinacion.lock.ui.theme.ZenOlive
import com.antiprocrastinacion.lock.ui.theme.ZenSage
import com.antiprocrastinacion.lock.ui.theme.ZenSurfaceElevated
import com.antiprocrastinacion.lock.ui.theme.ZenTheme
import com.antiprocrastinacion.lock.ui.theme.ZenWhite

// ---------------------------------------------------------------------------
// LAUNCHER (V25) — V26: colores unificados con el modo ENFOQUE (paleta Zen).
// El launcher ya NO usa su propia paleta desaturada: delega en la paleta Zen
// del sistema y en el ACENTO configurable elegido en Ajustes (Colores).
// Así el acento cambia TODO el sistema a la vez (launcher, notas, modo enfoque).
// ---------------------------------------------------------------------------

// Estado global del launcher (claro / oscuro) delegado al tema Zen
object LauncherTheme {
    var isDark by mutableStateOf(false)
}

// Colores expuestos a las pantallas del launcher (misma paleta que modo enfoque)
val LchBg: Color
    get() = CreamBackground

val LchSurface: Color
    get() = ZenWhite

val LchSurfaceHi: Color
    get() = ZenSurfaceElevated

val LchText: Color
    get() = ZenCharcoal

val LchMuted: Color
    get() = ZenSage

val LchAccent: Color
    get() = ZenOlive

val LchWarm: Color
    get() = ZenCoral

val LchBorder: Color
    get() = ZenBorderLight

val LchGradientStart: Color
    get() = ZenSurfaceElevated

val LchGradientEnd: Color
    get() = CreamBackground

val LchOnAccent: Color
    get() = if (ZenTheme.isDark) Color(0xFF0F171E) else Color.White
