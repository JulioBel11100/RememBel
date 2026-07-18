package com.example.remembel

import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableIntStateOf
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PantallaAjustes(
    modifier: Modifier = Modifier,
    onVolver: () -> Unit
) {
    val context = LocalContext.current

    var modoElegido by remember { mutableStateOf(ConfiguracionGrabacion.leerModo(context)) }
    var horaInicioMin by remember { mutableIntStateOf(ConfiguracionGrabacion.leerHoraInicioMinutos(context)) }
    var horaFinMin by remember { mutableIntStateOf(ConfiguracionGrabacion.leerHoraFinMinutos(context)) }
    var duracionMin by remember { mutableIntStateOf(ConfiguracionGrabacion.leerDuracionLimitadaMinutos(context)) }
    var calidadElegida by remember { mutableStateOf(ConfiguracionGrabacion.leerCalidad(context)) }
    var retencionDias by remember { mutableIntStateOf(ConfiguracionGrabacion.leerRetencionDias(context)) }
    var vozClara by remember { mutableStateOf(ConfiguracionGrabacion.leerVozClara(context)) }
    var estiloElegido by remember { mutableStateOf(ConfiguracionGrabacion.leerEstilo(context)) }
    var temaElegido by remember { mutableStateOf(ConfiguracionGrabacion.leerTema(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Modo RememBel", style = MaterialTheme.typography.headlineSmall)

        ModoGrabacion.entries.forEach { modo ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = modoElegido == modo,
                    onClick = { modoElegido = modo }
                )
                Text(nombreLegible(modo))
            }
        }

        if (modoElegido == ModoGrabacion.HORARIO_FIJO) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    mostrarSelectorHora(context) { h, m -> horaInicioMin = h * 60 + m }
                }) {
                    Text("Desde: ${formatearMinutos(horaInicioMin)}")
                }
                OutlinedButton(onClick = {
                    mostrarSelectorHora(context) { h, m -> horaFinMin = h * 60 + m }
                }) {
                    Text("Hasta: ${formatearMinutos(horaFinMin)}")
                }
            }
        }

        if (modoElegido == ModoGrabacion.DURACION_LIMITADA) {
            Column {
                Text("Duración: ${formatearMinutos(duracionMin)}")
                Slider(
                    value = duracionMin.toFloat(),
                    onValueChange = { duracionMin = it.toInt() },
                    valueRange = 15f..480f
                )
            }
        }

        HorizontalDivider()
        Text("Calidad de audio", style = MaterialTheme.typography.titleMedium)

        CalidadAudio.entries.forEach { calidad ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = calidadElegida == calidad,
                    onClick = { calidadElegida = calidad }
                )
                Text("${calidad.etiqueta} — %.0f MB/hora".format(calidad.megasPorHora()))
            }
        }
        Text(
            "Ejemplo: 24 h grabadas ocupan %.1f GB. El cambio aplica al siguiente trozo (máx. 15 min)."
                .format(calidadElegida.megasPorHora() * 24 / 1000),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()
        Text("Conservar archivos: $retencionDias días", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = retencionDias.toFloat(),
            onValueChange = { retencionDias = it.toInt() },
            valueRange = 1f..30f
        )
        Text(
            "Espacio máximo estimado: %.1f GB (grabando 24 h/día)"
                .format(calidadElegida.megasPorHora() * 24 * retencionDias / 1000),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Voz más clara", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Capta la voz con menos filtrado. Puede aumentar el ruido de fondo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = vozClara, onCheckedChange = { vozClara = it })
        }

        HorizontalDivider()
        Text("Apariencia", style = MaterialTheme.typography.titleMedium)

        Text("Estilo", style = MaterialTheme.typography.bodyMedium)
        EstiloVisual.entries.forEach { estilo ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = estiloElegido == estilo, onClick = { estiloElegido = estilo })
                Text(if (estilo == EstiloVisual.ESENCIAL) "Esencial (claro, texto grande)" else "Vivo (moderno, animado)")
            }
        }

        Text("Tema", style = MaterialTheme.typography.bodyMedium)
        TemaApp.entries.forEach { tema ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = temaElegido == tema, onClick = { temaElegido = tema })
                Text(
                    when (tema) {
                        TemaApp.CLARO -> "Claro"
                        TemaApp.OSCURO -> "Oscuro"
                        TemaApp.SISTEMA -> "Seguir el sistema"
                    }
                )
            }
        }

        Button(
            onClick = {
                ConfiguracionGrabacion.guardarModo(context, modoElegido)
                ConfiguracionGrabacion.guardarCalidad(context, calidadElegida)
                ConfiguracionGrabacion.guardarRetencionDias(context, retencionDias)
                ConfiguracionGrabacion.guardarVozClara(context, vozClara)
                ConfiguracionGrabacion.guardarEstilo(context, estiloElegido)
                ConfiguracionGrabacion.guardarTema(context, temaElegido)
                AlarmScheduler.cancelarTodasLasAlarmas(context)

                when (modoElegido) {
                    ModoGrabacion.HORARIO_FIJO -> {
                        ConfiguracionGrabacion.guardarHorarioFijo(context, horaInicioMin, horaFinMin)
                        AlarmScheduler.programarHorarioFijo(context)
                        context.startForegroundService(
                            Intent(context, RecordingService::class.java)
                                .setAction(RecordingService.ACCION_HORARIO_STANDBY)
                        )
                    }
                    ModoGrabacion.DURACION_LIMITADA -> {
                        ConfiguracionGrabacion.guardarDuracionLimitadaMinutos(context, duracionMin)
                        context.startForegroundService(
                            Intent(context, RecordingService::class.java)
                        )
                        AlarmScheduler.programarDuracionLimitada(context)
                    }
                    ModoGrabacion.CONSTANTE -> {
                        // No hace falta programar nada: el usuario controla esto con los
                        // botones "Empezar"/"Detener" de la pantalla principal.
                    }
                }
                onVolver()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }

        OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar")
        }
    }
}

private fun nombreLegible(modo: ModoGrabacion): String = when (modo) {
    ModoGrabacion.CONSTANTE -> "Constante"
    ModoGrabacion.HORARIO_FIJO -> "Horario fijo diario"
    ModoGrabacion.DURACION_LIMITADA -> "Duración determinada"
}

private fun formatearMinutos(totalMin: Int): String {
    val h = totalMin / 60
    val m = totalMin % 60
    return "%02d:%02d".format(h, m)
}