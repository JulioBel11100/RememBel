package com.juliobel11100.remembel

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.io.File
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/** A partir de esta duración se muestrea en vez de decodificar el audio entero. */
private const val UMBRAL_MUESTREO_US = 5 * 60 * 1_000_000L

/** Cuánto audio se decodifica alrededor de cada barra cuando se muestrea. */
private const val VENTANA_POR_BARRA_US = 400_000L

/**
 * Decodifica el audio a PCM y calcula el volumen (RMS) en [numBarras] tramos
 * iguales, normalizado entre 0 y 1, para dibujar una forma de onda estilo
 * WhatsApp. Es una operación bloqueante: llamarla desde Dispatchers.IO.
 *
 * Para audios cortos se decodifica la pista completa. Para audios largos
 * (recuperaciones de varias horas) hacer eso puede tardar demasiado y la
 * barra nunca llega a aparecer, así que a partir de [UMBRAL_MUESTREO_US] se
 * decodifica solo una ventana corta alrededor de cada barra en vez del audio
 * completo: el tiempo de cálculo deja de depender de lo largo que sea el audio.
 */
fun extraerFormaDeOnda(archivo: File, numBarras: Int = 46): List<Float> {
    val silencio = List(numBarras) { 0.05f }
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(archivo.absolutePath)

        val pista = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return silencio

        val formato = extractor.getTrackFormat(pista)
        val mime = formato.getString(MediaFormat.KEY_MIME) ?: return silencio
        if (!formato.containsKey(MediaFormat.KEY_DURATION)) return silencio
        val duracionUs = formato.getLong(MediaFormat.KEY_DURATION)
        if (duracionUs <= 0L) return silencio

        extractor.selectTrack(pista)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(formato, null, null, 0)
        codec.start()

        val sumaPorBarra = DoubleArray(numBarras)
        val cuentaPorBarra = LongArray(numBarras)

        if (duracionUs > UMBRAL_MUESTREO_US) {
            decodificarPorMuestreo(extractor, codec, numBarras, duracionUs, sumaPorBarra, cuentaPorBarra)
        } else {
            decodificarCompleto(extractor, codec, numBarras, duracionUs, sumaPorBarra, cuentaPorBarra)
        }

        codec.stop()
        codec.release()

        return normalizarBarras(sumaPorBarra, cuentaPorBarra, silencio)
    } catch (e: Exception) {
        return silencio
    } finally {
        extractor.release()
    }
}

/** Decodifica la pista de principio a fin, tramo a tramo (audios cortos). */
private fun decodificarCompleto(
    extractor: MediaExtractor,
    codec: MediaCodec,
    numBarras: Int,
    duracionUs: Long,
    sumaPorBarra: DoubleArray,
    cuentaPorBarra: LongArray
) {
    val bufferInfo = MediaCodec.BufferInfo()
    var entradaAgotada = false
    var salidaTerminada = false

    while (!salidaTerminada) {
        if (!entradaAgotada) {
            entradaAgotada = alimentarEntrada(extractor, codec)
        }

        val indiceSalida = codec.dequeueOutputBuffer(bufferInfo, 10_000)
        if (indiceSalida >= 0) {
            if (bufferInfo.size > 0) {
                val barraActual = ((bufferInfo.presentationTimeUs * numBarras) / duracionUs)
                    .toInt().coerceIn(0, numBarras - 1)
                acumularBuffer(codec, indiceSalida, bufferInfo, sumaPorBarra, cuentaPorBarra, barraActual)
            }
            codec.releaseOutputBuffer(indiceSalida, false)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                salidaTerminada = true
            }
        }
    }
}

/**
 * Decodifica solo una ventana corta ([VENTANA_POR_BARRA_US]) alrededor de cada
 * barra, saltando de una a otra con seekTo, en vez de todo el audio: así el
 * tiempo de cálculo es constante sin importar la duración total del audio.
 */
private fun decodificarPorMuestreo(
    extractor: MediaExtractor,
    codec: MediaCodec,
    numBarras: Int,
    duracionUs: Long,
    sumaPorBarra: DoubleArray,
    cuentaPorBarra: LongArray
) {
    val bufferInfo = MediaCodec.BufferInfo()
    // Deja siempre margen para una ventana completa por delante, para que la
    // última barra también tenga audio real que muestrear.
    val ultimoObjetivoPosibleUs = (duracionUs - VENTANA_POR_BARRA_US).coerceAtLeast(0L)

    for (barra in 0 until numBarras) {
        val objetivoUs = ((duracionUs * barra) / numBarras).coerceAtMost(ultimoObjetivoPosibleUs)
        extractor.seekTo(objetivoUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        codec.flush()

        var entradaAgotada = false
        var salidaTerminada = false
        var inicioReal = -1L

        while (!salidaTerminada) {
            if (!entradaAgotada) {
                entradaAgotada = alimentarEntrada(extractor, codec)
            }

            val indiceSalida = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (indiceSalida >= 0) {
                if (bufferInfo.size > 0) {
                    if (inicioReal < 0L) inicioReal = bufferInfo.presentationTimeUs
                    acumularBuffer(codec, indiceSalida, bufferInfo, sumaPorBarra, cuentaPorBarra, barra)
                }
                val ventanaCompleta = inicioReal >= 0L &&
                    bufferInfo.presentationTimeUs - inicioReal >= VENTANA_POR_BARRA_US
                codec.releaseOutputBuffer(indiceSalida, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || ventanaCompleta) {
                    salidaTerminada = true
                }
            }
        }
    }
}

/** Intenta alimentar un buffer de entrada; devuelve true si se ha llegado al final de la pista. */
private fun alimentarEntrada(extractor: MediaExtractor, codec: MediaCodec): Boolean {
    val indiceEntrada = codec.dequeueInputBuffer(10_000)
    if (indiceEntrada < 0) return false
    val bufferEntrada = codec.getInputBuffer(indiceEntrada)!!
    val tamano = extractor.readSampleData(bufferEntrada, 0)
    return if (tamano < 0) {
        codec.queueInputBuffer(indiceEntrada, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        true
    } else {
        codec.queueInputBuffer(indiceEntrada, 0, tamano, extractor.sampleTime, 0)
        extractor.advance()
        false
    }
}

/** PCM de 16 bits con signo (posibles canales intercalados; se tratan igual). */
private fun acumularBuffer(
    codec: MediaCodec,
    indiceSalida: Int,
    bufferInfo: MediaCodec.BufferInfo,
    sumaPorBarra: DoubleArray,
    cuentaPorBarra: LongArray,
    barra: Int
) {
    val bufferSalida = codec.getOutputBuffer(indiceSalida)!!
    bufferSalida.order(ByteOrder.LITTLE_ENDIAN)
    bufferSalida.position(bufferInfo.offset)
    bufferSalida.limit(bufferInfo.offset + bufferInfo.size)
    while (bufferSalida.remaining() >= 2) {
        val muestra = bufferSalida.short.toDouble()
        sumaPorBarra[barra] += muestra * muestra
        cuentaPorBarra[barra]++
    }
}

/**
 * Convierte la energía acumulada por barra a una escala logarítmica (dB) con
 * suelo de ruido: así el silencio y el ruido de fondo quedan planos y los
 * tramos con voz destacan con claridad, en vez de que un único pico de
 * volumen aplaste visualmente el resto de la onda.
 */
private fun normalizarBarras(
    sumaPorBarra: DoubleArray,
    cuentaPorBarra: LongArray,
    silencio: List<Float>
): List<Float> {
    val rmsPorBarra = DoubleArray(sumaPorBarra.size) { i ->
        if (cuentaPorBarra[i] > 0) sqrt(sumaPorBarra[i] / cuentaPorBarra[i]) else 0.0
    }
    val maximo = rmsPorBarra.maxOrNull()?.takeIf { it > 0.0 } ?: return silencio

    val pisoDb = 40.0
    return rmsPorBarra.map { valor ->
        val db = if (valor > 0.0) 20.0 * log10(valor / maximo) else -pisoDb
        val normalizado = ((db + pisoDb) / pisoDb).coerceIn(0.0, 1.0)
        normalizado.toFloat().coerceAtLeast(0.05f)
    }
}

/**
 * Barras de volumen tocables/arrastrables (estilo WhatsApp) que a la vez
 * sirven de indicador de progreso y de control de búsqueda (seek).
 */
@Composable
fun FormaDeOndaSlider(
    barras: List<Float>,
    progreso: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorReproducido = MaterialTheme.colorScheme.primary
    val colorPendiente = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        if (barras.isEmpty()) return@Canvas

        val anchoBarra = size.width / barras.size
        val grosorBarra = anchoBarra * 0.6f

        barras.forEachIndexed { indice, amplitud ->
            val alturaBarra = (size.height * amplitud).coerceAtLeast(4f)
            val x = indice * anchoBarra + (anchoBarra - grosorBarra) / 2f
            val yaReproducido = (indice + 0.5f) / barras.size <= progreso

            drawRoundRect(
                color = if (yaReproducido) colorReproducido else colorPendiente,
                topLeft = Offset(x, (size.height - alturaBarra) / 2f),
                size = Size(grosorBarra, alturaBarra),
                cornerRadius = CornerRadius(grosorBarra / 2f)
            )
        }
    }
}
