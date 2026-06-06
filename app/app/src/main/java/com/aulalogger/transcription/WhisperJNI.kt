package com.aulalogger.transcription

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bridge JNI para whisper.cpp.
 * Carrega libwhisperjni.so na primeira chamada.
 *
 * Modelo de uso recomendado:
 *   ensureLoaded()
 *   if (!isInitialized()) init(modelPath)
 *   transcribe(samples, lang, threads)   // pode ser chamado várias vezes
 *   ...
 *   free()  // só quando o processo for encerrar / trocar de modelo
 *
 * Manter o ctx vivo entre transcrições evita recarregar 500MB do disco.
 */
object WhisperJNI {

    @Serializable
    data class Segment(val t0: Long, val t1: Long, val text: String)

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var loaded = false
    @Volatile private var ctxPtr: Long = 0L
    @Volatile private var loadedModelPath: String? = null

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                System.loadLibrary("whisperjni")
                loaded = true
                Log.i(TAG, "libwhisperjni.so loaded")
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao carregar libwhisperjni.so", t)
                throw t
            }
        }
    }

    /** Carrega o modelo do disco (idempotente: se já está carregado o mesmo path, não faz nada). */
    @Synchronized
    fun init(modelPath: String): Boolean {
        ensureLoaded()
        if (ctxPtr != 0L && loadedModelPath == modelPath) return true
        if (ctxPtr != 0L) free()
        ctxPtr = nativeInit(modelPath)
        loadedModelPath = if (ctxPtr != 0L) modelPath else null
        return ctxPtr != 0L
    }

    @Synchronized
    fun isInitialized(): Boolean = ctxPtr != 0L

    /**
     * Retorna a lista de segmentos com timestamps em ms (relativos ao início do FloatArray).
     * @param vadModelPath caminho do modelo Silero VAD (.bin). Vazio = VAD desativado.
     */
    @Synchronized
    fun transcribe(
        samples: FloatArray,
        language: String = "pt",
        threads: Int = 4,
        vadModelPath: String = ""
    ): List<Segment> {
        if (ctxPtr == 0L) error("Whisper não inicializado — chame init() antes")
        val raw = nativeTranscribe(ctxPtr, samples, language, threads, vadModelPath)
        // Sentinela do native: whisper_full retornou erro (≠ silêncio "[]").
        // Lança para o chamador decidir fallback (ex.: tentar sem VAD).
        if (raw == WHISPER_ERR) error("whisper_full falhou no motor nativo")
        return try {
            json.decodeFromString<List<Segment>>(raw)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao parsear segmentos: $raw", t)
            emptyList()
        }
    }

    @Synchronized
    fun free() {
        if (ctxPtr != 0L) {
            nativeFree(ctxPtr)
            ctxPtr = 0L
            loadedModelPath = null
        }
    }

    /** Progresso atual da inferência ativa (0..100). Pollado pelo TranscriptionService. */
    fun getProgress(): Int = if (loaded) nativeGetProgress() else 0

    /**
     * Sinaliza ao native para abortar `whisper_full` na próxima oportunidade
     * (entre tokens). Cancel real, não só cancel de coroutine.
     */
    fun cancel() {
        if (loaded) nativeSetCancel(true)
    }

    /** Reseta o flag de cancel antes de uma nova transcrição. */
    fun resetCancel() {
        if (loaded) nativeSetCancel(false)
    }

    /**
     * Zera o progresso global ANTES de iniciar um novo chunk. Evita que o
     * poller leia o 100 residual do chunk anterior e a barra "pule".
     */
    fun resetProgress() {
        if (loaded) nativeResetProgress()
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(ctxPtr: Long, samples: FloatArray, language: String, threads: Int, vadModelPath: String): String
    private external fun nativeFree(ctxPtr: Long)
    private external fun nativeGetProgress(): Int
    private external fun nativeSetCancel(cancel: Boolean)
    private external fun nativeResetProgress()

    private const val TAG = "WhisperJNI"
    private const val WHISPER_ERR = "WHISPER_ERR"
}
