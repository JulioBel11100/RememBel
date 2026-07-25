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

/**
 * Decodifica el audio a PCM y calcula el volumen (RMS) en [numBarras] tramos
 * iguales, normalizado entre 0 y 1, para dibujar una forma de onda estilo
 * WhatsApp. Es una operación bloqueante: llamarla desde Dispatchers.IO.
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
        val bufferInfo = MediaCodec.BufferInfo()

        var entradaAgotada = false
        var salidaTerminada = false

        while (!salidaTerminada) {
            if (!entradaAgotada) {
                val indiceEntrada = codec.dequeueInputBuffer(10_000)
                if (indiceEntrada >= 0) {
                    val bufferEntrada = codec.getInputBuffer(indiceEntrada)!!
                    val tamano = extractor.readSampleData(bufferEntrada, 0)
                    if (tamano < 0) {
                        codec.queueInputBuffer(indiceEntrada, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        entradaAgotada = true
                    } else {
                        codec.queueInputBuffer(indiceEntrada, 0, tamano, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val indiceSalida = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (indiceSalida >= 0) {
                if (bufferInfo.size > 0) {
                    val bufferSalida = codec.getOutputBuffer(indiceSalida)!!
                    bufferSalida.order(ByteOrder.LITTLE_ENDIAN)
                    bufferSalida.position(bufferInfo.offset)
                    bufferSalida.limit(bufferInfo.offset + bufferInfo.size)

                    val barraActual = ((bufferInfo.presentationTimeUs * numBarras) / duracionUs)
                        .toInt().coerceIn(0, numBarras - 1)

                    // PCM de 16 bits con signo (posibles canales intercalados; los tratamos igual).
                    while (bufferSalida.remaining() >= 2) {
                        val muestra = bufferSalida.short.toDouble()
                        sumaPorBarra[barraActual] += muestra * muestra
                        cuentaPorBarra[barraActual]++
                    }
                }
                codec.releaseOutputBuffer(indiceSalida, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    salidaTerminada = true
                }
            }
        }

        codec.stop()
        codec.release()

        val rmsPorBarra = DoubleArray(numBarras) { i ->
            if (cuentaPorBarra[i] > 0) sqrt(sumaPorBarra[i] / cuentaPorBarra[i]) else 0.0
        }
        val maximo = rmsPorBarra.maxOrNull()?.takeIf { it > 0.0 } ?: return silencio

        // Escala logarítmica (dB) con suelo de ruido: así el silencio y el ruido de
        // fondo quedan planos y los tramos con voz destacan con claridad, en vez de
        // que un único pico de volumen aplaste visualmente el resto de la onda.
        val pisoDb = 40.0
        return rmsPorBarra.map { valor ->
            val db = if (valor > 0.0) 20.0 * log10(valor / maximo) else -pisoDb
            val normalizado = ((db + pisoDb) / pisoDb).coerceIn(0.0, 1.0)
            normalizado.toFloat().coerceAtLeast(0.05f)
        }
    } catch (e: Exception) {
        return silencio
    } finally {
        extractor.release()
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
