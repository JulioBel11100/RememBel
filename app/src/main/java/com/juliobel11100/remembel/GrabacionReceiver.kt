package com.juliobel11100.remembel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Escucha las alarmas de horario fijo y envía el aviso al RecordingService.
 *
 * Ojo: NO se puede pasar por [GrabacionTrampolinActivity] aquí (como hacen la
 * Tile o la notificación de reanudar tras reinicio). Aquello funciona porque
 * son gestos directos del usuario (toque en la Tile, toque en la
 * notificación), que están exentos de las restricciones de "Background
 * Activity Launch" de Android 10+. Una alarma de AlarmManager disparándose
 * en segundo plano NO tiene esa excepción: un startActivity() a pelo desde
 * este receiver se bloquea en silencio y la Activity nunca llega a abrirse,
 * así que RecordingService jamás recibe la orden (bug real: ver commit
 * "Arreglar horario fijo cuando el servicio muere en segundo plano", que
 * introdujo justo este problema).
 *
 * Lo que sí está permitido — y es la excepción documentada que hace útiles
 * a las alarmas exactas — es arrancar un foreground service directamente
 * desde el receiver cuando el disparo viene de
 * AlarmManager.setExactAndAllowWhileIdle()/setAlarmClock(), que es como
 * [AlarmScheduler] las programa. Por eso volvemos a hablar directo con el
 * servicio, sin intermediarios. (La única vez que un arranque de servicio de
 * tipo "microphone" SÍ está bloqueado incluso así es al procesar
 * BOOT_COMPLETED en Android 15+; ese caso lo cubre aparte
 * [ArranqueAutomaticoReceiver] con una notificación que hay que tocar.)
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

        context.startForegroundService(
            Intent(context, RecordingService::class.java).setAction(accionServicio)
        )

        // Solo se reprograma la alarma que acaba de disparar, para su ocurrencia del día
        // siguiente. La otra alarma (la que aún no ha sonado) ya está armada para hoy desde
        // la vez anterior — volver a registrarla aquí es innecesario y, en la práctica,
        // reprogramar la misma alarma dos veces en poco tiempo hace que capas como MIUI la
        // descarten en silencio (comprobado en dispositivo: la de fin dejó de sonar tras ser
        // re-armada por partida doble en una ventana de prueba corta).
        if (ConfiguracionGrabacion.leerModo(context) == ModoGrabacion.HORARIO_FIJO) {
            when (intent.action) {
                ACCION_INICIAR -> AlarmScheduler.programarProximoInicio(context)
                ACCION_DETENER -> AlarmScheduler.programarProximoFin(context)
            }
        }
    }
}