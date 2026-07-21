package com.juliobel11100.remembel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Escucha las alarmas de horario fijo y envía el aviso al RecordingService,
 * que YA ESTÁ VIVO (arrancado desde Ajustes con la app abierta) — por eso
 * este simple startService() basta, sin necesitar ningún truco de
 * "Activity trampolín": el servicio no necesita volver a "nacer".
 */
class GrabacionReceiver : BroadcastReceiver() {

    companion object {
        const val ACCION_INICIAR = "com.juliobel11100.remembel.ACCION_INICIAR"
        const val ACCION_DETENER = "com.juliobel11100.remembel.ACCION_DETENER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val accionServicio = when (intent.action) {
            ACCION_INICIAR -> RecordingService.ACCION_HORARIO_INICIAR
            ACCION_DETENER -> RecordingService.ACCION_HORARIO_DETENER
            else -> return
        }

        context.startService(
            Intent(context, RecordingService::class.java).setAction(accionServicio)
        )

        if (ConfiguracionGrabacion.leerModo(context) == ModoGrabacion.HORARIO_FIJO) {
            AlarmScheduler.programarHorarioFijo(context)
        }
    }
}