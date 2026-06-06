package com.aulalogger.transcription

import android.content.Context

object TranscriptionPrefs {

    private const val PREFS = "transcription_prefs"
    private const val KEY_TIMESTAMPS = "timestamps_enabled"
    private const val KEY_SPEAKERS = "speakers_enabled"
    private const val KEY_AUTO_TRANSCRIBE = "auto_transcribe_enabled"

    /**
     * Quando ligado, a transcrição inicia sozinha assim que uma gravação
     * termina. Default = true (comportamento esperado: gravou → transcreve).
     * O usuário pode desligar em Configurações para controlar quando gastar
     * CPU/bateria (ex.: deixar para transcrever várias aulas de uma vez à noite).
     */
    fun autoTranscribeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_TRANSCRIBE, true)

    fun setAutoTranscribeEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_TRANSCRIBE, value).apply()
    }

    fun timestampsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TIMESTAMPS, false)

    fun setTimestampsEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TIMESTAMPS, value).apply()
    }

    fun speakersEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SPEAKERS, false)

    fun setSpeakersEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SPEAKERS, value).apply()
    }
}
