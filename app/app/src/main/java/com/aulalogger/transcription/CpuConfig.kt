package com.aulalogger.transcription

import android.util.Log
import java.io.BufferedReader
import java.io.FileReader

/**
 * Escolhe o número de threads de inferência do whisper contando os núcleos de
 * ALTA performance (os "big" em chips big.LITTLE).
 *
 * Por que NÃO usar todos os núcleos: o whisper.cpp divide o trabalho igualmente
 * entre as threads e só termina quando a mais lenta acaba. Incluir os núcleos
 * "little" (tipicamente 3-4× mais lentos) faz as threads rápidas ficarem
 * ociosas esperando as lentas — então usar 8 núcleos costuma ser MAIS LENTO que
 * usar só os 4 rápidos. Esta é uma causa comum de "transcrição extremamente
 * lenta" em celular.
 *
 * Estratégia (a mesma do exemplo oficial whisper.android):
 *  1. Lê a frequência máxima de cada núcleo via /sys e conta os que não estão no
 *     bin de menor frequência (= big + middle cores).
 *  2. Fallback: agrupa por "CPU variant" de /proc/cpuinfo.
 *  3. Fallback final: nº de núcleos − 4.
 */
object CpuConfig {

    private const val TAG = "CpuConfig"

    /** Threads recomendadas para a inferência, limitadas a [2, 6]. */
    fun preferredThreadCount(): Int = highPerfCpuCount().coerceIn(2, 6)

    private fun highPerfCpuCount(): Int = try {
        readCpuInfo().highPerfCpuCount()
    } catch (e: Exception) {
        Log.d(TAG, "Não consegui ler info de CPU — usando heurística", e)
        (Runtime.getRuntime().availableProcessors() - 4).coerceAtLeast(2)
    }

    private fun readCpuInfo(): CpuInfo =
        CpuInfo(BufferedReader(FileReader("/proc/cpuinfo")).useLines { it.toList() })

    private class CpuInfo(private val lines: List<String>) {
        fun highPerfCpuCount(): Int = try {
            countByFrequencies()
        } catch (e: Exception) {
            Log.d(TAG, "Sem frequências — caindo para variantes de CPU", e)
            countByVariant()
        }

        private fun countByFrequencies(): Int =
            cpuValues(property = "processor") { maxCpuFrequency(it.toInt()) }.countDroppingMin()

        private fun countByVariant(): Int =
            cpuValues(property = "CPU variant") { it.substringAfter("0x").toInt(radix = 16) }
                .countKeepingMin()

        private fun cpuValues(property: String, mapper: (String) -> Int): List<Int> =
            lines.asSequence()
                .filter { it.startsWith(property) }
                .map { mapper(it.substringAfter(':').trim()) }
                .sorted()
                .toList()

        private fun List<Int>.countDroppingMin(): Int {
            if (isEmpty()) return 0
            val min = min()
            return count { it > min }
        }

        private fun List<Int>.countKeepingMin(): Int {
            if (isEmpty()) return 0
            val min = min()
            return count { it == min }
        }

        private fun maxCpuFrequency(cpuIndex: Int): Int {
            val path = "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_max_freq"
            return BufferedReader(FileReader(path)).use { it.readLine() }.trim().toInt()
        }
    }
}
