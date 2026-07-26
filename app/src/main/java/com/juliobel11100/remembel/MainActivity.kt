package com.juliobel11100.remembel

import java.io.File
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.juliobel11100.remembel.ui.theme.RememBelTheme
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Pantalla {
    PRINCIPAL, BIBLIOTECA, RECUPERAR
}

class MainActivity : ComponentActivity() {

    private val solicitarPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val lanzadorActualizacion = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()
        pedirPermisosNecesarios()
        ActualizadorApp.comprobar(this)

        setContent {
            RememBelTheme(estilo = EstiloVisual.VIVO, tema = TemaApp.OSCURO) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var pantallaActual by rememberSaveable { mutableStateOf(Pantalla.PRINCIPAL) }
                    var pistaARecuperar by remember { mutableStateOf<PistaDisponible?>(null) }

                    AnimatedContent(
                        targetState = pantallaActual,
                        transitionSpec = {
                            if (targetState != Pantalla.PRINCIPAL) {
                                (slideInHorizontally(tween(280)) { ancho -> ancho } + fadeIn(tween(280)))
                                    .togetherWith(slideOutHorizontally(tween(280)) { ancho -> -ancho / 4 } + fadeOut(tween(280)))
                            } else {
                                (slideInHorizontally(tween(280)) { ancho -> -ancho / 4 } + fadeIn(tween(280)))
                                    .togetherWith(slideOutHorizontally(tween(280)) { ancho -> ancho } + fadeOut(tween(280)))
                            }
                        },
                        label = "navegacion"
                    ) { pantalla ->
                        when (pantalla) {
                            Pantalla.BIBLIOTECA -> PantallaBiblioteca(
                                modifier = Modifier.padding(innerPadding),
                                onVolver = { pantallaActual = Pantalla.PRINCIPAL }
                            )
                            Pantalla.RECUPERAR -> PantallaRecuperar(
                                modifier = Modifier.padding(innerPadding),
                                onVolver = { pantallaActual = Pantalla.PRINCIPAL },
                                onSeleccionarPista = { pista ->
                                    pistaARecuperar = pista
                                    pantallaActual = Pantalla.PRINCIPAL
                                }
                            )
                            Pantalla.PRINCIPAL -> PantallaPrincipal(
                                modifier = Modifier.padding(innerPadding),
                                onIniciar = { iniciarServicio() },
                                onDetener = { detenerServicio() },
                                onAbrirBiblioteca = { pantallaActual = Pantalla.BIBLIOTECA },
                                onAbrirRecuperar = { pantallaActual = Pantalla.RECUPERAR },
                                pistaARecuperar = pistaARecuperar,
                                onPistaConsumida = { pistaARecuperar = null },
                                onActualizarAhora = { ActualizadorApp.iniciarDescarga(lanzadorActualizacion) },
                                onCompletarActualizacion = { ActualizadorApp.completarActualizacion() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun pedirPermisosNecesarios() {
        val permisos = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val faltantes = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltantes.isNotEmpty()) {
            solicitarPermisos.launch(faltantes.toTypedArray())
        }
    }

    private fun iniciarServicio() {
        // Por si quedaba pendiente una alarma de "duración limitada" de una sesión
        // anterior que se paró a mano: si no se cancela, cortaría esta grabación
        // nueva sin motivo cuando llegara su hora.
        AlarmScheduler.cancelarDuracionLimitada(this)
        startForegroundService(Intent(this, RecordingService::class.java))
    }

    private fun detenerServicio() {
        AlarmScheduler.cancelarDuracionLimitada(this)
        stopService(Intent(this, RecordingService::class.java))
    }

    override fun onResume() {
        super.onResume()
        // Si la descarga en segundo plano terminó (o quedó a medias) mientras
        // la app no estaba en primer plano, esto lo detecta al volver.
        ActualizadorApp.comprobar(this)
    }
}

@Composable
private fun Modifier.escalaAlPulsar(interactionSource: MutableInteractionSource): Modifier {
    val estaPulsado by interactionSource.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (estaPulsado) 0.96f else 1f,
        animationSpec = tween(120),
        label = "escalaPulsacion"
    )
    return this.graphicsLayer {
        scaleX = escala
        scaleY = escala
    }
}

@Composable
fun PantallaPrincipal(
    modifier: Modifier = Modifier,
    onIniciar: () -> Unit,
    onDetener: () -> Unit,
    onAbrirBiblioteca: () -> Unit,
    onAbrirRecuperar: () -> Unit,
    pistaARecuperar: PistaDisponible? = null,
    onPistaConsumida: () -> Unit = {},
    onActualizarAhora: () -> Unit = {},
    onCompletarActualizacion: () -> Unit = {}
) {
    val context = LocalContext.current

    var mostrarGuia by remember { mutableStateOf(!ConfiguracionGrabacion.leerGuiaVista(context)) }

    if (mostrarGuia) {
        PantallaGuia(onCerrar = {
            mostrarGuia = false
            ConfiguracionGrabacion.guardarGuiaVista(context, true)
        })
        return
    }

    var mensaje by remember { mutableStateOf("") }
    var mensajeModo by remember { mutableStateOf("") }

    var reproductor by remember { mutableStateOf<MediaPlayer?>(null) }
    var archivoTemporalRecuperado by remember { mutableStateOf<File?>(null) }
    var tramoActual by remember { mutableStateOf<TramoRecuperado?>(null) }
    var mostrarDialogoGuardar by remember { mutableStateOf(false) }
    var estaSonando by remember { mutableStateOf(false) }
    var posicionMs by remember { mutableStateOf(0) }
    var duracionMs by remember { mutableStateOf(0) }
    var velocidad by remember { mutableStateOf(1f) }
    var formaDeOnda by remember { mutableStateOf<List<Float>>(emptyList()) }
    val estaGrabando by RecordingService.estaGrabando.collectAsState()
    val hayActualizacionDisponible by ActualizadorApp.hayActualizacionDisponible.collectAsState()
    val actualizacionLista by ActualizadorApp.actualizacionLista.collectAsState()
    var actualizacionDescartada by remember { mutableStateOf(false) }

    var modoElegido by remember { mutableStateOf(ConfiguracionGrabacion.leerModo(context)) }
    var horaInicioMin by remember { mutableIntStateOf(ConfiguracionGrabacion.leerHoraInicioMinutos(context)) }
    var horaFinMin by remember { mutableIntStateOf(ConfiguracionGrabacion.leerHoraFinMinutos(context)) }
    var duracionMin by remember { mutableIntStateOf(ConfiguracionGrabacion.leerDuracionLimitadaMinutos(context)) }
    var restanteMs by remember { mutableStateOf(0L) }

    LaunchedEffect(estaSonando) {
        while (estaSonando) {
            reproductor?.let { posicionMs = it.currentPosition }
            delay(200)
        }
    }

    LaunchedEffect(estaGrabando) {
        while (estaGrabando) {
            val finMs = ConfiguracionGrabacion.leerFinDuracionLimitada(context)
            restanteMs = (finMs - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(1000)
        }
        restanteMs = 0L
    }

    DisposableEffect(Unit) {
        onDispose { reproductor?.release() }
    }

    suspend fun recuperarYReproducir(inicioMs: Long, finMs: Long) {
        mensaje = "Buscando y recomponiendo audio..."
        val tramos = withContext(Dispatchers.IO) {
            recuperarIntervalo(context, inicioMs, finMs)
        }

        if (tramos.isEmpty()) {
            mensaje = "No se encontró audio para ese intervalo."
        } else {
            val primerTramo = tramos.first()
            tramoActual = primerTramo
            archivoTemporalRecuperado = primerTramo.archivo
            reproductor?.release()
            velocidad = 1f
            reproductor = MediaPlayer().apply {
                setDataSource(primerTramo.archivo.absolutePath)
                setOnCompletionListener { estaSonando = false }
                prepare()
                duracionMs = duration
                posicionMs = 0
                start()
                playbackParams = playbackParams.setSpeed(1f)
            }
            estaSonando = true
            mensaje = if (tramos.size == 1) {
                "Intervalo: ${formatoHoraDesdeMs(primerTramo.inicioMs)} a ${formatoHoraDesdeMs(primerTramo.finMs)}"
            } else {
                "Hay ${tramos.size} tramos distintos en ese intervalo (con huecos sin grabar entre ellos). " +
                    "Reproduciendo el primero; el resto puedes recuperarlos por separado desde \"Pendientes de guardar\"."
            }
        }
    }

    LaunchedEffect(pistaARecuperar) {
        val pista = pistaARecuperar ?: return@LaunchedEffect
        recuperarYReproducir(pista.inicioMs, pista.finMs)
        onPistaConsumida()
    }

    LaunchedEffect(archivoTemporalRecuperado) {
        val archivo = archivoTemporalRecuperado
        formaDeOnda = if (archivo != null) {
            withContext(Dispatchers.IO) { extraerFormaDeOnda(archivo) }
        } else {
            emptyList()
        }
    }

    fun cambiarVelocidad(nuevaVelocidad: Float) {
        try {
            reproductor?.let { mp ->
                mp.playbackParams = mp.playbackParams.setSpeed(nuevaVelocidad)
            }
            velocidad = nuevaVelocidad
        } catch (e: Exception) {
            // Si el reproductor no está en un estado válido, ignoramos el cambio.
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_notification_remembel),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "RememBel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { mostrarGuia = true }) {
                Icon(Icons.Filled.Info, contentDescription = "Cómo funciona RememBel")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (estaGrabando) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (estaGrabando) 4.dp else 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PuntoDeGrabacion(estaGrabando = estaGrabando)
                    Text(
                        if (estaGrabando) "Recordando..." else "En pausa",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (estaGrabando && restanteMs > 0) {
                    Text(
                        "Quedan ${formatearRestante(restanteMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val interactionEmpezar = remember { MutableInteractionSource() }
                    Button(
                        onClick = { onIniciar() },
                        enabled = !estaGrabando,
                        interactionSource = interactionEmpezar,
                        modifier = Modifier.weight(1f).escalaAlPulsar(interactionEmpezar)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_notification_remembel), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Empezar", maxLines = 1)
                    }

                    val interactionDetener = remember { MutableInteractionSource() }
                    Button(
                        onClick = { onDetener() },
                        enabled = estaGrabando,
                        interactionSource = interactionDetener,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.weight(1f).escalaAlPulsar(interactionDetener)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Detener", maxLines = 1)
                    }
                }

                HorizontalDivider()
                Text("¿Cómo quieres que grabe?", style = MaterialTheme.typography.titleSmall)

                ModoGrabacion.entries.forEach { modo ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = modoElegido == modo,
                            onClick = {
                                modoElegido = modo
                                ConfiguracionGrabacion.guardarModo(context, modo)
                                AlarmScheduler.cancelarTodasLasAlarmas(context)
                                mensajeModo = ""
                            }
                        )
                        Text(nombreLegible(modo))
                    }
                }

                if (modoElegido == ModoGrabacion.HORARIO_FIJO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val interactionDesde = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = { mostrarSelectorHora(context) { h, m -> horaInicioMin = h * 60 + m } },
                            interactionSource = interactionDesde,
                            modifier = Modifier.escalaAlPulsar(interactionDesde)
                        ) {
                            Text("Desde: ${formatearMinutos(horaInicioMin)}")
                        }
                        val interactionHasta = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = { mostrarSelectorHora(context) { h, m -> horaFinMin = h * 60 + m } },
                            interactionSource = interactionHasta,
                            modifier = Modifier.escalaAlPulsar(interactionHasta)
                        ) {
                            Text("Hasta: ${formatearMinutos(horaFinMin)}")
                        }
                    }

                    val interactionActivarHorario = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            ConfiguracionGrabacion.guardarHorarioFijo(context, horaInicioMin, horaFinMin)
                            AlarmScheduler.programarHorarioFijo(context)
                            context.startForegroundService(
                                Intent(context, RecordingService::class.java)
                                    .setAction(RecordingService.ACCION_HORARIO_STANDBY)
                            )
                            mensajeModo = "✓ Horario activado: de ${formatearMinutos(horaInicioMin)} a ${formatearMinutos(horaFinMin)}, cada día"
                        },
                        interactionSource = interactionActivarHorario,
                        modifier = Modifier.fillMaxWidth().escalaAlPulsar(interactionActivarHorario)
                    ) {
                        Text("Activar horario fijo")
                    }
                    if (mensajeModo.isNotEmpty()) {
                        Text(mensajeModo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (modoElegido == ModoGrabacion.DURACION_LIMITADA) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Duración: ${formatearMinutos(duracionMin)}")
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            SelectorNumerico(
                                etiqueta = "Horas",
                                valor = duracionMin / 60,
                                sufijo = "h",
                                puedeDecrementar = duracionMin - 60 >= 1,
                                puedeIncrementar = duracionMin + 60 <= 480,
                                onDecrementar = { duracionMin -= 60 },
                                onIncrementar = { duracionMin += 60 }
                            )
                            SelectorNumerico(
                                etiqueta = "Minutos",
                                valor = duracionMin % 60,
                                sufijo = "min",
                                puedeDecrementar = duracionMin - 1 >= 1,
                                puedeIncrementar = duracionMin + 1 <= 480,
                                onDecrementar = { duracionMin -= 1 },
                                onIncrementar = { duracionMin += 1 }
                            )
                        }
                    }

                    val interactionGrabarTiempo = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            ConfiguracionGrabacion.guardarDuracionLimitadaMinutos(context, duracionMin)
                            context.startForegroundService(Intent(context, RecordingService::class.java))
                            AlarmScheduler.programarDuracionLimitada(context)
                            mensajeModo = "✓ Grabando ahora durante ${formatearMinutos(duracionMin)}"
                        },
                        interactionSource = interactionGrabarTiempo,
                        modifier = Modifier.fillMaxWidth().escalaAlPulsar(interactionGrabarTiempo)
                    ) {
                        Text("Grabar durante este tiempo")
                    }
                    if (mensajeModo.isNotEmpty()) {
                        Text(mensajeModo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val interactionRecuperar = remember { MutableInteractionSource() }
            Button(
                onClick = onAbrirRecuperar,
                interactionSource = interactionRecuperar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.weight(1f).escalaAlPulsar(interactionRecuperar)
            ) {
                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recuperar audio", maxLines = 1)
            }

            val interactionBiblioteca = remember { MutableInteractionSource() }
            Button(
                onClick = onAbrirBiblioteca,
                interactionSource = interactionBiblioteca,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                modifier = Modifier.weight(1f).escalaAlPulsar(interactionBiblioteca)
            ) {
                Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Biblioteca", maxLines = 1)
            }
        }

        if (mensaje.isNotEmpty()) {
            Text(
                mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (reproductor != null && duracionMs > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${formatearTiempo(posicionMs)} / ${formatearTiempo(duracionMs)}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    FormaDeOndaSlider(
                        barras = formaDeOnda,
                        progreso = if (duracionMs > 0) posicionMs / duracionMs.toFloat() else 0f,
                        onSeek = { fraccion ->
                            val nuevaPosicion = (fraccion * duracionMs).toInt()
                            reproductor?.seekTo(nuevaPosicion)
                            posicionMs = nuevaPosicion
                        },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        IconButton(onClick = {
                            val nuevaPos = (posicionMs - 10_000).coerceAtLeast(0)
                            reproductor?.seekTo(nuevaPos)
                            posicionMs = nuevaPos
                        }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Retroceder 10 segundos")
                        }

                        IconButton(
                            onClick = {
                                if (estaSonando) {
                                    reproductor?.pause()
                                    estaSonando = false
                                } else {
                                    reproductor?.start()
                                    estaSonando = true
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (estaSonando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (estaSonando) "Pausar" else "Reproducir",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = {
                            val nuevaPos = (posicionMs + 10_000).coerceAtMost(duracionMs)
                            reproductor?.seekTo(nuevaPos)
                            posicionMs = nuevaPos
                        }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Avanzar 10 segundos")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { opcion ->
                            val seleccionada = velocidad == opcion
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { cambiarVelocidad(opcion) }
                            ) {
                                Text(
                                    "${opcion}x".replace(".0x", "x"),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (seleccionada) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val interactionGuardarBiblioteca = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { mostrarDialogoGuardar = true },
                        interactionSource = interactionGuardarBiblioteca,
                        modifier = Modifier.fillMaxWidth().escalaAlPulsar(interactionGuardarBiblioteca)
                    ) {
                        Text("Guardar en biblioteca")
                    }
                }
            }
        }
    }

    if (mostrarDialogoGuardar) {
        val diaDelTramo = remember(tramoActual) {
            tramoActual?.let { Calendar.getInstance().apply { timeInMillis = it.inicioMs } }
                ?: Calendar.getInstance()
        }
        var nombreElegido by remember {
            mutableStateOf(tramoActual?.let { nombrePorHoraMs(it.inicioMs, it.finMs) } ?: "")
        }
        AlertDialog(
            onDismissRequest = { mostrarDialogoGuardar = false },
            title = { Text("Guardar audio") },
            text = {
                Column {
                    Text(
                        "Se guardará en la carpeta del día ${formatearDia(diaDelTramo)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nombreElegido,
                        onValueChange = { nombreElegido = it },
                        label = { Text("Nombre") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val origen = archivoTemporalRecuperado
                    if (origen != null && nombreElegido.isNotBlank()) {
                        val carpetaDelDia = obtenerCarpetaDia(context, diaDelTramo)
                        val destino = File(carpetaDelDia, "${nombreElegido.trim()}.m4a")
                        if (destino.exists()) {
                            mensaje = "Ya existe un audio llamado \"${nombreElegido.trim()}\" ese día. Elige otro nombre."
                        } else {
                            origen.copyTo(destino, overwrite = false)
                            mensaje = "Guardado en \"${carpetaDelDia.name}/${nombreElegido.trim()}\""
                            mostrarDialogoGuardar = false
                        }
                    } else {
                        mostrarDialogoGuardar = false
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoGuardar = false }) { Text("Cancelar") }
            }
        )
    }

    if (hayActualizacionDisponible && !actualizacionDescartada) {
        AlertDialog(
            onDismissRequest = { actualizacionDescartada = true },
            title = { Text("Actualización disponible") },
            text = { Text("Hay una nueva versión de RememBel. Se descargará en segundo plano sin interrumpir la grabación.") },
            confirmButton = {
                TextButton(onClick = {
                    actualizacionDescartada = true
                    onActualizarAhora()
                }) { Text("Actualizar") }
            },
            dismissButton = {
                TextButton(onClick = { actualizacionDescartada = true }) { Text("Más tarde") }
            }
        )
    }

    if (actualizacionLista) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Actualización lista") },
            text = { Text("La nueva versión ya se descargó. Reinicia la app para aplicarla.") },
            confirmButton = {
                TextButton(onClick = onCompletarActualizacion) { Text("Reiniciar ahora") }
            }
        )
    }
}

@Composable
private fun PuntoDeGrabacion(estaGrabando: Boolean) {
    val transicionInfinita = rememberInfiniteTransition(label = "pulso")
    val escalaHalo by transicionInfinita.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "escalaHalo"
    )
    val opacidadHalo by transicionInfinita.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "opacidadHalo"
    )

    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (estaGrabando) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        scaleX = escalaHalo
                        scaleY = escalaHalo
                        alpha = opacidadHalo
                    }
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    if (estaGrabando) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun SelectorNumerico(
    etiqueta: String,
    valor: Int,
    sufijo: String,
    puedeDecrementar: Boolean,
    puedeIncrementar: Boolean,
    onDecrementar: () -> Unit,
    onIncrementar: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onDecrementar,
                enabled = puedeDecrementar,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "Menos $etiqueta" }
            ) {
                Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "${dosDigitos(valor)} $sufijo",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(54.dp)
            )
            IconButton(
                onClick = onIncrementar,
                enabled = puedeIncrementar,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "Más $etiqueta" }
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun nombreLegible(modo: ModoGrabacion): String = when (modo) {
    ModoGrabacion.CONSTANTE -> "Constante"
    ModoGrabacion.HORARIO_FIJO -> "Horario fijo"
    ModoGrabacion.DURACION_LIMITADA -> "Duración limitada"
}

private fun formatearMinutos(totalMin: Int): String {
    val h = totalMin / 60
    val m = totalMin % 60
    return "%02d:%02d".format(h, m)
}

private fun formatearRestante(ms: Long): String {
    val totalMinutos = (ms / 60_000L).toInt()
    val horas = totalMinutos / 60
    val minutos = totalMinutos % 60
    return "$horas:${dosDigitos(minutos)}"
}

private fun formatearTiempo(ms: Int): String {
    val totalSegundos = ms / 1000
    val minutos = totalSegundos / 60
    val segundos = totalSegundos % 60
    return "${dosDigitos(minutos)}:${dosDigitos(segundos)}"
}

internal fun mostrarSelectorFecha(
    context: android.content.Context,
    alElegir: (Calendar) -> Unit
) {
    val ahora = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val cal = Calendar.getInstance()
            cal.set(year, month, day)
            alElegir(cal)
        },
        ahora.get(Calendar.YEAR),
        ahora.get(Calendar.MONTH),
        ahora.get(Calendar.DAY_OF_MONTH)
    ).show()
}

internal fun mostrarSelectorHora(
    context: android.content.Context,
    alElegir: (Int, Int) -> Unit
) {
    val ahora = Calendar.getInstance()
    TimePickerDialog(
        context,
        { _, hora, minuto -> alElegir(hora, minuto) },
        ahora.get(Calendar.HOUR_OF_DAY),
        ahora.get(Calendar.MINUTE),
        true
    ).show()
}

internal fun combinarDiaYHora(dia: Calendar, hora: Pair<Int, Int>): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, dia.get(Calendar.YEAR))
    cal.set(Calendar.MONTH, dia.get(Calendar.MONTH))
    cal.set(Calendar.DAY_OF_MONTH, dia.get(Calendar.DAY_OF_MONTH))
    cal.set(Calendar.HOUR_OF_DAY, hora.first)
    cal.set(Calendar.MINUTE, hora.second)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun formatearDia(cal: Calendar): String {
    val formato = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
    return formato.format(cal.time)
}

fun dosDigitos(n: Int): String = if (n < 10) "0$n" else "$n"

private fun formatoHoraDesdeMs(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return "${dosDigitos(cal.get(Calendar.HOUR_OF_DAY))}-${dosDigitos(cal.get(Calendar.MINUTE))}"
}

private fun nombrePorHoraMs(inicioMs: Long, finMs: Long): String =
    "${formatoHoraDesdeMs(inicioMs)} a ${formatoHoraDesdeMs(finMs)}"