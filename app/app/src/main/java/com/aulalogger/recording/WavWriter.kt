package com.aulalogger.recording

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Escreve um WAV PCM 16-bit em streaming.
 *
 * - Header de 44 bytes escrito no construtor; atualizado a cada fsync e em close().
 * - **fsync periódico (~1×/s):** garante que se app crashar, o WAV mantém os
 *   últimos N segundos válidos.
 * - **Hot path optimization (BUG-003):** ByteBuffer reusável, sem alocação por
 *   frame. Para 4h de gravação isso elimina ~115 MB de byte arrays órfãos.
 * - **Cap em 4 GB:** WAV PCM 16-bit usa Int32 unsigned para data size. Em ~17h
 *   o limite estoura. Acima desse limite, paramos de atualizar o header
 *   (arquivo continua válido até o último update).
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int = 16000,
    private val channels: Int = 1,
    private val bitDepth: Int = 16
) : AutoCloseable {

    private val raf: RandomAccessFile = RandomAccessFile(file, "rw").also {
        it.setLength(0)
        writeHeader(it, dataSize = 0)
    }
    private var bytesWritten: Long = 0
    private var lastSync: Long = System.currentTimeMillis()

    /** Buffer reusável para conversão short→byte. Dimensionado para frames típicos. */
    private var convBuf: ByteArray = ByteArray(INITIAL_BUFFER_BYTES)

    /**
     * Escreve `count` samples do array. NÃO retém referência ao array (cópia
     * imediata para o buffer interno). Caller pode reusar o ShortArray.
     */
    fun writeShorts(samples: ShortArray, count: Int = samples.size) {
        val byteCount = count * 2
        if (convBuf.size < byteCount) {
            // Cresce conforme necessário (idempotente após o primeiro chunk grande)
            convBuf = ByteArray(byteCount)
        }
        // Loop manual mais rápido que ByteBuffer para PCM 16-bit LE
        var bi = 0
        for (i in 0 until count) {
            val s = samples[i].toInt()
            convBuf[bi++] = (s and 0xFF).toByte()
            convBuf[bi++] = ((s shr 8) and 0xFF).toByte()
        }
        raf.write(convBuf, 0, byteCount)
        bytesWritten += byteCount.toLong()

        val now = System.currentTimeMillis()
        if (now - lastSync > 1000L) {
            try { raf.fd.sync() } catch (_: Throwable) {}
            // Atualiza header só se ainda cabe em Int32 unsigned
            if (bytesWritten <= MAX_DATA_SIZE) {
                try {
                    val pos = raf.filePointer
                    writeHeader(raf, bytesWritten.toInt())
                    raf.seek(pos)
                } catch (_: Throwable) {}
            }
            lastSync = now
        }
    }

    fun bytes(): Long = bytesWritten

    override fun close() {
        try {
            // Final header: clampa em MAX_DATA_SIZE para nunca escrever Int inválido
            val finalSize = if (bytesWritten > MAX_DATA_SIZE) MAX_DATA_SIZE.toInt() else bytesWritten.toInt()
            writeHeader(raf, finalSize)
            raf.fd.sync()
        } catch (_: Throwable) {}
        try { raf.close() } catch (_: Throwable) {}
    }

    private fun writeHeader(target: RandomAccessFile, dataSize: Int) {
        val byteRate = sampleRate * channels * bitDepth / 8
        val blockAlign = (channels * bitDepth / 8).toShort()
        val totalDataLen = dataSize + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort())  // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitDepth.toShort())
        header.put("data".toByteArray())
        header.putInt(dataSize)

        target.seek(0)
        target.write(header.array())
    }

    companion object {
        // 100ms @ 16kHz mono = 3.2 KB. Folga 4 KB inicial.
        private const val INITIAL_BUFFER_BYTES = 4096
        // Int32 unsigned max (header WAV); na prática Java escreve Int.MAX_VALUE.
        // ~2 GB ≈ 17h @ 16kHz mono. Acima disso o WAV header não pode mais
        // refletir o tamanho real, mas o arquivo continua reproduzível pelos
        // primeiros bytes válidos.
        private const val MAX_DATA_SIZE: Long = (Int.MAX_VALUE - 36).toLong()
    }
}
