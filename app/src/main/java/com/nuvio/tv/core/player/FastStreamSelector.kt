package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState

object FastStreamSelector {
    private val IDEAL_QUALITY = 720
    private val MIN_QUALITY = 360
    private val JUNK_PATTERN = Regex(
        pattern = """\b(cam|camrip|telesync|\bts\b|hdts|workprint|\bsample\b|hdcam)\b""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val QUALITY_PATTERN = Regex("""(\d{3,4})\s*p""", RegexOption.IGNORE_CASE)

    fun selectQuickPlayStream(
        streams: List<Stream>,
        installedAddonNames: Set<String>,
        allowTorrents: Boolean = true
    ): Stream? {
        return streams
            .asSequence()
            .filter { isPlayable(it) }
            .filter { allowTorrents || !it.isTorrent() }
            .filterNot { isJunk(it) }
            .maxWithOrNull(compareBy<Stream> { score(it, installedAddonNames) })
    }

    private fun score(stream: Stream, installedAddonNames: Set<String>): Int {
        var score = 0
        if (stream.addonName !in installedAddonNames) {
            score += 10_000
        }
        score += deliveryScore(stream) * 100
        val quality = resolveQuality(stream)
        if (quality != null) {
            if (quality in MIN_QUALITY..1080) {
                score += 500 - kotlin.math.abs(quality - IDEAL_QUALITY)
            } else {
                score -= 200
            }
        } else {
            score += 200
        }
        return score
    }

    private fun deliveryScore(stream: Stream): Int {
        if (stream.isDirectDebrid()) return 5
        val url = stream.getStreamUrl().orEmpty().lowercase()
        return when {
            url.contains(".m3u8") || url.contains("m3u8") -> 4
            url.contains(".mp4") -> 3
            stream.isTorrent() -> 0
            stream.isExternal() -> 1
            else -> 2
        }
    }

    private fun resolveQuality(stream: Stream): Int? {
        if (stream.qualityValue >= 0) return stream.qualityValue
        stream.quality?.toIntOrNull()?.let { return it }
        val searchable = buildString {
            append(stream.name.orEmpty()).append(' ')
            append(stream.title.orEmpty()).append(' ')
            append(stream.description.orEmpty())
        }
        return QUALITY_PATTERN.find(searchable)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun isJunk(stream: Stream): Boolean {
        val searchable = buildString {
            append(stream.name.orEmpty()).append(' ')
            append(stream.title.orEmpty()).append(' ')
            append(stream.description.orEmpty())
        }
        return JUNK_PATTERN.containsMatchIn(searchable)
    }

    private fun isPlayable(stream: Stream): Boolean {
        if (stream.isExternal()) return false
        when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CHECKING,
            StreamDebridCacheState.NOT_CACHED,
            StreamDebridCacheState.UNKNOWN -> return false
            StreamDebridCacheState.CACHED,
            null -> Unit
        }
        return stream.getStreamUrl() != null || stream.isTorrent() || stream.isDirectDebrid()
    }
}
