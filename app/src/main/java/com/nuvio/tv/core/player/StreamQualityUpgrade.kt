package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream

object StreamQualityUpgradePolicy {
    const val MIN_STABLE_PLAYBACK_MS = 30_000L
    const val MIN_PLAYHEAD_MS = 20_000L
    const val COOLDOWN_MS = 60_000L
    const val MAX_UPGRADES_PER_SESSION = 2
    const val INITIAL_DELAY_MS = 15_000L
    const val PERIODIC_CHECK_MS = 30_000L
}

object StreamQualityUpgrade {

    fun resolutionStep(value: Int): Int = when {
        value <= 0 -> -1
        value < 720 -> 0
        value < 1080 -> 1
        value < 2160 -> 2
        else -> 3
    }

    fun resolvePlaybackResolution(videoHeight: Int?, stream: Stream?): Int {
        val height = videoHeight?.takeIf { it > 0 } ?: 0
        val metadata = stream?.let { StreamScoreBuckets.resolveResolution(it) }
            ?: stream?.qualityValue?.takeIf { it > 0 }
            ?: 0
        return maxOf(height, metadata).takeIf { it > 0 } ?: -1
    }

    fun selectUpgradeCandidate(
        allStreams: List<Stream>,
        currentStream: Stream,
        currentResolution: Int,
        currentBingeGroup: String?,
        triedKeys: Set<String>,
        context: StreamRankingContext
    ): Stream? {
        val currentStep = resolutionStep(currentResolution)
        if (currentStep < 0 || currentStep >= 3) return null

        val currentKey = currentStream.playbackMergeKey()
        val higherQuality = allStreams.filter { candidate ->
            val key = candidate.playbackMergeKey()
            if (key == currentKey || key in triedKeys) return@filter false
            val candidateResolution = StreamScoreBuckets.resolveResolution(candidate) ?: return@filter false
            resolutionStep(candidateResolution) > currentStep
        }
        if (higherQuality.isEmpty()) return null

        val bingeMatched = if (!currentBingeGroup.isNullOrBlank()) {
            higherQuality.filter { it.behaviorHints?.bingeGroup == currentBingeGroup }
        } else {
            emptyList()
        }
        val pool = bingeMatched.ifEmpty { higherQuality }
        val upgradeContext = context.copy(policy = StreamSelectionPolicy.QUALITY_FIRST)
        return StreamHeuristicEngine.rank(pool, upgradeContext).firstOrNull()?.stream
    }

    fun canAttemptUpgrade(
        enabled: Boolean,
        upgradeCount: Int,
        lastUpgradeAtMs: Long,
        stablePlaybackSinceMs: Long,
        nowMs: Long,
        playheadMs: Long,
        isBuffering: Boolean,
        userPinnedSource: Boolean,
        pendingFailoverCancel: Boolean,
        hasRenderedFirstFrame: Boolean
    ): Boolean {
        if (!enabled || !hasRenderedFirstFrame) return false
        if (userPinnedSource || pendingFailoverCancel) return false
        if (isBuffering) return false
        if (upgradeCount >= StreamQualityUpgradePolicy.MAX_UPGRADES_PER_SESSION) return false
        if (lastUpgradeAtMs > 0L &&
            nowMs - lastUpgradeAtMs < StreamQualityUpgradePolicy.COOLDOWN_MS
        ) {
            return false
        }
        if (stablePlaybackSinceMs <= 0L ||
            nowMs - stablePlaybackSinceMs < StreamQualityUpgradePolicy.MIN_STABLE_PLAYBACK_MS
        ) {
            return false
        }
        if (playheadMs < StreamQualityUpgradePolicy.MIN_PLAYHEAD_MS) return false
        return true
    }
}
