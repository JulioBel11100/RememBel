package com.example.remembel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Responsable único de programar y cancelar las alarmas del sistema
 * que arrancan/paran la grabación automáticamente.
 */
object AlarmScheduler {

    private const val CODIGO_INICIO = 100
    private const val CODIGO_FIN = 101
    private const val CODIGO_FIN_DURACION = 102

    private fun crearPendingIntent(context: Context, accion: String, codigo: Int): PendingIntent {
        val intent = Intent(context, GrabacionReceiver::class.java).apply {
            action = accion
        }
        return PendingIntent.getBroadcast(
            context,
            codigo,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Dado "09:30" en minutos (570), calcula el próximo Calendar futuro con esa hora exacta. */
    private fun proximaOcurrenciaDe(minutosDelDia: Int): Calendar {
        val ahora = Calendar.getInstance()
        val objetivo = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutosDelDia / 60)
            set(Calendar.MINUTE, minutosDelDia % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (objetivo.before(ahora)) {
            objetivo.add(Calendar.DAY_OF_YEAR, 1)
        }
        return objetivo
    }

    fun programarHorarioFijo(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val minutoInicio = ConfiguracionGrabacion.leerHoraInicioMinutos(context)
        val momentoInicio = proximaOcurrenciaDe(minutoInicio)
        programarAlarmaExacta(
            alarmManager,
            momentoInicio.timeInMillis,
            crearPendingIntent(context, GrabacionReceiver.ACCION_INICIAR, CODIGO_INICIO)
        )

        val minutoFin = ConfiguracionGrabacion.leerHoraFinMinutos(context)
        val momentoFin = proximaOcurrenciaDe(minutoFin)
        programarAlarmaExacta(
            alarmManager,
            momentoFin.timeInMillis,
            crearPendingIntent(context, GrabacionReceiver.ACCION_DETENER, CODIGO_FIN)
        )
    }

    fun programarDuracionLimitada(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val minutos = ConfiguracionGrabacion.leerDuracionLimitadaMinutos(context)
        val momento = System.currentTimeMillis() + minutos * 60 * 1000L

        programarAlarmaExacta(
            alarmManager,
            momento,
            crearPendingIntent(context, GrabacionReceiver.ACCION_DETENER, CODIGO_FIN_DURACION)
        )
    }

    /**
     * Programa una alarma exacta de forma segura: comprueba primero si tenemos
     * permiso (revocable por el usuario desde Android 12) y, si no lo hay,
     * usa una alarma inexacta como plan B en vez de dejar que la app crashee.
     */
    private fun programarAlarmaExacta(
        alarmManager: AlarmManager,
        momentoMs: Long,
        pendingIntent: PendingIntent
    ) {
        val puedeExactas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (puedeExactas) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                momentoMs,
                pendingIntent
            )
        } else {
            // Plan B: alarma inexacta (puede retrasarse unos minutos, pero no crashea)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                momentoMs,
                pendingIntent
            )
        }
    }

    fun cancelarTodasLasAlarmas(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            crearPendingIntent(
                context,
                GrabacionReceiver.ACCION_INICIAR,
                CODIGO_INICIO
            )
        )
        alarmManager.cancel(
            crearPendingIntent(
                context,
                GrabacionReceiver.ACCION_DETENER,
                CODIGO_FIN
            )
        )
        alarmManager.cancel(
            crearPendingIntent(
                context,
                GrabacionReceiver.ACCION_DETENER,
                CODIGO_FIN_DURACION
            )
        )
    }
}
