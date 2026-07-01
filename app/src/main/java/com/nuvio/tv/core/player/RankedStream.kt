package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream

data class ScoreBreakdown(
    val delivery: Int,
    val quality: Int,
    val size: Int,
    val health: Int,
    val penalty: Int,
    val totalScore: Double,
    val viabilityDelta: Double = 0.0
) {
    val adjustedTotalScore: Double get() = totalScore + viabilityDelta

    fun formatForLog(stream: Stream): String {
        val label = stream.name ?: stream.title ?: stream.addonName
        return "$label | delivery=$delivery quality=$quality size=$size health=$health penalty=$penalty → total=${"%.1f".format(totalScore)}"
    }
}

data class RankedStream(
    val stream: Stream,
    val breakdown: ScoreBreakdown,
    val probeMetadata: StreamProbeMetadata? = null
) {
    val adjustedTotalScore: Double get() = breakdown.adjustedTotalScore
}

data class StreamProbeMetadata(
    val responseHeaders: Map<String, String> = emptyMap(),
    val sniffedMimeType: String? = null,
    val ttfbMs: Long? = null
)
