package com.juliobel11100.remembel

import android.content.Context

enum class ModoGrabacion {
    CONSTANTE,
    HORARIO_FIJO,
    DURACION_LIMITADA
}

enum class EstiloVisual {
    ESENCIAL,
    VIVO
}

enum class TemaApp {
    CLARO, OSCURO, SISTEMA
}

object ConfiguracionGrabacion {

    private const val NOMBRE_PREFS = "remembel_configuracion"
    private const val CLAVE_MODO = "modo"
    private const val CLAVE_HORA_INICIO = "hora_inicio_minutos"
    private const val CLAVE_HORA_FIN = "hora_fin_minutos"
    private const val CLAVE_DURACION_MIN = "duracion_minutos"
    private const val CLAVE_ESTABA_ACTIVO = "estaba_activo"
    private const val CLAVE_GUIA_VISTA = "guia_vista"

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
        return prefs.getInt(CLAVE_HORA_INICIO, 9 * 60)
    }

    fun leerHoraFinMinutos(context: Context): Int {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(CLAVE_HORA_FIN, 17 * 60)
    }

    fun guardarDuracionLimitadaMinutos(context: Context, minutos: Int) {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(CLAVE_DURACION_MIN, minutos).apply()
    }

    fun leerDuracionLimitadaMinutos(context: Context): Int {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(CLAVE_DURACION_MIN, 60)
    }

    fun guardarEstabaActivo(context: Context, activo: Boolean) {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(CLAVE_ESTABA_ACTIVO, activo).apply()
    }

    fun leerEstabaActivo(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(CLAVE_ESTABA_ACTIVO, false)
    }

    fun guardarGuiaVista(context: Context, vista: Boolean) {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(CLAVE_GUIA_VISTA, vista).apply()
    }

    fun leerGuiaVista(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(CLAVE_GUIA_VISTA, false)
    }
}