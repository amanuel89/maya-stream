package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream

object StreamPlaybackFailoverPolicy {
    const val MAX_FAILOVERS_PER_SESSION = 3
    const val FAILOVER_COOLDOWN_MS = 30_000L
    const val REBUFFER_WINDOW_MS = 300_000L
    const val REBUFFER_COUNT_THRESHOLD = 4
    const val SUSTAINED_REBUFFER_MS = 12_000L
    const val MIN_PLAYHEAD_BEFORE_FAILOVER_MS = 30_000L
    const val STABLE_PLAYBACK_GRACE_MS = 120_000L
    const val PRE_SWITCH_CANCEL_MS = 5_000L
    const val UNDO_WINDOW_MS = 60_000L
    const val POST_SWITCH_DISCLOSURE_MS = 30_000L
}

enum class FailoverTrigger {
    ERROR,
    REBUFFER
}

data class FailoverCandidate(
    val stream: Stream,
    val ranked: RankedStream
)

object StreamPlaybackFailover {

    fun selectNextCandidate(
        allStreams: List<Stream>,
        currentStream: Stream,
        triedKeys: Set<String>,
        context: StreamRankingContext,
        currentQualityValue: Int,
        currentBingeGroup: String?
    ): FailoverCandidate? {
        if (allStreams.isEmpty()) return null
        val currentKey = currentStream.playbackMergeKey()
        val eligible = allStreams.filter { stream ->
            val key = stream.playbackMergeKey()
            key != currentKey && key !in triedKeys
        }
        if (eligible.isEmpty()) return null

        val bingeMatched = if (!currentBingeGroup.isNullOrBlank()) {
            eligible.filter { it.behaviorHints?.bingeGroup == currentBingeGroup }
        } else {
            emptyList()
        }
        val pool = if (bingeMatched.isNotEmpty()) bingeMatched else eligible

        val proximityFiltered = filterByQualityProximity(pool, currentQualityValue)
        val ranked = StreamHeuristicEngine.rank(proximityFiltered.ifEmpty { pool }, context)
        val pick = ranked.firstOrNull() ?: return null
        return FailoverCandidate(stream = pick.stream, ranked = pick)
    }

    internal fun filterByQualityProximity(
        streams: List<Stream>,
        currentQualityValue: Int
    ): List<Stream> {
        if (currentQualityValue <= 0) return streams
        val currentStep = resolutionStep(currentQualityValue)
        val inBand = streams.filter { candidate ->
            val step = resolutionStep(candidate.qualityValue)
            step >= 0 && kotlin.math.abs(step - currentStep) <= 1
        }
        return inBand.ifEmpty { streams }
    }

    private fun resolutionStep(qualityValue: Int): Int = when {
        qualityValue <= 0 -> -1
        qualityValue < 720 -> 0
        qualityValue < 1080 -> 1
        qualityValue < 2160 -> 2
        else -> 3
    }

    fun canAttemptFailover(
        trigger: FailoverTrigger,
        onErrorEnabled: Boolean,
        onRebufferEnabled: Boolean,
        failoverCount: Int,
        lastFailoverAtMs: Long,
        stablePlaybackSinceMs: Long,
        nowMs: Long,
        playheadMs: Long,
        pendingCancelActive: Boolean,
        userPinnedSource: Boolean
    ): Boolean {
        if (pendingCancelActive || userPinnedSource) return false
        if (trigger == FailoverTrigger.ERROR && !onErrorEnabled) return false
        if (trigger == FailoverTrigger.REBUFFER && !onRebufferEnabled) return false
        if (failoverCount >= StreamPlaybackFailoverPolicy.MAX_FAILOVERS_PER_SESSION) return false
        if (lastFailoverAtMs > 0L && nowMs - lastFailoverAtMs < StreamPlaybackFailoverPolicy.FAILOVER_COOLDOWN_MS) {
            return false
        }
        if (stablePlaybackSinceMs > 0L &&
            nowMs - stablePlaybackSinceMs < StreamPlaybackFailoverPolicy.STABLE_PLAYBACK_GRACE_MS
        ) {
            return false
        }
        if (playheadMs in 1 until StreamPlaybackFailoverPolicy.MIN_PLAYHEAD_BEFORE_FAILOVER_MS) {
            return false
        }
        return true
    }

    fun shouldTriggerRebufferFailover(
        rebufferTimestampsMs: List<Long>,
        sustainedRebufferStartedAtMs: Long,
        nowMs: Long
    ): Boolean {
        val windowStart = nowMs - StreamPlaybackFailoverPolicy.REBUFFER_WINDOW_MS
        val recentCount = rebufferTimestampsMs.count { it >= windowStart }
        if (recentCount >= StreamPlaybackFailoverPolicy.REBUFFER_COUNT_THRESHOLD) return true
        if (sustainedRebufferStartedAtMs > 0L) {
            val sustainedMs = nowMs - sustainedRebufferStartedAtMs
            if (sustainedMs >= StreamPlaybackFailoverPolicy.SUSTAINED_REBUFFER_MS) return true
        }
        return false
    }
}
