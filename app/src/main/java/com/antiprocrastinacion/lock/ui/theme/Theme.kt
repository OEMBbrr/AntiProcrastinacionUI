package com.antiprocrastinacion.lock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

@Composable
fun AntiProcrastinacionTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Propagar el modo oscuro al estado global para que todas las pantallas
    // lean los colores correctos (CreamBackground, ZenWhite, etc.)
    SideEffect {
        ZenTheme.isDark = darkTheme
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Primario,
            secondary = Secundario,
            tertiary = Terciario,
            background = CreamBackground,
            surface = ZenWhite,
            onPrimary = ZenWhite,
            onSecondary = ZenWhite,
            onBackground = ZenCharcoal,
            onSurface = ZenCharcoal
        )
    } else {
        lightColorScheme(
            primary = Primario,
            secondary = Secundario,
            tertiary = Terciario,
            background = CreamBackground,
            surface = ZenWhite,
            onPrimary = ZenWhite,
            onSecondary = ZenWhite,
            onBackground = ZenCharcoal,
            onSurface = ZenCharcoal
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
