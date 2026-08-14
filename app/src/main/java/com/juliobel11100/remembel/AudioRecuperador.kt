package com.juliobel11100.remembel

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Motor de recuperación de audio.
 * En vez de asumir una cuadrícula perfecta de 15 min, mira los archivos
 * que existen de verdad y sus duraciones reales.
 */

private val FORMATO_NOMBRE = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())

/**
 * Representa un trozo real encontrado en la carpeta, con su rango de tiempo real.
 */
private data class TrozoReal(val archivo: File, val inicioMs: Long, val finMs: Long)

/** Un tramo de audio ya recompuesto, con su rango de tiempo real (puede no coincidir
 *  exactamente con lo pedido si se ha recortado a lo que hay realmente grabado). */
data class TramoRecuperado(val archivo: File, val inicioMs: Long, val finMs: Long)

/** Una pista de grabación en bruto que existe ahora mismo en disco (sin guardar
 *  todavía en la biblioteca), con su rango de tiempo real. */
data class PistaDisponible(val inicioMs: Long, val finMs: Long)

/**
 * Lista, sin filtrar por ningún intervalo, todas las pistas (tramos continuos de
 * grabación) que existen ahora mismo en la carpeta de grabaciones en bruto. Sirve
 * para mostrarle al usuario qué puede todavía recuperar y guardar antes de que el
 * borrado automático (7 días) se lo lleve.
 */
fun listarPistasDisponibles(context: Context): List<PistaDisponible> {
    val carpeta = File(context.getExternalFilesDir(null), "grabaciones")
    if (!carpeta.exists()) return emptyList()

    val trozos = leerTrozosReales(carpeta) { true }
    return agruparPorContinuidad(trozos).map { pista ->
        PistaDisponible(pista.first().inicioMs, pista.last().finMs)
    }.sortedByDescending { it.inicioMs }
}

/**
 * Recupera el intervalo pedido. Si dentro de ese intervalo hay huecos sin grabar
 * (porque la grabación se paró y se volvió a arrancar más tarde), no se rellenan
 * ni se pegan como si fueran continuos: se devuelve un [TramoRecuperado] por cada
 * tramo realmente continuo de grabación, para no mezclar audio de momentos
 * distintos en un mismo archivo.
 */
fun recuperarIntervalo(
    context: Context,
    inicioMs: Long,
    finMs: Long
): List<TramoRecuperado> {

    val carpeta = File(context.getExternalFilesDir(null), "grabaciones")
    if (!carpeta.exists()) return emptyList()

    val trozosQueTocan = buscarTrozosQueTocanRango(carpeta, inicioMs, finMs)
    if (trozosQueTocan.isEmpty()) return emptyList()

    val pistas = agruparPorContinuidad(trozosQueTocan)

    return pistas.mapIndexedNotNull { indice, pista ->
        val inicioPista = maxOf(inicioMs, pista.first().inicioMs)
        val finPista = minOf(finMs, pista.last().finMs)
        if (finPista <= inicioPista) return@mapIndexedNotNull null

        val archivoSalida = File(context.cacheDir, "recuperado_temporal_$indice.m4a")
        if (archivoSalida.exists()) archivoSalida.delete()

        unirYRecortar(pista, inicioPista, finPista, archivoSalida)

        if (archivoSalida.exists() && archivoSalida.length() > 0) {
            TramoRecuperado(archivoSalida, inicioPista, finPista)
        } else {
            null
        }
    }
}

/**
 * Agrupa los trozos ordenados en pistas: un trozo empieza una pista nueva si hay
 * un hueco real (sin grabación) entre el final del trozo anterior y su propio
 * inicio, más allá de un pequeño margen por la latencia normal del corte entre
 * un MediaRecorder y el siguiente.
 */
private fun agruparPorContinuidad(trozos: List<TrozoReal>): List<List<TrozoReal>> {
    val toleranciaMs = 3_000L
    val pistas = mutableListOf<MutableList<TrozoReal>>()

    for (trozo in trozos) {
        val pistaActual = pistas.lastOrNull()
        if (pistaActual != null && trozo.inicioMs - pistaActual.last().finMs <= toleranciaMs) {
            pistaActual.add(trozo)
        } else {
            pistas.add(mutableListOf(trozo))
        }
    }
    return pistas
}

/**
 * Lee la hora de inicio a partir del NOMBRE del archivo (ej. "2026-07-04_11-05.aac" -> ese momento).
 * Admite también ".m4a" por si quedan trozos de una versión anterior de la app.
 */
private fun leerInicioDesdeNombre(archivo: File): Long? {
    val nombreSinExtension = archivo.name.removeSuffix(".aac").removeSuffix(".m4a")
    return try {
        FORMATO_NOMBRE.parse(nombreSinExtension)?.time
    } catch (e: Exception) {
        null
    }
}

/**
 * Pregunta al propio archivo de audio cuánto dura, en milisegundos.
 */
private fun leerDuracionReal(archivo: File): Long {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(archivo.absolutePath)
        val pista = seleccionarPistaAudio(extractor) ?: return 0L
        val formato = extractor.getTrackFormat(pista)
        formato.getLong(MediaFormat.KEY_DURATION) / 1000 // viene en microsegundos, pasamos a ms
    } catch (e: Exception) {
        0L
    } finally {
        extractor.release()
    }
}

/**
 * Lista los archivos reales de la carpeta y calcula su rango real [inicio, fin)
 * usando su nombre + su duración de verdad, ordenados cronológicamente.
 *
 * [filtroCandidato] se aplica ANTES de abrir cada archivo (usando solo el inicio
 * que ya sabemos por su nombre, sin coste de E/S) para no pagar el precio de un
 * MediaExtractor por cada .aac en disco cuando solo nos interesa un puñado de
 * ellos — con retención de 7 días y trozos de 15 min puede haber cientos de
 * archivos, y abrirlos todos para descartar la mayoría es el cuello de botella
 * real de una recuperación.
 */
private fun leerTrozosReales(
    carpeta: File,
    filtroCandidato: (inicioTrozo: Long) -> Boolean
): List<TrozoReal> {
    val archivos = carpeta.listFiles { f -> f.name.endsWith(".aac") || f.name.endsWith(".m4a") } ?: return emptyList()

    return archivos.mapNotNull { archivo ->
        val inicioTrozo = leerInicioDesdeNombre(archivo) ?: return@mapNotNull null
        if (!filtroCandidato(inicioTrozo)) return@mapNotNull null
        val duracion = leerDuracionReal(archivo)
        if (duracion <= 0L) return@mapNotNull null
        TrozoReal(archivo, inicioTrozo, inicioTrozo + duracion)
    }.sortedBy { it.inicioMs }
}

/**
 * Filtra primero por nombre (barato) y luego, solo para los candidatos reales,
 * comprueba el solape exacto con el rango pedido usando la duración real.
 */
private fun buscarTrozosQueTocanRango(
    carpeta: File,
    inicioMs: Long,
    finMs: Long
): List<TrozoReal> {
    // Margen generoso por encima de la duración normal de un trozo (15 min):
    // un trozo que empieza justo antes de inicioMs puede seguir solapando el
    // rango pedido gracias a su propia duración.
    val margenMs = 60 * 60 * 1000L

    return leerTrozosReales(carpeta) { inicioTrozo ->
        inicioTrozo < finMs && inicioTrozo > inicioMs - margenMs
    }.filter { trozo ->
        // Solape exacto, ya con la duración real de cada candidato.
        trozo.inicioMs < finMs && trozo.finMs > inicioMs
    }
}

/**
 * PASOS 2 y 3: recorre cada trozo real, calcula cuánto recortar de sus extremos
 * (usando su rango REAL, no uno asumido), y va escribiendo el resultado con MediaMuxer.
 */
private fun unirYRecortar(
    trozos: List<TrozoReal>,
    inicioMs: Long,
    finMs: Long,
    salida: File
) {
    val muxer = MediaMuxer(salida.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var pistaSalida = -1
    var muxerIniciado = false
    var tiempoAcumuladoUs = 0L

    for (trozo in trozos) {
        val extractor = MediaExtractor()
        extractor.setDataSource(trozo.archivo.absolutePath)

        val pista = seleccionarPistaAudio(extractor) ?: continue
        extractor.selectTrack(pista)
        val formato = extractor.getTrackFormat(pista)

        if (!muxerIniciado) {
            pistaSalida = muxer.addTrack(formato)
            muxer.start()
            muxerIniciado = true
        }

        // Igual que antes, pero usando el rango REAL de este trozo
        val recorteInicioMs = maxOf(0L, inicioMs - trozo.inicioMs)
        val recorteFinMs = minOf(trozo.finMs, finMs) - trozo.inicioMs

        val recorteInicioUs = recorteInicioMs * 1000L
        val recorteFinUs = recorteFinMs * 1000L

        extractor.seekTo(recorteInicioUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val buffer = ByteBuffer.allocate(256 * 1024)
        val info = MediaCodec.BufferInfo()

        while (true) {
            val tamano = extractor.readSampleData(buffer, 0)
            if (tamano < 0) break

            val tiempoActualUs = extractor.sampleTime
            if (tiempoActualUs > recorteFinUs) break

            info.offset = 0
            info.size = tamano
            info.presentationTimeUs = tiempoAcumuladoUs + (tiempoActualUs - recorteInicioUs)
            info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }

            muxer.writeSampleData(pistaSalida, buffer, info)
            extractor.advance()
        }

        tiempoAcumuladoUs += (recorteFinUs - recorteInicioUs)
        extractor.release()
    }

    if (muxerIniciado) {
        muxer.stop()
    }
    muxer.release()
}

private fun seleccionarPistaAudio(extractor: MediaExtractor): Int? {
    for (i in 0 until extractor.trackCount) {
        val formato = extractor.getTrackFormat(i)
        val mime = formato.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("audio/")) return i
    }
    return null
}