package com.juliobel11100.remembel

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class PaginaGuia(
    val titulo: String,
    val descripcion: String,
    val icono: @Composable () -> Unit
)

@Composable
private fun paginasGuia(): List<PaginaGuia> = listOf(
    PaginaGuia(
        titulo = "Bienvenido a RememBel",
        descripcion = "Tu memoria de audio: graba en segundo plano y recupera exactamente lo que pasó en un momento concreto.",
        icono = {
            Icon(
                painter = painterResource(id = R.drawable.ic_logo_guia),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ),
    PaginaGuia(
        titulo = "Siempre visible cuando graba",
        descripcion = "Mientras RememBel está grabando, verás un aviso permanente en tu móvil y el propio Android muestra un indicador de micrófono en uso. Nunca graba a escondidas, ni para ti ni para nadie a tu alrededor.",
        icono = {
            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }
    ),
    PaginaGuia(
        titulo = "Elige cómo grabar",
        descripcion = "Constante (tú controlas cuándo), Horario fijo (cada día, sola), o Duración limitada (ahora mismo, durante el tiempo que digas).",
        icono = {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }
    ),
    PaginaGuia(
        titulo = "Recupera un momento",
        descripcion = "Elige el día y las horas de inicio y fin. RememBel busca y recompone el audio exacto de ese intervalo.",
        icono = {
            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }
    ),
    PaginaGuia(
        titulo = "Guarda en tu biblioteca",
        descripcion = "Ponle el nombre que quieras al audio recuperado y organízalo en carpetas, para siempre.",
        icono = {
            Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }
    ),
    PaginaGuia(
        titulo = "Atajo rápido, sin abrir la app",
        descripcion = "Desliza dos veces desde arriba del móvil para ver el panel de Ajustes Rápidos: ahí puedes añadir el azulejo de RememBel para empezar o parar de grabar con un solo toque.",
        icono = {
            Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }
    ),
    PaginaGuia(
        titulo = "Un consejo importante",
        descripcion = "En algunos móviles (sobre todo Xiaomi), desactiva las restricciones de batería para RememBel, para que la grabación no se corte.",
        icono = {
            Icon(Icons.Filled.BatteryAlert, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }
    )
)

@Composable
fun PantallaGuia(onCerrar: () -> Unit) {
    val paginas = paginasGuia()
    val pagerState = rememberPagerState(pageCount = { paginas.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCerrar) {
                Text("Saltar")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pagina ->
            val contenido = paginas[pagina]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    contenido.icono()
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    contenido.titulo,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    contenido.descripcion,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(paginas.size) { indice ->
                val esActual = indice == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (esActual) 10.dp else 8.dp)
                        .background(
                            if (esActual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1, animationSpec = tween(280))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Atrás")
                }
            }

            Button(
                onClick = {
                    if (pagerState.currentPage == paginas.lastIndex) {
                        onCerrar()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = tween(280))
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (pagerState.currentPage == paginas.lastIndex) "Empezar a usar RememBel" else "Siguiente")
            }
        }
    }
}