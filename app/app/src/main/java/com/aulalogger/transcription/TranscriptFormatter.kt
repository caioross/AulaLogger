package com.aulalogger.transcription

import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Formata os segmentos crus do Whisper em texto final, aplicando opcionalmente:
 *  - timestamps `[HH:MM:SS]`
 *  - diarização heurística por pausa (≥ 2s entre segmentos = troca de locutor)
 */
object TranscriptFormatter {

    fun format(
        segments: List<WhisperJNI.Segment>,
        withTimestamps: Boolean,
        withSpeakers: Boolean,
        speakerALabel: String = "Locutor A",
        speakerBLabel: String = "Locutor B"
    ): String {
        if (segments.isEmpty()) return ""

        val cleanSegs = segments
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotBlank() && !isLikelyHallucination(it) }
        if (cleanSegs.isEmpty()) return ""

        // Atribui locutores: começa em A, troca a cada pausa >= 2s
        val speakerLabels = if (withSpeakers) {
            assignSpeakers(cleanSegs, pauseThresholdMs = 2_000L, speakerALabel, speakerBLabel)
        } else List(cleanSegs.size) { "" }

        val sb = StringBuilder()
        var lastSpeaker = ""
        for ((i, seg) in cleanSegs.withIndex()) {
            val speaker = speakerLabels[i]
            val needNewLine = withSpeakers && speaker != lastSpeaker
            val needTs = withTimestamps && (needNewLine || i == 0 || cleanSegs[i].t0 - cleanSegs[i - 1].t1 > 2000)

            if (sb.isNotEmpty()) {
                if (needNewLine) sb.append("\n\n") else sb.append(' ')
            }

            val prefix = buildString {
                if (needTs) {
                    if (isNotEmpty()) append(' ')
                    append('[').append(formatTs(seg.t0)).append(']')
                }
                if (withSpeakers && needNewLine) {
                    if (isNotEmpty()) append(' ')
                    append(speaker).append(':')
                }
            }
            if (prefix.isNotEmpty()) {
                sb.append(prefix).append(' ')
            }
            sb.append(seg.text)
            lastSpeaker = speaker
        }
        return sb.toString().trim()
    }

    /**
     * Heurística: começa em "Locutor A". A cada pausa >= [pauseThresholdMs] entre
     * segmentos consecutivos, alterna para o "outro" locutor (A↔B).
     *
     * Não é diarização real (não usa embedding de voz), mas é útil pra aulas
     * onde o professor fala continuamente e alunos perguntam em pausas.
     */
    private fun assignSpeakers(
        segments: List<WhisperJNI.Segment>,
        pauseThresholdMs: Long,
        speakerA: String,
        speakerB: String
    ): List<String> {
        if (segments.isEmpty()) return emptyList()
        val labels = ArrayList<String>(segments.size)
        var current = speakerA
        labels.add(current)
        for (i in 1 until segments.size) {
            val gap = segments[i].t0 - segments[i - 1].t1
            if (gap.absoluteValue >= pauseThresholdMs) {
                current = if (current == speakerA) speakerB else speakerA
            }
            labels.add(current)
        }
        return labels
    }

    private fun formatTs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /**
     * Filtro contextual: descarta segmento somente se for curto (<1.5s) E sua
     * frase normalizada coincidir com um hallucination conhecido. Frases de
     * créditos do YouTube são removidas independente da duração.
     */
    private fun isLikelyHallucination(seg: WhisperJNI.Segment): Boolean {
        val t = seg.text.lowercase().trimEnd('.', '!', '?', ',', ' ')
        if (t in HARD_HALLUCINATIONS) return true
        val durMs = (seg.t1 - seg.t0).coerceAtLeast(0)
        return durMs < 1500L && t in SHORT_HALLUCINATIONS
    }

    /** Frases típicas de créditos automáticos do Whisper (Amara, YouTube). */
    private val HARD_HALLUCINATIONS = setOf(
        "legendas pela comunidade amara.org",
        "legendas pela comunidade",
        "subtítulos pela comunidade amara.org",
        "transcrição: amara.org",
        "obrigado por assistir",
        "obrigada por assistir",
    )

    /** Palavras curtas que aparecem em silêncio. Só descarta se segmento <1.5s. */
    private val SHORT_HALLUCINATIONS = setOf(
        "obrigado",
        "obrigada",
        "[música]",
        "música",
        "valeu",
        "tchau",
        "fim"
    )
}
