package com.juliobel11100.remembel

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

    /** Arma las dos alarmas (inicio y fin). Solo debe usarse al activar el horario desde
     * Ajustes o al re-armar tras un reinicio: ninguna de las dos ha disparado todavía, así
     * que hace falta programar ambas desde cero. */
    fun programarHorarioFijo(context: Context) {
        programarProximoInicio(context)
        programarProximoFin(context)
    }

    /** Reprograma solo la alarma de inicio para su próxima ocurrencia (mañana, normalmente).
     * Se usa cuando el inicio acaba de disparar: la de fin ya está armada para hoy y no hay
     * que tocarla — re-registrarla sin necesidad puede hacer que el sistema (sobre todo en
     * capas de fabricante tipo MIUI) la descarte por exceso de reprogramaciones. */
    fun programarProximoInicio(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val minutoInicio = ConfiguracionGrabacion.leerHoraInicioMinutos(context)
        val momentoInicio = proximaOcurrenciaDe(minutoInicio)
        programarAlarmaDespertador(
            context,
            alarmManager,
            momentoInicio.timeInMillis,
            crearPendingIntent(context, GrabacionReceiver.ACCION_INICIAR, CODIGO_INICIO)
        )
    }

    /** Reprograma solo la alarma de fin para su próxima ocurrencia (mañana, normalmente).
     * Simétrico a [programarProximoInicio]. */
    fun programarProximoFin(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val minutoFin = ConfiguracionGrabacion.leerHoraFinMinutos(context)
        val momentoFin = proximaOcurrenciaDe(minutoFin)
        programarAlarmaDespertador(
            context,
            alarmManager,
            momentoFin.timeInMillis,
            crearPendingIntent(context, GrabacionReceiver.ACCION_DETENER, CODIGO_FIN)
        )
    }

    fun programarDuracionLimitada(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val minutos = ConfiguracionGrabacion.leerDuracionLimitadaMinutos(context)
        val momento = System.currentTimeMillis() + minutos * 60 * 1000L
        ConfiguracionGrabacion.guardarFinDuracionLimitada(context, momento)

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

    /**
     * Programa el inicio/fin del horario fijo con setAlarmClock(): a diferencia de
     * setExactAndAllowWhileIdle(), esta API está exenta de Doze y de las restricciones por
     * "standby bucket" del sistema (es el mecanismo que usan las apps de despertador), así que
     * no sufre los retrasos de varios minutos que sí pueden darse con la alarma "exacta"
     * normal bajo uso intensivo o capas de fabricante agresivas (MIUI). Eso sí, sigue
     * exigiendo el permiso de "Alarmas y recordatorios" igual que la exacta normal (probado
     * en dispositivo: sin el permiso, setAlarmClock() lanza SecurityException y tira la app
     * abajo) — por eso comprueba el permiso igual que [programarAlarmaExacta] y cae también a
     * una alarma inexacta si no lo tiene. La contrapartida de usarla cuando sí hay permiso es
     * que Android muestra un pequeño icono de despertador en la barra de estado mientras la
     * alarma esté pendiente.
     */
    private fun programarAlarmaDespertador(
        context: Context,
        alarmManager: AlarmManager,
        momentoMs: Long,
        pendingIntent: PendingIntent
    ) {
        val puedeExactas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (!puedeExactas) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momentoMs, pendingIntent)
            return
        }

        val mostrarIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(momentoMs, mostrarIntent),
                pendingIntent
            )
        } catch (e: SecurityException) {
            // El permiso puede haberse revocado entre la comprobación y la llamada.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momentoMs, pendingIntent)
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
        cancelarDuracionLimitada(context)
    }

    /**
     * Cancela solo la alarma de fin de "duración limitada". Se usa al parar la
     * grabación manualmente para que una alarma antigua no se quede pendiente
     * y termine cortando una grabación posterior sin motivo.
     */
    fun cancelarDuracionLimitada(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            crearPendingIntent(
                context,
                GrabacionReceiver.ACCION_DETENER,
                CODIGO_FIN_DURACION
            )
        )
        ConfiguracionGrabacion.guardarFinDuracionLimitada(context, 0L)
    }
}
