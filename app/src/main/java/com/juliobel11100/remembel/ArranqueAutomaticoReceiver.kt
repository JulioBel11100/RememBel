package com.juliobel11100.remembel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe el aviso de que el móvil ha terminado de arrancar, y reactiva
 * la grabación automática según el modo y el estado que hubiera guardados.
 */
class ArranqueAutomaticoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        when (ConfiguracionGrabacion.leerModo(context)) {
            ModoGrabacion.CONSTANTE -> {
                if (ConfiguracionGrabacion.leerEstabaActivo(context)) {
                    context.startForegroundService(Intent(context, RecordingService::class.java))
                }
            }

            ModoGrabacion.HORARIO_FIJO -> {
                AlarmScheduler.programarHorarioFijo(context)
                context.startForegroundService(
                    Intent(context, RecordingService::class.java)
                        .setAction(RecordingService.ACCION_HORARIO_STANDBY)
                )
            }

            ModoGrabacion.DURACION_LIMITADA -> {
                // Una cuenta atrás no tiene sentido retomarla tras un reinicio;
                // el usuario debe volver a activarla conscientemente.
            }
        }
    }
}