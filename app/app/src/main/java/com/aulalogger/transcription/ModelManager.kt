package com.aulalogger.transcription

import android.content.Context
import android.util.Log
import com.aulalogger.state.AppStatusHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Modelos disponíveis. Escala de qualidade x tamanho x velocidade.
 */
enum class ModelProfile(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val approxBytes: Long,
    val minBytes: Long,
    val sha256: String,
    val description: String
) {
    MINI(
        id = "mini",
        displayName = "Mínimo (Small)",
        fileName = "ggml-small-q5_1.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        approxBytes = 181L * 1024 * 1024,
        // 90% do esperado: rejeita downloads truncados em vez de aceitar arquivo parcial
        // que crashe no init nativo.
        minBytes = (181L * 1024 * 1024 * 9) / 10,
        sha256 = "",  // não enforçamos hash exato pois HF pode publicar variantes
        description = "~181 MB · rápido · qualidade decente"
    ),
    MEDIUM(
        id = "medium",
        displayName = "Médio (Medium)",
        fileName = "ggml-medium-q5_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin",
        approxBytes = 514L * 1024 * 1024,
        minBytes = (514L * 1024 * 1024 * 9) / 10,
        sha256 = "",
        description = "~514 MB · bom equilíbrio · recomendado"
    ),
    HIGH(
        id = "high",
        displayName = "Alto (Large v3 Turbo)",
        fileName = "ggml-large-v3-turbo-q5_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        approxBytes = 574L * 1024 * 1024,
        minBytes = (574L * 1024 * 1024 * 9) / 10,
        sha256 = "",
        description = "~574 MB · qualidade máxima · turbo (rápido)"
    );

    companion object {
        // Fallback de PARSING apenas. O default real (quando o usuário ainda não
        // escolheu) é decidido por ModelManager.recommendedDefault() conforme a
        // capacidade do aparelho. O large-v3-turbo tem decoder rápido (4 camadas),
        // mas o ENCODER continua sendo o do large completo — pesado demais como
        // padrão universal em aparelhos médios, o que deixava a transcrição lenta.
        fun fromId(id: String?): ModelProfile = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

object ModelManager {

    private const val TAG = "ModelManager"
    private const val PREFS = "model_prefs"
    private const val KEY_PROFILE = "selected_profile"

    // Modelo Silero VAD em formato GGML (oficial whisper.cpp). ~2 MB.
    private const val VAD_FILE = "ggml-silero-v5.1.2.bin"
    private const val VAD_URL =
        "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v5.1.2.bin"
    private const val VAD_MIN_BYTES = 500L * 1024  // sanity: > 500 KB

    fun getSelectedProfile(context: Context): ModelProfile {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = sp.getString(KEY_PROFILE, null)
        if (stored != null) return ModelProfile.fromId(stored)
        // Sem escolha explícita: prefere um modelo JÁ instalado (não aponta para
        // um modelo não baixado, o que quebraria a transcrição). Se nada está
        // instalado, recomenda pela capacidade do aparelho.
        val installed = installedProfiles(context)
        if (installed.isNotEmpty()) return installed.maxByOrNull { it.ordinal }!!
        return recommendedDefault(context)
    }

    /**
     * Recomendação de modelo por capacidade do aparelho. Usada só quando o
     * usuário ainda não escolheu e nenhum modelo está instalado. Mira o
     * equilíbrio velocidade × qualidade em aparelhos de 2019+:
     *  - flagships (≥7 GB RAM): large-v3-turbo (HIGH), que eles aguentam;
     *  - 6 GB: medium (MEDIUM);
     *  - ≤4 GB: small (MINI), rápido e sem risco de OOM com a aula inteira.
     */
    fun recommendedDefault(context: Context): ModelProfile {
        val ramGb = totalRamGb(context)
        return when {
            ramGb >= 7.0 -> ModelProfile.HIGH
            ramGb >= 5.0 -> ModelProfile.MEDIUM
            else -> ModelProfile.MINI
        }
    }

    private fun totalRamGb(context: Context): Double = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem / (1024.0 * 1024.0 * 1024.0)
    } catch (_: Throwable) {
        4.0  // assume aparelho médio
    }

    fun vadModelFile(context: Context): File =
        File(context.filesDir, "models/$VAD_FILE").apply { parentFile?.mkdirs() }

    fun isVadInstalled(context: Context): Boolean {
        val f = vadModelFile(context)
        return f.exists() && f.length() >= VAD_MIN_BYTES
    }

    /**
     * Baixa o modelo VAD (idempotente). Best-effort: se falhar, a transcrição
     * roda sem VAD (mais lenta, mais alucinações, mas funciona).
     */
    suspend fun ensureVadModel(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isVadInstalled(context)) return@withContext true
        val target = vadModelFile(context)
        val tmp = File(target.parentFile, "$VAD_FILE.part")
        try {
            val conn = URL(VAD_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode !in 200..299) {
                Log.e(TAG, "VAD download HTTP ${conn.responseCode}")
                return@withContext false
            }
            FileOutputStream(tmp).use { out ->
                conn.inputStream.use { it.copyTo(out, 64 * 1024) }
            }
            if (tmp.length() < VAD_MIN_BYTES) {
                Log.e(TAG, "VAD truncado: ${tmp.length()} bytes")
                tmp.delete()
                return@withContext false
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true); tmp.delete()
            }
            Log.i(TAG, "Modelo VAD instalado (${target.length()} bytes)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao baixar VAD", t)
            try { tmp.delete() } catch (_: Throwable) {}
            false
        }
    }

    fun setSelectedProfile(context: Context, profile: ModelProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROFILE, profile.id).apply()
    }

    fun modelFile(context: Context, profile: ModelProfile = getSelectedProfile(context)): File =
        File(context.filesDir, "models/${profile.fileName}").apply { parentFile?.mkdirs() }

    fun isModelInstalled(context: Context, profile: ModelProfile = getSelectedProfile(context)): Boolean {
        val f = modelFile(context, profile)
        return f.exists() && f.length() >= profile.minBytes
    }

    fun installedSizeMb(context: Context, profile: ModelProfile = getSelectedProfile(context)): Int {
        val f = modelFile(context, profile)
        return if (f.exists()) (f.length() / (1024 * 1024)).toInt() else 0
    }

    /** Lista quais profiles estão instalados em disco. */
    fun installedProfiles(context: Context): Set<ModelProfile> =
        ModelProfile.entries.filter { isModelInstalled(context, it) }.toSet()

    suspend fun downloadModel(
        context: Context,
        profile: ModelProfile = getSelectedProfile(context),
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val target = modelFile(context, profile)
        val tmp = File(target.parentFile, "${target.name}.part")
        AppStatusHolder.setDownloadingProgress(0)

        try {
            val resumeFrom = if (tmp.exists()) tmp.length() else 0L
            val conn = URL(profile.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000  // download lento em 4G — folga
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            // BUG-013: solicita continuação de download parcial
            if (resumeFrom > 0L) {
                conn.setRequestProperty("Range", "bytes=$resumeFrom-")
                Log.i(TAG, "Retomando download de ${profile.id} a partir de ${resumeFrom / 1024} KB")
            }
            conn.connect()

            val code = conn.responseCode
            val appending = code == 206  // Partial Content: servidor aceitou Range
            if (resumeFrom > 0L && !appending) {
                Log.w(TAG, "Servidor não suporta Range — reiniciando do zero (HTTP $code)")
                tmp.delete()
            }

            val remoteLength = conn.contentLengthLong.coerceAtLeast(0L)
            val totalSize = if (appending) remoteLength + resumeFrom
                            else if (remoteLength > 0L) remoteLength
                            else profile.approxBytes

            var downloaded = if (appending) resumeFrom else 0L
            var lastPct = -1

            FileOutputStream(tmp, appending).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        val pct = ((downloaded * 100) / totalSize).coerceIn(0, 99).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                            AppStatusHolder.setDownloadingProgress(pct)
                        }
                    }
                }
            }

            if (tmp.length() < profile.minBytes) {
                Log.e(TAG, "Arquivo baixado parece truncado: ${tmp.length()} bytes")
                tmp.delete()
                AppStatusHolder.setDownloadFinished(false)
                return@withContext false
            }
            // Sanity: arquivo gigante demais sugere mismatch (ex: arquivo HTML
            // de erro do CDN, ou nome trocado).
            if (tmp.length() > profile.approxBytes * 2) {
                Log.e(TAG, "Arquivo suspeitosamente grande: ${tmp.length()} bytes (esperado ~${profile.approxBytes})")
                tmp.delete()
                AppStatusHolder.setDownloadFinished(false)
                return@withContext false
            }
            // Whisper.cpp ggml v3 começa com mágica "ggml"/"ggla"/"ggsa" ou
            // GGUF v1+ começa com "GGUF". Validamos magic number.
            try {
                val header = tmp.inputStream().use { it.readNBytes(4) }
                val magic = String(header)
                if (magic !in setOf("ggml", "ggla", "ggsa", "GGUF")) {
                    Log.e(TAG, "Arquivo sem magic number Whisper válido: '$magic'")
                    tmp.delete()
                    AppStatusHolder.setDownloadFinished(false)
                    return@withContext false
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao validar magic number", t)
            }

            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }

            onProgress(100)
            AppStatusHolder.setDownloadFinished(true)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao baixar modelo ${profile.id}", t)
            try { tmp.delete() } catch (_: Throwable) {}
            AppStatusHolder.setDownloadFinished(false)
            false
        }
    }

    fun deleteModel(context: Context, profile: ModelProfile) {
        try { modelFile(context, profile).delete() } catch (_: Throwable) {}
    }
}
