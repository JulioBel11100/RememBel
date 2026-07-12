package com.example.remembel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Activity invisible que existe solo para "rebotar": al aparecer (aunque
 * sea una fracción de segundo), cumple el requisito de Android 14+ de que
 * debe haber una pantalla visible para poder arrancar un servicio de
 * micrófono. Se usa desde el azulejo de Ajustes Rápidos, que por sí solo
 * no cuenta como "estado elegible".
 */
class GrabacionTrampolinActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (RecordingService.estaGrabando.value) {
            stopService(Intent(this, RecordingService::class.java))
        } else {
            startForegroundService(Intent(this, RecordingService::class.java))
        }

        finish()
    }
}