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
        // "Emisora" del estado de grabación: cualquiera puede suscribirse
        // y recibir automáticamente cada cambio, en el instante en que ocurre.
        private val _estaGrabando = MutableStateFlow(false)
        val estaGrabando: StateFlow<Boolean> = _estaGrabando
    }

    private var grabadorActual: MediaRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private val intervaloMs = 15 * 60 * 1000L // 15 minutos en milisegundos

    private val runnableCorte = object : Runnable{
        override fun run(){
            cortarYEmpezarNuevoTrozo()
            handler.postDelayed(this, intervaloMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1,crearNotification())

        // Si ya está grabando, ignoramos esta orden repetida en vez de arrancar un segundo grabador
        if (grabadorActual != null) {
            return START_STICKY
        }

        limpiarGrabacionesAntiguas()

        //Calculaos cuánto falta para el próximo múltiplo de 15 min
        val ahora = Calendar.getInstance()
        val minutoActual = ahora.get(Calendar.MINUTE)
        val minutosParaSiguienteCorte = 15 - (minutoActual % 15)
        val msParaSiguienteCorte = (minutosParaSiguienteCorte * 60 * 1000L) - (ahora.get(Calendar.SECOND) * 1000L)

        empezarGrabacion()
        handler.postDelayed(runnableCorte, msParaSiguienteCorte)

        return START_STICKY
    }
    private fun cortarYEmpezarNuevoTrozo(){
        detenerGrabacionAcual()
        empezarGrabacion()
        limpiarGrabacionesAntiguas()
    }
    private fun empezarGrabacion(){
        val carpeta = File(getExternalFilesDir(null), "grabaciones")
        if(!carpeta.exists()) carpeta.mkdirs()
        val formato = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val nombreArchivo = formato.format(Calendar.getInstance().time) + ".m4a"
        val archivo = File(carpeta, nombreArchivo)
        val calidad = ConfiguracionGrabacion.leerCalidad(this)
        val fuenteAudio = if (ConfiguracionGrabacion.leerVozClara(this)) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.MIC
        }

        grabadorActual = MediaRecorder().apply{
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
    private fun detenerGrabacionAcual(){
        grabadorActual?.apply{
            try{
                stop()
            }catch (e: Exception){
                // Si el trozo dura muy poco, stop() puede lanzar excepción; lo ignoramos
            }
            release()
        }
        grabadorActual = null
        _estaGrabando.value = false
    }
    /**
     * Borra los trozos de audio con más de 7 días de antigüedad.
     * Se llama cada vez que se crea un trozo nuevo, así la limpieza
     * ocurre sola sin necesidad de un mecanismo aparte.
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

            // Si no podemos leer la fecha del nombre, usamos la fecha de modificación del archivo como respaldo
            val momentoDelTrozo = inicioTrozo ?: archivo.lastModified()

            if (momentoDelTrozo < limiteMs) {
                archivo.delete()
            }
        }
    }
    override fun onDestroy(){
        super.onDestroy()
        handler.removeCallbacks(runnableCorte)
        detenerGrabacionAcual()
    }
    private fun crearNotification(): Notification{
        val canalId = "grabacion_channel"
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val canal = NotificationChannel(canalId,"Grabación en curso",NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
        return NotificationCompat.Builder(this, canalId)
            .setContentTitle("RememBel")
            .setContentText("Grabando audio...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}