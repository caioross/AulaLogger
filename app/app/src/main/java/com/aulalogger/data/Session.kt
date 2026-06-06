package com.aulalogger.data

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSec: Long,
    val audioFile: String,
    val audioBytes: Long,
    val transcript: String = "",
    val analysis: String = "",
    val analysisProvider: String = "",
    val analysisModel: String = "",
    val status: String = STATUS_COMPLETED,
    val lastUpdatedAt: Long = 0L
) {
    companion object {
        const val STATUS_COMPLETED = "completed"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_RECOVERED = "recovered"
    }
}
