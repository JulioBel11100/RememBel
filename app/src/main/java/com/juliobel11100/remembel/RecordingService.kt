package com.juliobel11100.remembel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class RecordingService : Service() {

    companion object {
        const val ACCION_HORARIO_INICIAR = "com.juliobel11100.remembel.SERVICIO_HORARIO_INICIAR"
        const val ACCION_HORARIO_DETENER = "com.juliobel11100.remembel.SERVICIO_HORARIO_DETENER"
        const val ACCION_HORARIO_STANDBY = "com.juliobel11100.remembel.SERVICIO_HORARIO_STANDBY"

        private const val BITRATE_FIJO = 128_000
        private val _estaGrabando = MutableStateFlow(false)
        val estaGrabando: StateFlow<Boolean> = _estaGrabando
    }

    private var grabadorActual: MediaRecorder? = null
    private var archivoActual: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private val intervaloMs = 15 * 60 * 1000L

    /** Serializa las conversiones .aac -> .m4a en segundo plano, sin bloquear la grabación. */
    private val ejecutorConversion = Executors.newSingleThreadExecutor()

    private val runnableCorte = object : Runnable {
        override fun run() {
            cortarYEmpezarNuevoTrozo()
            handler.postDelayed(this, intervaloMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, crearNotification())

        when (intent?.action) {
            ACCION_HORARIO_INICIAR -> {
                if (grabadorActual == null) {
                    limpiarGrabacionesAntiguas()
                    empezarGrabacion()
                    programarProximoCorte()
                }
                actualizarNotificacion()
                return START_STICKY
            }

            ACCION_HORARIO_DETENER -> {
                handler.removeCallbacks(runnableCorte)
                detenerGrabacionAcual()
                actualizarNotificacion()
                return START_STICKY
            }

            ACCION_HORARIO_STANDBY -> {
                if (dentroDeHorarioConfigurado()) {
                    if (grabadorActual == null) {
                        limpiarGrabacionesAntiguas()
                        empezarGrabacion()
                        programarProximoCorte()
                    }
                }
                actualizarNotificacion()
                return START_STICKY
            }

            else -> {
                if (grabadorActual != null) {
                    return START_STICKY
                }
                limpiarGrabacionesAntiguas()
                empezarGrabacion()
                programarProximoCorte()
                ConfiguracionGrabacion.guardarEstabaActivo(this, true)
                return START_STICKY
            }
        }
    }

    private fun programarProximoCorte() {
        val ahora = Calendar.getInstance()
        val minutoActual = ahora.get(Calendar.MINUTE)
        val minutosParaSiguienteCorte = 15 - (minutoActual % 15)
        val msParaSiguienteCorte =
            (minutosParaSiguienteCorte * 60 * 1000L) - (ahora.get(Calendar.SECOND) * 1000L)
        handler.postDelayed(runnableCorte, msParaSiguienteCorte)
    }

    private fun dentroDeHorarioConfigurado(): Boolean {
        val minutoInicio = ConfiguracionGrabacion.leerHoraInicioMinutos(this)
        val minutoFin = ConfiguracionGrabacion.leerHoraFinMinutos(this)
        val ahora = Calendar.getInstance()
        val minutoActual = ahora.get(Calendar.HOUR_OF_DAY) * 60 + ahora.get(Calendar.MINUTE)
        return if (minutoInicio <= minutoFin) {
            minutoActual in minutoInicio until minutoFin
        } else {
            minutoActual >= minutoInicio || minutoActual < minutoFin
        }
    }

    private fun cortarYEmpezarNuevoTrozo() {
        detenerGrabacionAcual()
        empezarGrabacion()
        limpiarGrabacionesAntiguas()
    }

    private fun empezarGrabacion() {
        val carpeta = File(getExternalFilesDir(null), "grabaciones")
        if (!carpeta.exists()) carpeta.mkdirs()

        val formato = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val nombreArchivo = formato.format(Calendar.getInstance().time) + ".aac"
        val archivo = File(carpeta, nombreArchivo)

        // Calidad y fuente de audio fijas: siempre la mejor combinación posible.

        val fuenteAudio = MediaRecorder.AudioSource.VOICE_RECOGNITION

        grabadorActual = MediaRecorder().apply {
            setAudioSource(fuenteAudio)
            // AAC_ADTS en vez de MPEG_4: un .m4a solo queda legible si stop() llega a
            // ejecutarse (el índice de duración se escribe al final). Si el sistema mata
            // el proceso a media grabación de un trozo (frecuente en capas como MIUI/
            // HyperOS), ese trozo se perdía entero. ADTS no tiene ese índice final: cada
            // fragmento de audio es autocontenido y legible aunque el archivo quede a
            // medias, así que como mucho se pierde el último fragmento sin volcar a disco.
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(BITRATE_FIJO)
            setAudioSamplingRate(44100)
            setOutputFile(archivo.absolutePath)
            prepare()
            start()
        }
        archivoActual = archivo
        _estaGrabando.value = true
    }

    private fun detenerGrabacionAcual() {
        val archivoQueTermina = archivoActual
        var paroLimpio = false
        grabadorActual?.apply {
            try {
                stop()
                paroLimpio = true
            } catch (e: Exception) {
                // Si el trozo dura muy poco, stop() puede lanzar excepción; lo ignoramos
            }
            release()
        }
        grabadorActual = null
        archivoActual = null
        _estaGrabando.value = false
        ConfiguracionGrabacion.guardarEstabaActivo(this, false)

        // Solo convertimos si stop() terminó limpio: si lanzó excepción, el .aac puede
        // estar incompleto y mejor dejarlo tal cual (ADTS sigue siendo legible aunque
        // esté a medias; un .m4a a medias no lo sería).
        if (paroLimpio && archivoQueTermina != null) {
            ejecutorConversion.execute { convertirAM4aYBorrarOriginal(archivoQueTermina) }
        }
    }

    /**
     * Borra los trozos de audio con más de 7 días de antigüedad (fijo), y de paso convierte
     * a .m4a cualquier trozo .aac ya cerrado (que no sea el que se está grabando ahora mismo)
     * que se haya quedado sin convertir — p.ej. trozos grabados con una versión anterior de
     * la app, antes de que existiera esta conversión automática.
     */
    private fun limpiarGrabacionesAntiguas() {
        val carpeta = File(getExternalFilesDir(null), "grabaciones")
        val archivos = carpeta.listFiles { f -> f.name.endsWith(".aac") || f.name.endsWith(".m4a") } ?: return

        val diasDeRetencion = 7L
        val limiteMs = System.currentTimeMillis() - (diasDeRetencion * 24 * 60 * 60 * 1000L)

        val formato = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())

        for (archivo in archivos) {
            val nombreSinExtension = archivo.name.removeSuffix(".aac").removeSuffix(".m4a")
            val inicioTrozo = try {
                formato.parse(nombreSinExtension)?.time
            } catch (e: Exception) {
                null
            }
            val momentoDelTrozo = inicioTrozo ?: archivo.lastModified()
            if (momentoDelTrozo < limiteMs) {
                archivo.delete()
            } else if (archivo.name.endsWith(".aac") && archivo != archivoActual) {
                ejecutorConversion.execute { convertirAM4aYBorrarOriginal(archivo) }
            }
        }
    }

    /**
     * Convierte un trozo .aac (ADTS) ya cerrado a .m4a indexado (MPEG_4). ADTS no tiene
     * índice de duración: leerla obliga a recorrer el archivo entero, lo que hace lenta la
     * recuperación de intervalos largos si se dejan muchos trozos en ese formato. Se ejecuta
     * en segundo plano y el .aac original solo se borra si la conversión termina con éxito,
     * así nunca hay una ventana sin ninguna copia válida del trozo.
     */
    private fun convertirAM4aYBorrarOriginal(origenAac: File) {
        if (!origenAac.exists()) return
        val destino = File(origenAac.parentFile, origenAac.nameWithoutExtension + ".m4a")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(origenAac.absolutePath)
            val pista = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return
            extractor.selectTrack(pista)

            muxer = MediaMuxer(destino.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val pistaSalida = muxer.addTrack(extractor.getTrackFormat(pista))
            muxer.start()

            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val tamano = extractor.readSampleData(buffer, 0)
                if (tamano < 0) break
                info.offset = 0
                info.size = tamano
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(pistaSalida, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            muxer = null

            if (destino.exists() && destino.length() > 0) {
                origenAac.delete()
            } else {
                destino.delete()
            }
        } catch (e: Exception) {
            muxer?.release()
            destino.delete()
        } finally {
            extractor.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnableCorte)
        detenerGrabacionAcual()
        ejecutorConversion.shutdown()
    }

    /**
     * Se dispara cuando el sistema retira la tarea de la app (p.ej. el usuario la desliza
     * fuera de recientes). En Android normal el servicio en primer plano sobrevive a esto y
     * sigue grabando; en capas como MIUI/HyperOS este gesto suele ir seguido de un cierre
     * agresivo del proceso. Por eso, si seguíamos grabando, cerramos el trozo actual y
     * arrancamos uno nuevo ya: así el trozo en curso queda bien finalizado si el proceso
     * muere justo después, y si no muere (Android normal), la grabación sigue sin más.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (grabadorActual != null) {
            cortarYEmpezarNuevoTrozo()
        }
    }

    private fun actualizarNotificacion() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, crearNotification())
    }

    private fun crearNotification(): Notification {
        val canalId = "grabacion_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                canalId, "Grabación en curso", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
        val texto =
            if (grabadorActual != null) "Guardando lo que pasa a tu alrededor" else "Listo para recordar cuando toque"
        val intentAbrirApp = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intentAbrirApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, canalId)
            .setContentTitle("RememBel")
            .setContentText(texto)
            .setSmallIcon(R.drawable.ic_notification_remembel)
            .setContentIntent(pendingIntent)
            .build()
    }
}