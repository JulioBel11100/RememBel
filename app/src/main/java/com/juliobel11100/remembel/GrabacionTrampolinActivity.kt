package com.juliobel11100.remembel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Activity invisible que existe solo para "rebotar": al aparecer (aunque sea
 * una fracción de segundo), cumple el requisito de Android de que debe haber
 * una pantalla visible para poder arrancar un servicio de micrófono desde un
 * contexto que no es una Activity (azulejo, alarmas). Se lanza también desde
 * la notificación de "reanudar grabación" tras un reinicio del móvil: desde
 * Android 15 no se puede arrancar un servicio de tipo "microphone" mientras
 * se procesa BOOT_COMPLETED (ni con esta Activity de por medio), así que
 * [ArranqueAutomaticoReceiver] ya no la lanza directamente — solo prepara un
 * PendingIntent hacia ella que se ejecuta cuando el usuario toca la
 * notificación, un gesto que sí está exento de esa restricción.
 *
 * [EXTRA_ACCION_SERVICIO] lleva directamente la acción que debe recibir
 * RecordingService (una de las constantes ACCION_HORARIO_*). Si no se pasa
 * ninguna, se interpreta como "sin acción explícita" (el caso del azulejo):
 * alternar entre grabar y parar según el estado actual.
 */
class GrabacionTrampolinActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ACCION_SERVICIO = "extra_accion_servicio"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (val accion = intent.getStringExtra(EXTRA_ACCION_SERVICIO)) {
            RecordingService.ACCION_HORARIO_DETENER -> {
                stopService(Intent(this, RecordingService::class.java))
            }

            null -> {
                // Sin acción explícita (caso del azulejo de Ajustes Rápidos): alternar.
                if (RecordingService.estaGrabando.value) {
                    stopService(Intent(this, RecordingService::class.java))
                } else {
                    startForegroundService(Intent(this, RecordingService::class.java))
                }
            }

            else -> {
                // ACCION_HORARIO_INICIAR, ACCION_HORARIO_STANDBY, etc.
                startForegroundService(
                    Intent(this, RecordingService::class.java).setAction(accion)
                )
            }
        }

        finish()
    }
}