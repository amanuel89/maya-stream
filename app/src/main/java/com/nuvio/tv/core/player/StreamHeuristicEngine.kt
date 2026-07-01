package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState

data class StreamRankingContext(
    val policy: StreamSelectionPolicy = StreamSelectionPolicy.FAST_START,
    val installedAddonNames: Set<String> = emptySet(),
    val allowTorrents: Boolean = true
)

object StreamHeuristicEngine {

    fun rank(streams: List<Stream>, context: StreamRankingContext): List<RankedStream> {
        return streams
            .asSequence()
            .filter { isPlayable(it, context.allowTorrents) }
            .filterNot { StreamScoreBuckets.isJunk(it) }
            .map { scoreStream(it, context) }
            .sortedByDescending { it.breakdown.totalScore }
            .toList()
    }

    fun selectBest(streams: List<Stream>, context: StreamRankingContext): Stream? {
        return rank(streams, context).firstOrNull()?.stream
    }

    fun isDominantWinner(ranked: List<RankedStream>, policy: StreamSelectionPolicy): Boolean {
        if (ranked.isEmpty()) return false
        val top = ranked.first()
        val topScore = top.adjustedTotalScore
        if (topScore < policy.minDominantScore) return false

        val runnerUp = ranked.getOrNull(1) ?: return true
        val gap = topScore - runnerUp.adjustedTotalScore
        if (gap < policy.dominantConfidenceGap) return false

        val competingHighDelivery = ranked.drop(1).any { candidate ->
            candidate.breakdown.delivery >= policy.highDeliveryTierThreshold
        }
        if (competingHighDelivery) return false

        if (top.breakdown.viabilityDelta < 0) return false

        return true
    }

    fun isEarlyPickEligible(
        ranked: List<RankedStream>,
        policy: StreamSelectionPolicy,
        viabilityProbeRan: Boolean
    ): Boolean {
        if (ranked.isEmpty()) return false
        if (!isDominantWinner(ranked, policy)) return false
        if (viabilityProbeRan && ranked.first().breakdown.viabilityDelta < 0) return false
        return true
    }

    internal fun scoreStream(stream: Stream, context: StreamRankingContext): RankedStream {
        val weights = context.policy.weights
        val delivery = StreamScoreBuckets.deliveryScore(stream)
        val quality = StreamScoreBuckets.qualityScore(stream, context.policy)
        val size = StreamScoreBuckets.sizeScore(stream)
        val health = StreamScoreBuckets.healthScore(stream, context.installedAddonNames)
        val penalty = StreamScoreBuckets.penaltyPoints(stream)

        val weighted = delivery * weights.delivery +
            quality * weights.quality +
            size * weights.size +
            health * weights.health
        val total = (weighted - penalty * weights.penalty * 100.0).coerceAtLeast(0.0)

        return RankedStream(
            stream = stream,
            breakdown = ScoreBreakdown(
                delivery = delivery,
                quality = quality,
                size = size,
                health = health,
                penalty = penalty,
                totalScore = total
            )
        )
    }

    fun isPlayable(stream: Stream, allowTorrents: Boolean = true): Boolean {
        if (!allowTorrents && stream.isTorrent()) return false
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
