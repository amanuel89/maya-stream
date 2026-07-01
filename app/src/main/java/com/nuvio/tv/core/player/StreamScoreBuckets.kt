package com.nuvio.tv.core.player

import com.nuvio.tv.core.debrid.StreamTextSizeParser
import com.nuvio.tv.domain.model.Stream

object StreamScoreBuckets {
    private val JUNK_PATTERN = Regex(
        pattern = """\b(cam|camrip|telesync|\bts\b|hdts|workprint|\bsample\b|hdcam)\b""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val QUALITY_PATTERN = Regex("""(\d{3,4})\s*p""", RegexOption.IGNORE_CASE)
    private val REMUX_PATTERN = Regex(
        """\b(remux|blu[\s-]?ray|bluray|bdrip|brrip|iso)\b""",
        RegexOption.IGNORE_CASE
    )

    private const val GB = 1024L * 1024L * 1024L
    private const val MB = 1024L * 1024L

    fun isJunk(stream: Stream): Boolean {
        return JUNK_PATTERN.containsMatchIn(streamSearchableText(stream))
    }

    fun hasRemuxPenalty(stream: Stream): Boolean {
        return REMUX_PATTERN.containsMatchIn(streamSearchableText(stream))
    }

    fun deliveryScore(stream: Stream): Int {
        if (stream.isDirectDebrid() && !stream.getStreamUrl().isNullOrBlank()) return 100
        if (stream.isDirectDebrid()) return 95
        val url = stream.getStreamUrl().orEmpty().lowercase()
        return when {
            url.contains(".m3u8") || url.contains("m3u8") -> 90
            url.contains(".mp4") || url.contains(".m4v") -> 75
            url.contains(".mpd") || url.contains("dash") -> 65
            stream.isTorrent() -> 35
            stream.isExternal() -> 0
            url.isNotBlank() -> 70
            else -> 0
        }
    }

    fun qualityScore(stream: Stream, policy: StreamSelectionPolicy): Int {
        val resolution = resolveResolution(stream) ?: return 70
        val ideal = policy.idealResolution
        val distance = kotlin.math.abs(resolution - ideal)
        var score = (100 - kotlin.math.min(100, (distance * 0.15).toInt())).coerceIn(0, 100)
        if (resolution >= 2160) {
            score = when (policy) {
                StreamSelectionPolicy.FAST_START, StreamSelectionPolicy.MOBILE_SAVER -> kotlin.math.min(score, 60)
                StreamSelectionPolicy.BALANCED -> kotlin.math.min(score, 80)
                StreamSelectionPolicy.QUALITY_FIRST, StreamSelectionPolicy.TORRENT_SAFE -> kotlin.math.max(score, 80)
            }
        }
        return score
    }

    fun sizeScore(stream: Stream): Int {
        val bytes = stream.sizeBytes ?: StreamTextSizeParser.effectiveSizeBytes(stream) ?: return 70
        return when {
            bytes in (800L * MB)..(2560L * MB) -> 100
            bytes < 800L * MB -> 85
            bytes <= 5L * GB -> 60
            else -> {
                val extraGb = ((bytes - 5L * GB).toDouble() / GB.toDouble()).coerceAtLeast(0.0)
                kotlin.math.max(20, (60 - (extraGb * 10).toInt()).coerceAtMost(60))
            }
        }
    }

    fun healthScore(stream: Stream, installedAddonNames: Set<String>): Int {
        var score = 70
        if (stream.addonName !in installedAddonNames) {
            score = (score + 20).coerceAtMost(100)
        }
        val seeders = stream.seeders
        if (seeders != null) {
            score = when {
                seeders >= 50 -> 100
                seeders >= 10 -> 75
                seeders >= 3 -> 50
                else -> 20
            }
            if (stream.addonName !in installedAddonNames) {
                score = (score + 10).coerceAtMost(100)
            }
        }
        return score
    }

    fun penaltyPoints(stream: Stream): Int {
        return if (hasRemuxPenalty(stream)) 25 else 0
    }

    fun resolveResolution(stream: Stream): Int? {
        if (stream.qualityValue >= 0) return stream.qualityValue
        stream.quality?.toIntOrNull()?.let { return it }
        val searchable = streamSearchableText(stream)
        QUALITY_PATTERN.find(searchable)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        val parsed = stream.clientResolve?.stream?.raw?.parsed?.resolution
        if (!parsed.isNullOrBlank()) {
            return when {
                parsed.contains("2160", ignoreCase = true) || parsed.contains("4k", ignoreCase = true) -> 2160
                parsed.contains("1080", ignoreCase = true) -> 1080
                parsed.contains("720", ignoreCase = true) -> 720
                parsed.contains("480", ignoreCase = true) -> 480
                else -> parsed.filter { it.isDigit() }.toIntOrNull()
            }
        }
        return null
    }

    private fun streamSearchableText(stream: Stream): String = buildString {
        append(stream.name.orEmpty()).append(' ')
        append(stream.title.orEmpty()).append(' ')
        append(stream.description.orEmpty())
    }
}
