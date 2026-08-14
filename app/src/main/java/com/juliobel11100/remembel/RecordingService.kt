package com.juliobel11100.remembel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    private val handler = Handler(Looper.getMainLooper())
    private val intervaloMs = 15 * 60 * 1000L

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
        _estaGrabando.value = true
    }

    private fun detenerGrabacionAcual() {
        grabadorActual?.apply {
            try {
                stop()
            } catch (e: Exception) {
                // Si el trozo dura muy poco, stop() puede lanzar excepción; lo ignoramos
            }
            release()
        }
        grabadorActual = null
        _estaGrabando.value = false
        ConfiguracionGrabacion.guardarEstabaActivo(this, false)
    }

    /**
     * Borra los trozos de audio con más de 7 días de antigüedad (fijo).
     * Reconoce tanto ".aac" (formato actual) como ".m4a" (trozos que puedan quedar de una
     * versión anterior de la app, para que no se queden huérfanos ocupando espacio para siempre).
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
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnableCorte)
        detenerGrabacionAcual()
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