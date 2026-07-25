package com.juliobel11100.remembel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Recibe el aviso de que el móvil ha terminado de arrancar, y reactiva
 * la grabación automática según el modo y el estado que hubiera guardados.
 *
 * Importante: a partir de Android 15, el sistema no permite iniciar un
 * servicio en primer plano de tipo "microphone" mientras se procesa un
 * BOOT_COMPLETED, ni siquiera a través de una Activity puente visible: el
 * intento se descarta (ForegroundServiceStartNotAllowedException). Por eso
 * aquí ya no se arranca la grabación de forma automática: se muestra una
 * notificación que, al tocarla, sí puede arrancar el servicio, porque en
 * ese momento es un gesto directo del usuario y no una consecuencia del
 * arranque del móvil.
 */
class ArranqueAutomaticoReceiver : BroadcastReceiver() {

    companion object {
        private const val CANAL_REANUDAR = "reanudar_grabacion_channel"
        private const val ID_NOTIFICACION_REANUDAR = 2
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        when (ConfiguracionGrabacion.leerModo(context)) {
            ModoGrabacion.CONSTANTE -> {
                if (ConfiguracionGrabacion.leerEstabaActivo(context)) {
                    mostrarNotificacionReanudar(context, null)
                }
            }

            ModoGrabacion.HORARIO_FIJO -> {
                AlarmScheduler.programarHorarioFijo(context)
                mostrarNotificacionReanudar(context, RecordingService.ACCION_HORARIO_STANDBY)
            }

            ModoGrabacion.DURACION_LIMITADA -> {
                // Una cuenta atrás no tiene sentido retomarla tras un reinicio;
                // el usuario debe volver a activarla conscientemente.
            }
        }
    }

    /** [accionServicio] null equivale a un arranque "constante" simple, sin acción. */
    private fun mostrarNotificacionReanudar(context: Context, accionServicio: String?) {
        val intentTrampolin = Intent(context, GrabacionTrampolinActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (accionServicio != null) {
                putExtra(GrabacionTrampolinActivity.EXTRA_ACCION_SERVICIO, accionServicio)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ID_NOTIFICACION_REANUDAR,
            intentTrampolin,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CANAL_REANUDAR, "Reanudar grabación", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val notificacion = NotificationCompat.Builder(context, CANAL_REANUDAR)
            .setContentTitle("RememBel")
            .setContentText("Tu móvil se ha reiniciado. Toca para reanudar la grabación.")
            .setSmallIcon(R.drawable.ic_notification_remembel)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(ID_NOTIFICACION_REANUDAR, notificacion)
    }
}
