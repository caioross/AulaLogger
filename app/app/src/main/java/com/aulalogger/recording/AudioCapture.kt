package com.aulalogger.recording

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Captura PCM 16-bit mono em frames de ~100ms.
 *
 * **Hot path optimization (BUG-002):** retorna SEMPRE o mesmo `frame` buffer
 * (pinned). Quem chama `read()` deve consumir/copiar imediatamente, antes da
 * próxima `read()`. Em 4h de gravação isso elimina 144.000 alocações de
 * ShortArray (~144MB de pressão de GC).
 *
 * Para uso seguro:
 *   - `WavWriter.writeShorts(samples, count)` aceita count; faz a cópia interna
 *     para byte buffer (que é reaproveitado).
 *
 * O `count` real é exposto via `lastReadCount`. Caller deve sempre usar isso
 * em vez de `samples.size`.
 */
class AudioCapture(
    val sampleRate: Int = 16000
) {
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        .coerceAtLeast(2048)
    private val bufferSize = minBufferSize * 4

    /** ~100 ms de áudio @ 16 kHz = 1600 samples. */
    private val frameSize = sampleRate / 10

    private var audioRecord: AudioRecord? = null
    /** Buffer reusável. Não copiado em hot path. */
    private val frame = ShortArray(frameSize)

    /** Número de samples válidos no último read(). Caller usa isso, não `frame.size`. */
    @Volatile var lastReadCount: Int = 0
        private set

    /** Última amplitude RMS lida, em escala 0..1 (0 = silêncio, 1 = saturação). */
    @Volatile var lastLevel: Float = 0f
        private set

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        try {
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord não inicializou (state=${record.state})")
                return false
            }
            record.startRecording()
            audioRecord = record
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao iniciar AudioRecord", t)
            return false
        }
    }

    /**
     * Lê próximo frame. Retorna **a referência interna** (`frame`) — NÃO armazene
     * para uso assíncrono. O `count` válido fica em `lastReadCount`.
     *
     * Retorna `null` se falhar (mic perdido, etc).
     */
    fun read(): ShortArray? {
        val rec = audioRecord ?: return null
        val n = try {
            rec.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
        } catch (_: Throwable) {
            return null
        }
        if (n <= 0) return null

        // RMS para level meter
        var sum = 0.0
        for (i in 0 until n) {
            val v = frame[i].toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / n) / 32768.0
        val db = if (rms > 1e-6) 20 * log10(rms) else -90.0
        val level = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
        lastLevel = level
        lastReadCount = n
        return frame
    }

    /**
     * Desbloqueia um `read()` pendente SEM liberar o recurso. Chamar isto antes
     * de fazer join no loop de captura: `AudioRecord.stop()` faz a leitura
     * bloqueante (READ_BLOCKING) retornar imediatamente, então o loop consegue
     * observar o flag de parada e sair. Sem isso, o read() fica preso e o
     * join/cancel nunca completa — a gravação "não para".
     */
    fun signalStop() {
        try { audioRecord?.stop() } catch (_: Throwable) {}
    }

    /** Para e libera o AudioRecord. Chamar SÓ depois que o loop de captura saiu. */
    fun release() {
        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
        lastLevel = 0f
        lastReadCount = 0
    }

    companion object {
        private const val TAG = "AudioCapture"
    }
}
