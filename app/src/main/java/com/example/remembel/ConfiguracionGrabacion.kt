package com.example.remembel

import android.content.Context

/**
 * Los distintos modos en los que puede funcionar la grabación.
 * Un "enum class" es una lista cerrada de valores posibles: aquí solo
 * pueden existir estos tres, nada más — el compilador te avisa si
 * intentas usar algo que no esté en esta lista.
 */
enum class ModoGrabacion {
    CONSTANTE,
    HORARIO_FIJO,
    DURACION_LIMITADA
}
/**
 * Presets de calidad. Un enum puede llevar PROPIEDADES asociadas a cada
 * valor: aquí, cada preset "sabe" su propio bitrate y su nombre visible.
 */
enum class CalidadAudio(val bitrate: Int, val etiqueta: String) {
    AHORRO(32_000, "Ahorro"),
    NORMAL(64_000, "Normal"),
    ALTA(128_000, "Alta"),
    MAXIMA(192_000, "Máxima");

    /** Megabytes que ocupa una hora de grabación con este preset. */
    fun megasPorHora(): Double = bitrate / 8.0 * 3600 / 1_000_000
}
/**
 * Punto único de acceso a los ajustes guardados de la app.
 * Todo lo relacionado con "leer o escribir configuración" pasa por aquí.
 */
object ConfiguracionGrabacion {

    private const val NOMBRE_PREFS = "remembel_configuracion"
    private const val CLAVE_MODO = "modo"
    private const val CLAVE_HORA_INICIO = "hora_inicio_minutos"
    private const val CLAVE_HORA_FIN = "hora_fin_minutos"
    private const val CLAVE_DURACION_MIN = "duracion_minutos"
    private const val CLAVE_CALIDAD = "calidad_audio"
    private const val CLAVE_RETENCION_DIAS = "retencion_dias"
    private const val CLAVE_VOZ_CLARA = "voz_clara"

    fun guardarCalidad(context: Context, calidad: CalidadAudio) {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(CLAVE_CALIDAD, calidad.name).apply()
    }

    fun leerCalidad(context: Context): CalidadAudio {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        val nombre = prefs.getString(CLAVE_CALIDAD, CalidadAudio.NORMAL.name)
        return CalidadAudio.valueOf(nombre ?: CalidadAudio.NORMAL.name)
    }

    fun guardarRetencionDias(context: Context, dias: Int) {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(CLAVE_RETENCION_DIAS, dias).apply()
    }

    fun leerRetencionDias(context: Context): Int {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(CLAVE_RETENCION_DIAS, 2) // por defecto: 2 días
    }

    fun guardarVozClara(context: Context, activada: Boolean) {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(CLAVE_VOZ_CLARA, activada).apply()
    }

    fun leerVozClara(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(CLAVE_VOZ_CLARA, false)
    }

    fun guardarModo(context: Context, modo: ModoGrabacion) {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(CLAVE_MODO, modo.name).apply()
    }

    fun leerModo(context: Context): ModoGrabacion {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        val nombreGuardado = prefs.getString(CLAVE_MODO, ModoGrabacion.CONSTANTE.name)
        return ModoGrabacion.valueOf(nombreGuardado ?: ModoGrabacion.CONSTANTE.name)
    }

    fun guardarHorarioFijo(context: Context, minutoInicio: Int, minutoFin: Int) {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(CLAVE_HORA_INICIO, minutoInicio)
            .putInt(CLAVE_HORA_FIN, minutoFin)
            .apply()
    }

    fun leerHoraInicioMinutos(context: Context): Int {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(CLAVE_HORA_INICIO, 9 * 60) // por defecto: 09:00
    }

    fun leerHoraFinMinutos(context: Context): Int {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(CLAVE_HORA_FIN, 17 * 60) // por defecto: 17:00
    }

    fun guardarDuracionLimitadaMinutos(context: Context, minutos: Int) {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(CLAVE_DURACION_MIN, minutos).apply()
    }

    fun leerDuracionLimitadaMinutos(context: Context): Int {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(CLAVE_DURACION_MIN, 60) // por defecto: 60 min
    }

}

