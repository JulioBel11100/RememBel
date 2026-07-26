package com.juliobel11100.remembel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Escucha las alarmas de horario fijo y envía el aviso al RecordingService.
 * No se puede asumir que el servicio siga vivo desde que se activó el
 * horario en Ajustes: el sistema puede haber matado el proceso mientras
 * tanto (gestión de batería, app cerrada desde Recientes...), y en ese caso
 * un startService() a pelo desde este receiver dispara el mismo bloqueo de
 * Android 14+ que motivó [GrabacionTrampolinActivity] para la Tile: no se
 * puede arrancar un servicio foreground de tipo "microphone" desde un
 * contexto sin Activity visible. Por eso se pasa siempre por la Activity
 * trampolín, igual que hacen la Tile y el arranque tras reinicio.
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

        context.startActivity(
            Intent(context, GrabacionTrampolinActivity::class.java)
                .putExtra(GrabacionTrampolinActivity.EXTRA_ACCION_SERVICIO, accionServicio)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        if (ConfiguracionGrabacion.leerModo(context) == ModoGrabacion.HORARIO_FIJO) {
            AlarmScheduler.programarHorarioFijo(context)
        }
    }
}