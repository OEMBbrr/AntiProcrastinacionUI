package com.antiprocrastinacion.lock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ZenColorScheme = lightColorScheme(
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

@Composable
fun AntiProcrastinacionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ZenColorScheme,
        typography = Typography,
        content = content
    )
}
