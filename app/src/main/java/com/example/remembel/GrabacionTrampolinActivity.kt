package com.example.remembel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Activity invisible que existe solo para "rebotar": al aparecer (aunque
 * sea una fracción de segundo), cumple el requisito de Android 14+ de que
 * debe haber una pantalla visible para poder arrancar un servicio de
 * micrófono. La usan tanto el azulejo (sin acción explícita: alterna) como
 * las alarmas de horario fijo (con acción explícita: inicia o para).
 */
class GrabacionTrampolinActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ACCION = "extra_accion"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent.getStringExtra(EXTRA_ACCION)) {
            GrabacionReceiver.ACCION_INICIAR -> {
                startForegroundService(Intent(this, RecordingService::class.java))
            }
            GrabacionReceiver.ACCION_DETENER -> {
                stopService(Intent(this, RecordingService::class.java))
            }
            else -> {
                // Sin acción explícita (caso del azulejo de Ajustes Rápidos): alternar.
                if (RecordingService.estaGrabando.value) {
                    stopService(Intent(this, RecordingService::class.java))
                } else {
                    startForegroundService(Intent(this, RecordingService::class.java))
                }
            }
        }

        finish()
    }
}