// JNI wrapper para whisper.cpp.
// - Suporta transcrição por chunks (cada call processa uma janela do áudio).
// - Suporta cancelamento real via abort_callback (Java seta cancel=true).
// - Reporta progresso real via callback.
// - Copia o FloatArray para um buffer nativo (GetFloatArrayRegion) ANTES de
//   transcrever. NÃO usa GetPrimitiveArrayCritical: pinnar o array durante o
//   whisper_full (segundos a minutos) suspende o GC e congela o app inteiro.

#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <android/log.h>
#include "whisper.h"

#define TAG "whisperjni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::atomic<int>  g_progress{0};
static std::atomic<bool> g_cancel{false};

static void on_whisper_progress(struct whisper_context* /*ctx*/,
                                struct whisper_state* /*state*/,
                                int progress,
                                void* /*user_data*/) {
    g_progress.store(progress, std::memory_order_relaxed);
}

static bool on_whisper_abort(void* /*user_data*/) {
    return g_cancel.load(std::memory_order_relaxed);
}

static std::string escape_json(const char* s) {
    std::string out;
    if (!s) return out;
    for (const char* p = s; *p; ++p) {
        unsigned char c = (unsigned char)*p;
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b";  break;
            case '\f': out += "\\f";  break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += (char)c;
                }
        }
    }
    return out;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_aulalogger_transcription_WhisperJNI_nativeInit(
    JNIEnv* env, jobject /*thiz*/, jstring jModelPath
) {
    const char* path = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("Loading model from %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    // Flash attention DESLIGADO (é o default do whisper.cpp). Em CPU o ganho é
    // marginal e era um vetor de instabilidade/erro no whisper_full com certos
    // modelos (ex.: large-v3-turbo) combinados com VAD. Prioridade: a
    // transcrição TERMINAR sem erro. Reavaliar se quiser arrancar +velocidade.
    cparams.flash_attn = false;

    struct whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(jModelPath, path);

    if (ctx == nullptr) {
        LOGE("whisper_init_from_file_with_params returned null");
        return 0L;
    }
    LOGI("Model loaded ok");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jint JNICALL
Java_com_aulalogger_transcription_WhisperJNI_nativeGetProgress(
    JNIEnv* /*env*/, jobject /*thiz*/
) {
    return g_progress.load(std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_aulalogger_transcription_WhisperJNI_nativeSetCancel(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean cancel
) {
    g_cancel.store(cancel != JNI_FALSE, std::memory_order_relaxed);
}

// Reseta o progresso global ANTES de iniciar um novo chunk. Sem isso, o
// poller Kotlin lê o 100 residual do chunk anterior na primeira amostra,
// fazendo a barra "pular pra frente e voltar".
JNIEXPORT void JNICALL
Java_com_aulalogger_transcription_WhisperJNI_nativeResetProgress(
    JNIEnv* /*env*/, jobject /*thiz*/
) {
    g_progress.store(0, std::memory_order_relaxed);
}

JNIEXPORT jstring JNICALL
Java_com_aulalogger_transcription_WhisperJNI_nativeTranscribe(
    JNIEnv* env, jobject /*thiz*/,
    jlong ctxPtr, jfloatArray jSamples, jstring jLanguage, jint threads,
    jstring jVadModelPath
) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx == nullptr) return env->NewStringUTF("[]");

    jsize sampleCount = env->GetArrayLength(jSamples);

    // VAD model path (pode ser vazio = desabilitado). Copiamos ANTES do
    // GetPrimitiveArrayCritical pois chamadas JNI são proibidas dentro da
    // região crítica.
    const char* vadPath = nullptr;
    std::string vadPathStr;
    if (jVadModelPath != nullptr) {
        vadPath = env->GetStringUTFChars(jVadModelPath, nullptr);
        if (vadPath) vadPathStr = vadPath;
        if (vadPath) env->ReleaseStringUTFChars(jVadModelPath, vadPath);
    }
    const bool useVad = !vadPathStr.empty();

    const char* lang = env->GetStringUTFChars(jLanguage, nullptr);

    // Copia os samples para um buffer nativo SEM região crítica. Pinnar o array
    // (GetPrimitiveArrayCritical) durante todo o whisper_full — que leva
    // segundos a minutos — pode suspender o GC do app inteiro, congelando a UI
    // e todas as outras threads (causa nº 1 de "trava em vários momentos").
    // O custo da cópia (~4 MB por janela de 60s) é desprezível perto disso.
    std::vector<float> audio(sampleCount > 0 ? (size_t) sampleCount : 0);
    if (sampleCount > 0) {
        env->GetFloatArrayRegion(jSamples, 0, sampleCount, audio.data());
    }

    g_progress.store(0, std::memory_order_relaxed);
    g_cancel.store(false, std::memory_order_relaxed);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language        = lang;
    params.translate       = false;
    params.token_timestamps = false;
    params.print_progress  = false;
    params.print_special   = false;
    params.print_realtime  = false;
    params.print_timestamps = false;
    params.suppress_blank  = true;
    params.suppress_nst    = true;
    params.n_threads       = threads;
    // no_context=true: cada janela é independente. Evita propagação de
    // halucinações entre chunks (tradeoff: leve perda de coerência).
    params.no_context      = true;
    params.single_segment  = false;

    // Fallback de temperatura: quando o decoder greedy falha pelos thresholds
    // de entropia/logprob, faz retry com temperatura maior. Com inc=0.4 são até
    // 3 tentativas (0.0, 0.4, 0.8) em vez de 6 (0.0..1.0) — metade do custo de
    // fallback no pior caso, mantendo a proteção anti-alucinação (o VAD já
    // remove a maior parte do silêncio que dispara esses retries).
    params.temperature       = 0.0f;
    params.temperature_inc   = 0.4f;

    params.no_speech_thold   = 0.6f;
    params.entropy_thold     = 2.4f;
    params.logprob_thold     = -1.0f;

    // Initial prompt neutro: só sinaliza idioma, sem enviesar domínio.
    // Aulas podem ser de yoga, história, programação — não força "técnico".
    // Tokens de prompt entram no contexto da janela (mesmo com no_context).
    static const char* PT_INITIAL_PROMPT = "Aula em português brasileiro.";
    if (lang && std::string(lang) == "pt") {
        params.initial_prompt = PT_INITIAL_PROMPT;
    }

    params.progress_callback           = &on_whisper_progress;
    params.progress_callback_user_data = nullptr;

    params.abort_callback           = &on_whisper_abort;
    params.abort_callback_user_data = nullptr;

    // VAD (Voice Activity Detection): o whisper.cpp roda o Silero internamente,
    // transcreve SÓ os trechos com fala e remapeia os timestamps para a linha
    // do tempo original. Ganho duplo:
    //  - Velocidade: pula silêncio (30-50% do tempo de uma aula real).
    //  - Qualidade: elimina alucinações ("obrigado", "[música]") nas pausas.
    if (useVad) {
        params.vad            = true;
        params.vad_model_path = vadPathStr.c_str();
        whisper_vad_params vp = whisper_vad_default_params();
        vp.threshold               = 0.5f;   // prob. mínima de fala
        vp.min_speech_duration_ms  = 250;    // ignora estalos curtos
        vp.min_silence_duration_ms = 200;    // pausa mínima p/ fechar segmento
        vp.max_speech_duration_s   = 30.0f;  // casa com janela do encoder
        vp.speech_pad_ms           = 60;     // não corta início/fim de palavra
        vp.samples_overlap         = 0.10f;  // costura entre segmentos VAD
        params.vad_params          = vp;
        LOGI("VAD habilitado: %s", vadPathStr.c_str());
    }

    LOGI("whisper_full %d samples lang=%s threads=%d vad=%d", sampleCount, lang, threads, useVad ? 1 : 0);
    int result = whisper_full(ctx, params, audio.data(), sampleCount);

    env->ReleaseStringUTFChars(jLanguage, lang);

    g_progress.store(100, std::memory_order_relaxed);

    if (g_cancel.load(std::memory_order_relaxed)) {
        LOGI("Transcrição cancelada pelo usuário");
        return env->NewStringUTF("[]");
    }

    if (result != 0) {
        LOGE("whisper_full failed: %d", result);
        // Sentinela distinto de "[]" (silêncio legítimo): permite ao Kotlin
        // saber que houve FALHA real e tentar fallback (ex.: sem VAD).
        return env->NewStringUTF("WHISPER_ERR");
    }

    int n = whisper_full_n_segments(ctx);
    std::string json = "[";
    for (int i = 0; i < n; ++i) {
        const char* text = whisper_full_get_segment_text(ctx, i);
        int64_t t0_10ms = whisper_full_get_segment_t0(ctx, i);
        int64_t t1_10ms = whisper_full_get_segment_t1(ctx, i);
        int64_t t0_ms = t0_10ms * 10;
        int64_t t1_ms = t1_10ms * 10;

        if (i > 0) json += ",";
        json += "{\"t0\":";
        json += std::to_string(t0_ms);
        json += ",\"t1\":";
        json += std::to_string(t1_ms);
        json += ",\"text\":\"";
        json += escape_json(text ? text : "");
        json += "\"}";
    }
    json += "]";

    LOGI("Returned %d segments", n);
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT void JNICALL
Java_com_aulalogger_transcription_WhisperJNI_nativeFree(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong ctxPtr
) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Whisper context freed");
    }
}

} // extern "C"
