package com.aulalogger.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.aulalogger.transcription.TranscriptionService
import com.aulalogger.transcription.TranscriptionState
import com.aulalogger.transcription.WhisperJNI
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SessionRepository(private val context: Context) {

    private val tag = "SessionRepository"
    // PERF-009: prettyPrint só em debug. Em release escreve 30% menos bytes.
    private val json = Json {
        prettyPrint = com.aulalogger.BuildConfig.DEBUG
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val storeFile: File
        get() = File(context.filesDir, "sessions.json")

    // Worker dedicado de I/O: persist roda em background, deduplicado.
    // Conflated channel: se 5 updates chegarem rápido, só persiste o último.
    // IMPORTANTE: declarado ANTES de _sessions porque loadAndRecover() pode
    // disparar persist() durante init.
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val persistChannel = Channel<List<Session>>(capacity = Channel.CONFLATED).also {
        ioScope.launch {
            for (snapshot in it) writeToDisk(snapshot)
        }
    }

    private val _sessions = MutableStateFlow(loadAndRecover())
    val sessions: StateFlow<List<Session>> = _sessions

    private fun loadFromDisk(): List<Session> {
        if (!storeFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<Session>>(storeFile.readText())
        } catch (t: Throwable) {
            Log.e(tag, "Falha ao decodificar sessions.json — recuperando vazio", t)
            emptyList()
        }
    }

    /**
     * Em toda inicialização: pega sessions com status IN_PROGRESS sem
     * atualização recente (>2 min). Se o arquivo .wav existir, marca como
     * RECOVERED com a duração estimada pelo header WAV ou pelo lastUpdatedAt.
     * Se .wav não existir ou for vazio, descarta.
     */
    private fun loadAndRecover(): List<Session> {
        val now = System.currentTimeMillis()
        val raw = loadFromDisk()
        val recovered = raw.mapNotNull { s ->
            if (s.status != Session.STATUS_IN_PROGRESS) return@mapNotNull s
            val staleness = now - s.lastUpdatedAt
            // BUG-023: 60s (3× heartbeat de 20s) — antes era 2 min e podia
            // classificar erroneamente sessions vivas como abandonadas.
            if (staleness < 60_000L) return@mapNotNull s  // ainda viva
            val file = File(s.audioFile)
            if (!file.exists() || file.length() < 1024) {
                Log.w(tag, "Descartando session órfã ${s.id} (sem áudio)")
                null
            } else {
                val durationFromBytes = ((file.length() - 44L).coerceAtLeast(0)) / (16_000L * 2L)
                Log.i(tag, "Recuperando session ${s.id} (${durationFromBytes}s)")
                s.copy(
                    status = Session.STATUS_RECOVERED,
                    durationSec = if (s.durationSec > 0) s.durationSec else durationFromBytes,
                    audioBytes = file.length(),
                    endedAt = if (s.endedAt > 0) s.endedAt else s.lastUpdatedAt
                )
            }
        }.sortedByDescending { it.startedAt }
        if (recovered.size != raw.size || recovered.zip(raw).any { (a, b) -> a != b }) {
            persist(recovered)
        }
        return recovered
    }

    /**
     * Não bloqueia a thread chamadora. Envia para canal conflated; se a fila
     * já tem um pendente, o último vence.
     */
    private fun persist(list: List<Session>) {
        persistChannel.trySend(list)
    }

    /** Escrita real, em IO. */
    private fun writeToDisk(list: List<Session>) {
        try {
            val tmp = File(storeFile.parentFile, "${storeFile.name}.tmp")
            tmp.writeText(json.encodeToString(list))
            if (!tmp.renameTo(storeFile)) {
                tmp.copyTo(storeFile, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            Log.e(tag, "Falha ao persistir sessions", t)
        }
    }

    @Synchronized
    fun addSession(session: Session) {
        val list = (_sessions.value + session)
            .distinctBy { it.id }
            .sortedByDescending { it.startedAt }
        _sessions.value = list
        persist(list)
    }

    /** Insere ou atualiza uma session existente. */
    @Synchronized
    fun upsertSession(session: Session) {
        val list = if (_sessions.value.any { it.id == session.id }) {
            _sessions.value.map { if (it.id == session.id) session else it }
        } else {
            _sessions.value + session
        }.sortedByDescending { it.startedAt }
        _sessions.value = list
        persist(list)
    }

    @Synchronized
    fun updateInProgress(id: String, durationSec: Long, audioBytes: Long) {
        val list = _sessions.value.map {
            if (it.id == id) it.copy(
                durationSec = durationSec,
                audioBytes = audioBytes,
                lastUpdatedAt = System.currentTimeMillis()
            ) else it
        }
        _sessions.value = list
        persist(list)
    }

    @Synchronized
    fun markCompleted(id: String, durationSec: Long, audioBytes: Long, endedAt: Long) {
        val list = _sessions.value.map {
            if (it.id == id) it.copy(
                status = Session.STATUS_COMPLETED,
                durationSec = durationSec,
                audioBytes = audioBytes,
                endedAt = endedAt,
                lastUpdatedAt = System.currentTimeMillis()
            ) else it
        }
        _sessions.value = list
        persist(list)
    }

    @Synchronized
    fun updateTranscript(id: String, transcript: String) {
        val list = _sessions.value.map {
            if (it.id == id) {
                // NEW-003: ao registrar transcript válido em sessão recuperada,
                // promover para COMPLETED — assim o badge "RECUPERADA" some na Home.
                val newStatus =
                    if (it.status == Session.STATUS_RECOVERED && transcript.isNotBlank() &&
                        !transcript.startsWith("[")) Session.STATUS_COMPLETED
                    else it.status
                it.copy(transcript = transcript, status = newStatus)
            } else it
        }
        _sessions.value = list
        persist(list)
    }

    /**
     * Retorna a próxima sessão recuperada com transcript em branco, para
     * encadeamento automático de transcrição (NEW-002).
     */
    fun nextRecoveredAwaitingTranscription(): Session? =
        _sessions.value.firstOrNull {
            it.status == Session.STATUS_RECOVERED && it.transcript.isBlank()
        }

    @Synchronized
    fun delete(id: String) {
        val target = _sessions.value.firstOrNull { it.id == id } ?: return
        // BUG-006: se essa session está sendo transcrita agora, cancela primeiro
        // para não desperdiçar CPU/bateria processando algo que será descartado.
        try {
            if (TranscriptionState.state.value.sessionId == id &&
                TranscriptionState.state.value.phase == TranscriptionState.Phase.RUNNING
            ) {
                WhisperJNI.cancel()
                TranscriptionService.cancel(context)
            }
        } catch (_: Throwable) {}
        try { File(target.audioFile).delete() } catch (_: Throwable) {}
        val list = _sessions.value.filter { it.id != id }
        _sessions.value = list
        persist(list)
    }

    @Synchronized
    fun updateAnalysis(id: String, analysis: String, provider: String, model: String) {
        val list = _sessions.value.map {
            if (it.id == id) it.copy(analysis = analysis, analysisProvider = provider, analysisModel = model)
            else it
        }
        _sessions.value = list
        persist(list)
    }

    @Synchronized
    fun renameSession(id: String, newName: String) {
        val list = _sessions.value.map {
            if (it.id == id) it.copy(name = newName) else it
        }
        _sessions.value = list
        persist(list)
    }

    fun recordingsDir(): File =
        File(context.filesDir, "recordings").apply { mkdirs() }
}
