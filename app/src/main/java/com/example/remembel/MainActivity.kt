package com.example.remembel

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import java.io.File
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button

import androidx.compose.material3.Scaffold
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
import androidx.core.content.ContextCompat
import com.example.remembel.ui.theme.RememBelTheme
import java.util.Calendar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



private enum class Pantalla {
    PRINCIPAL, AJUSTES, BIBLIOTECA
}
class MainActivity : ComponentActivity() {

    private val solicitarPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pedirPermisosNecesarios()

        setContent {
            var estilo by remember { mutableStateOf(ConfiguracionGrabacion.leerEstilo(this)) }
            var tema by remember { mutableStateOf(ConfiguracionGrabacion.leerTema(this)) }

            RememBelTheme(estilo = estilo, tema = tema) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var pantallaActual by rememberSaveable { mutableStateOf(Pantalla.PRINCIPAL) }

                    AnimatedContent(
                        targetState = pantallaActual,
                        transitionSpec = {
                            if (targetState != Pantalla.PRINCIPAL) {
                                // Entrando a una subpantalla: la nueva llega desde la derecha,
                                // la anterior se aparta hacia la izquierda.
                                (slideInHorizontally(tween(280)) { ancho -> ancho } + fadeIn(tween(280)))
                                    .togetherWith(slideOutHorizontally(tween(280)) { ancho -> -ancho / 4 } + fadeOut(tween(280)))
                            } else {
                                // Volviendo a Principal: entra desde la izquierda,
                                // la subpantalla se va hacia la derecha.
                                (slideInHorizontally(tween(280)) { ancho -> -ancho / 4 } + fadeIn(tween(280)))
                                    .togetherWith(slideOutHorizontally(tween(280)) { ancho -> ancho } + fadeOut(tween(280)))
                            }
                        },
                        label = "navegacion"
                    ) { pantalla ->
                        when (pantalla) {
                            Pantalla.AJUSTES -> PantallaAjustes(
                                modifier = Modifier.padding(innerPadding),
                                onVolver = {
                                    pantallaActual = Pantalla.PRINCIPAL
                                    estilo = ConfiguracionGrabacion.leerEstilo(this@MainActivity)
                                    tema = ConfiguracionGrabacion.leerTema(this@MainActivity)
                                }
                            )
                            Pantalla.BIBLIOTECA -> PantallaBiblioteca(
                                modifier = Modifier.padding(innerPadding),
                                onVolver = { pantallaActual = Pantalla.PRINCIPAL }
                            )
                            Pantalla.PRINCIPAL -> PantallaPrincipal(
                                modifier = Modifier.padding(innerPadding),
                                onIniciar = { iniciarServicio() },
                                onDetener = { detenerServicio() },
                                onAbrirAjustes = { pantallaActual = Pantalla.AJUSTES },
                                onAbrirBiblioteca = { pantallaActual = Pantalla.BIBLIOTECA }
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
        val intent = Intent(this, RecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun detenerServicio() {
        val intent = Intent(this, RecordingService::class.java)
        stopService(intent)
    }
}

@Composable
fun PantallaPrincipal(
    modifier: Modifier = Modifier,
    onIniciar: () -> Unit,
    onDetener: () -> Unit,
    onAbrirAjustes: () -> Unit,
    onAbrirBiblioteca: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var diaSeleccionado by remember { mutableStateOf<Calendar?>(null) }
    var horaInicio by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var horaFin by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var mensaje by remember { mutableStateOf("") }

    var reproductor by remember { mutableStateOf<MediaPlayer?>(null) }
    var archivoTemporalRecuperado by remember { mutableStateOf<File?>(null) }
    var mostrarDialogoGuardar by remember { mutableStateOf(false) }
    var mensajeGuardado by remember { mutableStateOf("") }
    var estaSonando by remember { mutableStateOf(false) }
    var posicionMs by remember { mutableStateOf(0) }
    var duracionMs by remember { mutableStateOf(0) }
    var velocidad by remember { mutableStateOf(1f) }
    val estaGrabando by RecordingService.estaGrabando.collectAsState()

    LaunchedEffect(estaSonando) {
        while (estaSonando) {
            reproductor?.let { posicionMs = it.currentPosition }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose { reproductor?.release() }
    }

    fun cambiarVelocidad(nuevaVelocidad: Float) {
        try {
            reproductor?.let { mp ->
                mp.playbackParams = mp.playbackParams.setSpeed(nuevaVelocidad)
            }
            velocidad = nuevaVelocidad
        } catch (e: Exception) {
            // Si el reproductor no está en un estado válido (pausado, p.ej.),
            // ignoramos el cambio en vez de crashear.
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Cabecera ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onAbrirBiblioteca) {
                    Icon(Icons.Filled.Folder, contentDescription = "Biblioteca")
                }
                IconButton(onClick = onAbrirAjustes) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                }
            }
        }
        Text(
            "RememBel",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Tu memoria de audio",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- Tarjeta: Grabación ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (estaGrabando) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (estaGrabando) "Grabando..." else "En pausa",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val interactionEmpezar = remember { MutableInteractionSource() }
                    Button(
                        onClick = { onIniciar() },
                        enabled = !estaGrabando,
                        interactionSource = interactionEmpezar,
                        modifier = Modifier.escalaAlPulsar(interactionEmpezar)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Empezar")
                    }

                    val interactionDetener = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { onDetener() },
                        enabled = estaGrabando,
                        interactionSource = interactionDetener,
                        modifier = Modifier.escalaAlPulsar(interactionDetener)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Detener")
                    }
                }
            }
        }

        // ---- Tarjeta: Recuperar audio ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Recuperar audio", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = { mostrarSelectorFecha(context) { cal -> diaSeleccionado = cal } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (diaSeleccionado != null) formatearDia(diaSeleccionado!!) else "Elegir día")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { mostrarSelectorHora(context) { h, m -> horaInicio = Pair(h, m) } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (horaInicio != null) "${dosDigitos(horaInicio!!.first)}:${dosDigitos(horaInicio!!.second)}" else "Desde")
                    }

                    OutlinedButton(
                        onClick = { mostrarSelectorHora(context) { h, m -> horaFin = Pair(h, m) } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (horaFin != null) "${dosDigitos(horaFin!!.first)}:${dosDigitos(horaFin!!.second)}" else "Hasta")
                    }
                }
                val interactionRecuperar = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        if (diaSeleccionado == null || horaInicio == null || horaFin == null) {
                            mensaje = "Elige día, hora de inicio y hora de fin."
                            return@Button
                        }
                        val inicioMs = combinarDiaYHora(diaSeleccionado!!, horaInicio!!)
                        var finMs = combinarDiaYHora(diaSeleccionado!!, horaFin!!)

                        // Si la hora de fin "parece" anterior o igual a la de inicio, asumimos
                        // que el intervalo cruza la medianoche y en realidad termina al día siguiente.
                        if (finMs <= inicioMs) {
                            finMs += 24 * 60 * 60 * 1000L
                        }

                        mensaje = "Buscando y recomponiendo audio..."

                        scope.launch {
                            val resultado = withContext(Dispatchers.IO) {
                                recuperarIntervalo(context, inicioMs, finMs)
                            }

                            if (resultado == null) {
                                mensaje = "No se encontró audio para ese intervalo."
                            } else {
                                mensaje = "Intervalo: ${dosDigitos(horaInicio!!.first)}:${dosDigitos(horaInicio!!.second)} " +
                                        "a ${dosDigitos(horaFin!!.first)}:${dosDigitos(horaFin!!.second)}"
                                archivoTemporalRecuperado = resultado
                                reproductor?.release()
                                velocidad = 1f
                                reproductor = MediaPlayer().apply {
                                    setDataSource(resultado.absolutePath)
                                    setOnCompletionListener { estaSonando = false }
                                    prepare()
                                    duracionMs = duration
                                    posicionMs = 0
                                    start()
                                    playbackParams = playbackParams.setSpeed(1f)
                                }
                                estaSonando = true
                            }
                        }
                    },
                    interactionSource = interactionRecuperar,
                    modifier = Modifier.fillMaxWidth().escalaAlPulsar(interactionRecuperar)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Recuperar y reproducir")
                }
                if (mensaje.isNotEmpty()) {
                    Text(
                        mensaje,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- Tarjeta: Reproductor (solo si hay algo cargado) ----
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

                    Slider(
                        value = posicionMs.toFloat(),
                        onValueChange = { nuevaPosicion ->
                            reproductor?.seekTo(nuevaPosicion.toInt())
                            posicionMs = nuevaPosicion.toInt()
                        },
                        valueRange = 0f..duracionMs.toFloat()
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

                    OutlinedButton(
                        onClick = { mostrarDialogoGuardar = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar en biblioteca")
                    }
                }
            }
        }
    }
    if (mostrarDialogoGuardar) {
        var nombreElegido by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mostrarDialogoGuardar = false },
            title = { Text("Guardar audio") },
            text = {
                OutlinedTextField(
                    value = nombreElegido,
                    onValueChange = { nombreElegido = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val origen = archivoTemporalRecuperado
                    if (origen != null && nombreElegido.isNotBlank()) {
                        val carpetaBiblioteca = obtenerCarpetaBiblioteca(context)
                        val destino = File(carpetaBiblioteca, "${nombreElegido.trim()}.m4a")

                        if (destino.exists()) {
                            mensaje = "Ya existe un audio llamado \"${nombreElegido.trim()}\". Elige otro nombre."
                        } else {
                            origen.copyTo(destino, overwrite = false)
                            mensaje = "Guardado en biblioteca como \"${nombreElegido.trim()}\""
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

// -------- Funciones auxiliares de interfaz --------

private fun formatearTiempo(ms: Int): String {
    val totalSegundos = ms / 1000
    val minutos = totalSegundos / 60
    val segundos = totalSegundos % 60
    return "${dosDigitos(minutos)}:${dosDigitos(segundos)}"
}
private fun mostrarSelectorFecha(
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
        true // formato 24h
    ).show()
}

private fun combinarDiaYHora(dia: Calendar, hora: Pair<Int, Int>): Long {
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

private fun formatearDia(cal: Calendar): String {
    val formato = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
    return formato.format(cal.time)
}

private fun dosDigitos(n: Int): String = if (n < 10) "0$n" else "$n"

