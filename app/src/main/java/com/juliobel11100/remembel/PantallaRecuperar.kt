package com.juliobel11100.remembel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Punto único para recuperar audio: arriba, un intervalo a medida (día + hora
 * de inicio y fin, para recortar exactamente lo que quieras dentro de una
 * grabación); debajo, la lista de sesiones completas que ya existen en bruto
 * pero que el usuario aún no ha guardado en su biblioteca (se borran solas a
 * los 7 días si no se rescatan).
 */
@Composable
fun PantallaRecuperar(
    modifier: Modifier = Modifier,
    onVolver: () -> Unit,
    onSeleccionarPista: (PistaDisponible) -> Unit
) {
    val context = LocalContext.current
    var pistas by remember { mutableStateOf<List<PistaDisponible>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }

    var diaSeleccionado by remember { mutableStateOf<Calendar?>(null) }
    var horaInicio by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var horaFin by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var mensaje by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        pistas = withContext(Dispatchers.IO) { listarPistasDisponibles(context) }
        cargando = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onVolver) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            }
            Text(
                "Recuperar audio",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            "Elige un momento concreto",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        OutlinedButton(
            onClick = { mostrarSelectorFecha(context) { cal -> diaSeleccionado = cal } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.width(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (diaSeleccionado != null) formatearDia(diaSeleccionado!!) else "Elegir día")
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { mostrarSelectorHora(context) { h, m -> horaInicio = Pair(h, m) } },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (horaInicio != null) "${dosDigitos(horaInicio!!.first)}:${dosDigitos(horaInicio!!.second)}" else "Desde")
            }

            OutlinedButton(
                onClick = { mostrarSelectorHora(context) { h, m -> horaFin = Pair(h, m) } },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (horaFin != null) "${dosDigitos(horaFin!!.first)}:${dosDigitos(horaFin!!.second)}" else "Hasta")
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val dia = diaSeleccionado
                val inicio = horaInicio
                val fin = horaFin
                if (dia == null || inicio == null || fin == null) {
                    mensaje = "Elige día, hora de inicio y hora de fin."
                    return@Button
                }
                val inicioMs = combinarDiaYHora(dia, inicio)
                var finMs = combinarDiaYHora(dia, fin)
                if (finMs <= inicioMs) finMs += 24 * 60 * 60 * 1000L
                mensaje = ""
                onSeleccionarPista(PistaDisponible(inicioMs, finMs))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.width(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Recuperar y reproducir")
        }
        if (mensaje.isNotEmpty()) {
            Text(
                mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(
            "Pendientes de guardar",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when {
            cargando -> Text(
                "Buscando lo que tienes disponible...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            pistas.isEmpty() -> Text(
                "No hay nada pendiente: no se encontró audio en bruto todavía sin guardar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(pistas) { pista ->
                    ElementoPendiente(pista = pista, onClick = { onSeleccionarPista(pista) })
                }
            }
        }
    }
}

@Composable
private fun ElementoPendiente(pista: PistaDisponible, onClick: () -> Unit) {
    val diasRestantes = remember(pista) { diasHastaBorrado(pista.inicioMs) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatearDia(Calendar.getInstance().apply { timeInMillis = pista.inicioMs }),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "${formatearHoraDesdeMs(pista.inicioMs)} - ${formatearHoraDesdeMs(pista.finMs)}" +
                        "  ·  ${formatearDuracion(pista.finMs - pista.inicioMs)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    textoCaducidad(diasRestantes),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diasRestantes <= 1) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Recuperar y reproducir",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatearHoraDesdeMs(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return "${dosDigitos(cal.get(Calendar.HOUR_OF_DAY))}:${dosDigitos(cal.get(Calendar.MINUTE))}"
}

private fun formatearDuracion(ms: Long): String {
    val totalMinutos = (ms / 60_000).toInt()
    val horas = totalMinutos / 60
    val minutos = totalMinutos % 60
    return when {
        horas > 0 && minutos > 0 -> "$horas h $minutos min"
        horas > 0 -> "$horas h"
        totalMinutos > 0 -> "$minutos min"
        else -> "menos de 1 min"
    }
}

/** Días de retención (7) menos lo que ya ha pasado desde el inicio de la pista. */
private fun diasHastaBorrado(inicioMs: Long): Int {
    val diasDeRetencion = 7
    val diasPasados = ((System.currentTimeMillis() - inicioMs) / (24 * 60 * 60 * 1000L)).toInt()
    return (diasDeRetencion - diasPasados).coerceAtLeast(0)
}

private fun textoCaducidad(diasRestantes: Int): String = when {
    diasRestantes <= 0 -> "Caduca hoy"
    diasRestantes == 1 -> "Caduca mañana"
    else -> "Caduca en $diasRestantes días"
}
