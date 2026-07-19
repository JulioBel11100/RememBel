package com.example.remembel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.remembel.EstiloVisual
import com.example.remembel.TemaApp

private fun esquemaColores(estilo: EstiloVisual, esOscuro: Boolean) = when (estilo) {
    EstiloVisual.ESENCIAL if !esOscuro -> lightColorScheme(
        primary = EsencialClaroAcento,
        onPrimary = Color.White,
        background = EsencialClaroFondo,
        onBackground = EsencialClaroTextoPrimario,
        surface = EsencialClaroSuperficie,
        onSurface = EsencialClaroTextoPrimario,
        surfaceVariant = EsencialClaroSuperficie,
        onSurfaceVariant = EsencialClaroTextoSecundario,
        error = RojoAlerta
    )
    EstiloVisual.ESENCIAL if true -> darkColorScheme(
        primary = EsencialOscuroAcento,
        onPrimary = Color.Black,
        background = EsencialOscuroFondo,
        onBackground = EsencialOscuroTextoPrimario,
        surface = EsencialOscuroSuperficie,
        onSurface = EsencialOscuroTextoPrimario,
        surfaceVariant = EsencialOscuroSuperficie,
        onSurfaceVariant = EsencialOscuroTextoSecundario,
        error = RojoAlerta
    )
    EstiloVisual.VIVO if !esOscuro -> lightColorScheme(
        primary = VivoClaroAcento,
        onPrimary = Color.White,
        background = VivoClaroFondo,
        onBackground = VivoClaroTextoPrimario,
        surface = VivoClaroSuperficie,
        onSurface = VivoClaroTextoPrimario,
        surfaceVariant = VivoClaroFondo,
        onSurfaceVariant = VivoClaroTextoSecundario,
        error = RojoAlerta
    )
    else -> darkColorScheme(
        primary = VivoOscuroAcento,
        onPrimary = Color.Black,
        background = VivoOscuroFondo,
        onBackground = VivoOscuroTextoPrimario,
        surface = VivoOscuroSuperficie,
        onSurface = VivoOscuroTextoPrimario,
        surfaceVariant = VivoOscuroSuperficie,
        onSurfaceVariant = VivoOscuroTextoSecundario,
        error = RojoAlerta
    )
}

@Composable
fun RememBelTheme(
    estilo: EstiloVisual,
    tema: TemaApp,
    content: @Composable () -> Unit
) {
    val esOscuro = when (tema) {
        TemaApp.CLARO -> false
        TemaApp.OSCURO -> true
        TemaApp.SISTEMA -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = esquemaColores(estilo, esOscuro),
        typography = tipografiaPara(estilo),
        content = content
    )
}