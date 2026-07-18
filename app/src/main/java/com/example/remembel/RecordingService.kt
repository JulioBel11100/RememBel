package com.example.remembel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Handler
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RecordingService : Service() {

    companion object {
        const val ACCION_HORARIO_INICIAR = "com.example.remembel.SERVICIO_HORARIO_INICIAR"
        const val ACCION_HORARIO_DETENER = "com.example.remembel.SERVICIO_HORARIO_DETENER"
        const val ACCION_HORARIO_STANDBY = "com.example.remembel.SERVICIO_HORARIO_STANDBY"

        // "Emisora" del estado de grabación: cualquiera puede suscribirse
        // y recibir automáticamente cada cambio, en el instante en que ocurre.
        private val _estaGrabando = MutableStateFlow(false)
        val estaGrabando: StateFlow<Boolean> = _estaGrabando
    }

    private var grabadorActual: MediaRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private val intervaloMs = 15 * 60 * 1000L // 15 minutos en milisegundos

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
                // El servicio ya estaba vivo en espera; solo arrancamos a grabar.
                if (grabadorActual == null) {
                    limpiarGrabacionesAntiguas()
                    empezarGrabacion()
                    programarProximoCorte()
                }
                actualizarNotificacion()
                return START_STICKY
            }
            ACCION_HORARIO_DETENER -> {
                // Paramos de grabar, pero el servicio SIGUE VIVO en espera del
                // próximo inicio (no llamamos a stopSelf ni matamos nada).
                handler.removeCallbacks(runnableCorte)
                detenerGrabacionAcual()
                actualizarNotificacion()
                return START_STICKY
            }
            ACCION_HORARIO_STANDBY -> {
                // Arranque inicial del modo Horario fijo: si ahora mismo estamos
                // dentro de la franja configurada, empezamos a grabar ya;
                // si no, el servicio se queda vivo mostrando "en espera".
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
                // Arranque manual normal (modo Constante o Duración limitada).
                if (grabadorActual != null) {
                    return START_STICKY
                }
                limpiarGrabacionesAntiguas()
                empezarGrabacion()
                programarProximoCorte()
                return START_STICKY
            }
        }
    }

    private fun programarProximoCorte() {
        val ahora = Calendar.getInstance()
        val minutoActual = ahora.get(Calendar.MINUTE)
        val minutosParaSiguienteCorte = 15 - (minutoActual % 15)
        val msParaSiguienteCorte = (minutosParaSiguienteCorte * 60 * 1000L) - (ahora.get(Calendar.SECOND) * 1000L)
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
            // Horario que cruza medianoche (ej. 22:00 a 06:00)
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
        val nombreArchivo = formato.format(Calendar.getInstance().time) + ".m4a"
        val archivo = File(carpeta, nombreArchivo)

        val calidad = ConfiguracionGrabacion.leerCalidad(this)
        val fuenteAudio = if (ConfiguracionGrabacion.leerVozClara(this)) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.MIC
        }

        grabadorActual = MediaRecorder().apply {
            setAudioSource(fuenteAudio)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(calidad.bitrate)
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
    }

    /**
     * Borra los trozos de audio más antiguos que los días de retención configurados.
     */
    private fun limpiarGrabacionesAntiguas() {
        val carpeta = File(getExternalFilesDir(null), "grabaciones")
        val archivos = carpeta.listFiles { f -> f.name.endsWith(".m4a") } ?: return

        val diasDeRetencion = ConfiguracionGrabacion.leerRetencionDias(this).toLong()
        val limiteMs = System.currentTimeMillis() - (diasDeRetencion * 24 * 60 * 60 * 1000L)

        val formato = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())

        for (archivo in archivos) {
            val nombreSinExtension = archivo.name.removeSuffix(".m4a")
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
        val texto = if (grabadorActual != null) "Grabando audio..." else "En espera de horario fijo"
        return NotificationCompat.Builder(this, canalId)
            .setContentTitle("RememBel")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}