package com.example.remembel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Escucha las alarmas programadas por AlarmScheduler y arranca/para
 * el servicio de grabación en consecuencia.
 */
class GrabacionReceiver : BroadcastReceiver() {

    companion object {
        const val ACCION_INICIAR = "com.example.remembel.ACCION_INICIAR"
        const val ACCION_DETENER = "com.example.remembel.ACCION_DETENER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCION_INICIAR -> {
                context.startForegroundService(Intent(context, RecordingService::class.java))
            }
            ACCION_DETENER -> {
                context.stopService(Intent(context, RecordingService::class.java))
            }
        }
    }
}
