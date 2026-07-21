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

fun recuperarIntervalo(
    context: Context,
    inicioMs: Long,
    finMs: Long
): File? {

    val carpeta = File(context.getExternalFilesDir(null), "grabaciones")
    if (!carpeta.exists()) return null

    // -------- PASO 1 (nuevo): miramos qué existe de verdad --------
    val trozosQueTocan = buscarTrozosQueTocanRango(carpeta, inicioMs, finMs)
    if (trozosQueTocan.isEmpty()) return null

    // -------- PASOS 2 y 3: recortar extremos y pegar (igual que antes) --------
    val archivoSalida = File(context.cacheDir, "recuperado_temporal.m4a")
    if (archivoSalida.exists()) archivoSalida.delete()

    unirYRecortar(trozosQueTocan, inicioMs, finMs, archivoSalida)

    return if (archivoSalida.exists() && archivoSalida.length() > 0) archivoSalida else null
}

/**
 * Lee la hora de inicio a partir del NOMBRE del archivo (ej. "2026-07-04_11-05.m4a" -> ese momento).
 */
private fun leerInicioDesdeNombre(archivo: File): Long? {
    val nombreSinExtension = archivo.name.removeSuffix(".m4a")
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
 * PASO 1 en detalle: lista los archivos reales de la carpeta, calcula su rango
 * real [inicio, fin) usando su nombre + su duración de verdad, y devuelve
 * solo los que se solapan con el rango pedido, ordenados cronológicamente.
 */
private fun buscarTrozosQueTocanRango(
    carpeta: File,
    inicioMs: Long,
    finMs: Long
): List<TrozoReal> {
    val archivos = carpeta.listFiles { f -> f.name.endsWith(".m4a") } ?: return emptyList()

    return archivos.mapNotNull { archivo ->
        val inicioTrozo = leerInicioDesdeNombre(archivo) ?: return@mapNotNull null
        val duracion = leerDuracionReal(archivo)
        if (duracion <= 0L) return@mapNotNull null
        val finTrozo = inicioTrozo + duracion
        TrozoReal(archivo, inicioTrozo, finTrozo)
    }.filter { trozo ->
        // ¿Se solapa este trozo con el rango que pide el usuario?
        trozo.inicioMs < finMs && trozo.finMs > inicioMs
    }.sortedBy { it.inicioMs }
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