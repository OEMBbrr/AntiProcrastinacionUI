package com.antiprocrastinacion.lock.ui.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * V25: App Ciclo de Sueño del nuevo launcher (esqueleto navegable).
 * Estructura placeholder: despertadores con sonidos amigables y
 * calculadora de sueño (se implementa en una iteración posterior).
 */
@Composable
fun SleepCycleScreen(
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LchBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp)) // Dynamic Island

            // Cabecera
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = LchText
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Ciclo de Sueño",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LchText
                    )
                    Text(
                        text = "Despierta en la fase correcta",
                        fontSize = 11.sp,
                        color = LchMuted
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LchSurface),
                border = BorderStroke(1.dp, LchBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = LchAccent,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Calculadora de sueño",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = LchText
                    )
                    Text(
                        text = "Aquí podrás saber a qué hora poner la alarma para no despertarte en un sueño profundo.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = LchMuted,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Próximamente",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LchWarm
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LchSurface),
                border = BorderStroke(1.dp, LchBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Despertadores",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = LchText
                    )
                    Text(
                        text = "Alarmas con sonidos amigables, sin alertas bruscas. En construcción.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = LchMuted,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Próximamente",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LchWarm
                    )
                }
            }
        }
    }
}
