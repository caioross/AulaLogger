package com.aulalogger.transcription

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Leitor de WAV PCM 16-bit mono, otimizado para arquivos longos (4h+).
 *
 * O leitor antigo carregava o arquivo INTEIRO em memória três vezes
 * (ByteArray + ShortArray + FloatArray), resultando em pico de ~1.8 GB para
 * uma aula de 4h e OOM garantido. Esta versão lê em chunks direto para um
 * FloatArray pré-alocado, com pico ~ tamanho_final + chunk.
 */
object WavReader {

    private const val TAG = "WavReader"
    private const val CHUNK_BYTES = 1 * 1024 * 1024  // 1 MB por iteração

    data class WavInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataBytes: Long
    )

    /**
     * Lê o cabeçalho RIFF, pulando chunks desconhecidos até encontrar o "data".
     * Retorna info ou null se WAV inválido.
     */
    fun parseHeader(file: File): WavInfo? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val riff = ByteArray(4); raf.readFully(riff)
                if (String(riff) != "RIFF") return null
                raf.skipBytes(4)  // chunk size
                val wave = ByteArray(4); raf.readFully(wave)
                if (String(wave) != "WAVE") return null

                var sampleRate = 0
                var channels = 0
                var bits = 0
                var dataOffset = -1L
                var dataBytes = 0L

                while (raf.filePointer < raf.length() - 8) {
                    val id = ByteArray(4); raf.readFully(id)
                    val size = readIntLE(raf)
                    when (String(id)) {
                        "fmt " -> {
                            val start = raf.filePointer
                            raf.skipBytes(2)  // audio format
                            channels = readShortLE(raf)
                            sampleRate = readIntLE(raf)
                            raf.skipBytes(6)  // byteRate(4) + blockAlign(2)
                            bits = readShortLE(raf)
                            // pula resto do fmt se houver
                            raf.seek(start + size)
                        }
                        "data" -> {
                            dataOffset = raf.filePointer
                            dataBytes = size.toLong().coerceAtLeast(0L)
                            // se size==0 (writer crashou antes de finalizar header),
                            // assume até o fim do arquivo
                            if (dataBytes == 0L || dataOffset + dataBytes > raf.length()) {
                                dataBytes = raf.length() - dataOffset
                            }
                            return@use WavInfo(sampleRate, channels, bits, dataOffset, dataBytes)
                        }
                        else -> raf.skipBytes(size)  // pula chunk desconhecido
                    }
                }
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao parsear header WAV", t)
            null
        }
    }

    /**
     * @deprecated Use [openStream] + [Stream.readRange] em vez disso.
     * Esta função aloca o áudio INTEIRO em RAM (4h ≈ 920 MB FloatArray) e
     * causa OOM em aulas longas. Mantida apenas para retrocompatibilidade.
     */
    @Deprecated(
        message = "Aloca arquivo inteiro em RAM. Use openStream().readRange() para chunks.",
        replaceWith = ReplaceWith("openStream(file)?.use { it.readRange(0, totalSamples) }")
    )
    fun readAsFloat(file: File): FloatArray {
        val info = parseHeader(file) ?: run {
            Log.e(TAG, "WAV inválido ou ausente: ${file.absolutePath}")
            return FloatArray(0)
        }
        if (info.bitsPerSample != 16) {
            Log.e(TAG, "WAV não é PCM 16-bit (${info.bitsPerSample}-bit)")
            return FloatArray(0)
        }
        val totalSamples = (info.dataBytes / 2L).toInt()
        if (totalSamples <= 0) return FloatArray(0)

        val out = try {
            FloatArray(totalSamples)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM ao alocar FloatArray de $totalSamples samples", oom)
            return FloatArray(0)
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(info.dataOffset)
                val buf = ByteArray(CHUNK_BYTES)
                var sampleIdx = 0
                val scale = 1f / 32768f
                var remainingBytes = info.dataBytes

                while (remainingBytes > 0L && sampleIdx < totalSamples) {
                    val toRead = minOf(buf.size.toLong(), remainingBytes).toInt()
                    val read = raf.read(buf, 0, toRead)
                    if (read <= 0) break

                    val bb = ByteBuffer.wrap(buf, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                    val n = read / 2
                    var i = 0
                    while (i < n && sampleIdx < totalSamples) {
                        out[sampleIdx++] = bb.short * scale
                        i++
                    }
                    remainingBytes -= read
                }

                // Se for stereo, faz downmix simples para mono (média dos canais).
                // Whisper espera mono. Nosso writer só grava mono, mas defensivo
                // para áudios externos.
                if (info.channels == 2) {
                    val mono = FloatArray(sampleIdx / 2)
                    for (i in mono.indices) {
                        mono[i] = (out[i * 2] + out[i * 2 + 1]) * 0.5f
                    }
                    return mono
                }
                if (sampleIdx < totalSamples) out.copyOf(sampleIdx) else out
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao ler dados WAV", t)
            FloatArray(0)
        }
    }

    /**
     * Stream stateful: mantém RandomAccessFile aberto entre chamadas de chunk,
     * evitando 240 open/close em uma aula de 4h. Use no padrão `use { ... }`.
     */
    class Stream(private val file: File, val info: WavInfo) : AutoCloseable {
        private val raf = RandomAccessFile(file, "r")
        private val buf = ByteArray(CHUNK_BYTES)
        private val scale = 1f / 32768f

        fun readRange(startSample: Int, endSample: Int): FloatArray {
            if (info.bitsPerSample != 16) return FloatArray(0)
            val n = (endSample - startSample).coerceAtLeast(0)
            if (n == 0) return FloatArray(0)
            val out = FloatArray(if (info.channels == 2) n / 2 else n)
            return try {
                val byteOffset = info.dataOffset + startSample.toLong() * 2L
                raf.seek(byteOffset)
                val totalBytesNeeded = n.toLong() * 2L
                var remaining = totalBytesNeeded
                var sampleIdx = 0
                val outArrayIsStereo = info.channels == 2
                while (remaining > 0L && sampleIdx < out.size) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val read = raf.read(buf, 0, toRead)
                    if (read <= 0) break
                    val bb = ByteBuffer.wrap(buf, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                    val ns = read / 2
                    if (outArrayIsStereo) {
                        var i = 0
                        while (i + 1 < ns && sampleIdx < out.size) {
                            val l = bb.short * scale
                            val r = bb.short * scale
                            out[sampleIdx++] = (l + r) * 0.5f
                            i += 2
                        }
                    } else {
                        var i = 0
                        while (i < ns && sampleIdx < out.size) {
                            out[sampleIdx++] = bb.short * scale
                            i++
                        }
                    }
                    remaining -= read
                }
                if (sampleIdx < out.size) out.copyOf(sampleIdx) else out
            } catch (t: Throwable) {
                Log.e(TAG, "Falha em Stream.readRange [$startSample, $endSample)", t)
                FloatArray(0)
            }
        }

        override fun close() {
            try { raf.close() } catch (_: Throwable) {}
        }
    }

    fun openStream(file: File): Stream? {
        val info = parseHeader(file) ?: return null
        return Stream(file, info)
    }

    /**
     * Lê apenas a janela [startSample, endSample) como FloatArray normalizado.
     * Usado pela transcrição chunked: cada chamada aloca ~1.9 MB (60s @ 16kHz)
     * em vez de ~920 MB do áudio inteiro.
     */
    fun readSamplesRange(file: File, info: WavInfo, startSample: Int, endSample: Int): FloatArray {
        if (info.bitsPerSample != 16) return FloatArray(0)
        val n = (endSample - startSample).coerceAtLeast(0)
        if (n == 0) return FloatArray(0)
        val out = FloatArray(if (info.channels == 2) n / 2 else n)
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val byteOffset = info.dataOffset + startSample.toLong() * 2L
                raf.seek(byteOffset)
                val buf = ByteArray(CHUNK_BYTES)
                val totalBytesNeeded = n.toLong() * 2L
                var remaining = totalBytesNeeded
                var sampleIdx = 0
                val scale = 1f / 32768f
                val outArrayIsStereo = info.channels == 2
                while (remaining > 0L && sampleIdx < out.size) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val read = raf.read(buf, 0, toRead)
                    if (read <= 0) break
                    val bb = ByteBuffer.wrap(buf, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                    val ns = read / 2
                    if (outArrayIsStereo) {
                        var i = 0
                        while (i + 1 < ns && sampleIdx < out.size) {
                            val l = bb.short * scale
                            val r = bb.short * scale
                            out[sampleIdx++] = (l + r) * 0.5f
                            i += 2
                        }
                    } else {
                        var i = 0
                        while (i < ns && sampleIdx < out.size) {
                            out[sampleIdx++] = bb.short * scale
                            i++
                        }
                    }
                    remaining -= read
                }
                if (sampleIdx < out.size) out.copyOf(sampleIdx) else out
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Falha em readSamplesRange [$startSample, $endSample)", t)
            FloatArray(0)
        }
    }

    private fun readIntLE(raf: RandomAccessFile): Int {
        val b = ByteArray(4); raf.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readShortLE(raf: RandomAccessFile): Int {
        val b = ByteArray(2); raf.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }
}
