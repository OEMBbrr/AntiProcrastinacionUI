package com.antiprocrastinacion.lock

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * V30: escala de grises PROPIA de la app.
 *
 * Desatura SOLO la interfaz de AntiProcrastinación (home, cajón de aplicaciones y
 * pantalla de bloqueo del Modo Enfoque) mediante RenderEffect. No requiere permisos
 * del sistema ni afecta a las demás apps.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppGrayscaleWrapper(enabled: Boolean, content: @Composable () -> Unit) {
    val effect = remember(enabled) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val matrix = android.graphics.ColorMatrix()
            matrix.setSaturation(0f)
            val androidEffect = android.graphics.RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(matrix)
            )
            androidEffect.asComposeRenderEffect()
        } else {
            null
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = effect }
    ) { content() }
}
