package com.example.remembel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.remembel.EstiloVisual

fun tipografiaPara(estilo: EstiloVisual): Typography {
    val escala = if (estilo == EstiloVisual.ESENCIAL) 1.15f else 0.9f

    return Typography(
        headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = (24 * escala).sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = (28 * escala).sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = (22 * escala).sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = (18 * escala).sp),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (17 * escala).sp,
            lineHeight = (25 * escala).sp
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (15 * escala).sp,
            lineHeight = (22 * escala).sp
        ),
        bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = (13 * escala).sp)
    )
}