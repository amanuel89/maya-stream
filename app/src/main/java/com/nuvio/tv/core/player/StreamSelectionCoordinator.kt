package com.nuvio.tv.core.player

import android.content.Context
import com.nuvio.tv.domain.model.Stream
import javax.inject.Inject
import javax.inject.Singleton

data class StreamSelectionResult(
    val stream: Stream?,
    val ranked: List<RankedStream>,
    val probeMetadata: StreamProbeMetadata? = null,
    val probeRan: Boolean = false
)

@Singleton
class StreamSelectionCoordinator @Inject constructor(
    private val viabilityProber: StreamViabilityProber
) {
    fun rank(streams: List<Stream>, context: StreamRankingContext): List<RankedStream> {
        return StreamHeuristicEngine.rank(streams, context)
    }

    suspend fun selectBest(
        streams: List<Stream>,
        context: StreamRankingContext,
        probeConfig: ViabilityProbeConfig,
        appContext: Context,
        probesRemaining: Int = probeConfig.maxProbesPerSession
    ): StreamSelectionResult {
        val ranked = StreamHeuristicEngine.rank(streams, context)
        if (ranked.isEmpty()) {
            return StreamSelectionResult(stream = null, ranked = emptyList())
        }

        val skipProbe = !probeConfig.enabled ||
            context.policy.skipViabilityProbeByDefault ||
            (probeConfig.skipOnMetered && StreamViabilityProber.isMeteredNetwork(appContext))

        if (skipProbe) {
            val best = ranked.first()
            return StreamSelectionResult(
                stream = best.stream,
                ranked = ranked,
                probeMetadata = best.probeMetadata,
                probeRan = false
            )
        }

        val viability = viabilityProber.applyViability(
            ranked = ranked,
            config = probeConfig,
            isMeteredNetwork = StreamViabilityProber.isMeteredNetwork(appContext),
            probesRemaining = probesRemaining
        )
        val best = viability.ranked.firstOrNull()
        return StreamSelectionResult(
            stream = best?.stream,
            ranked = viability.ranked,
            probeMetadata = best?.probeMetadata,
            probeRan = viability.probeRan
        )
    }

    fun isEarlyPickEligible(
        ranked: List<RankedStream>,
        policy: StreamSelectionPolicy,
        probeRan: Boolean
    ): Boolean {
        return StreamHeuristicEngine.isEarlyPickEligible(ranked, policy, probeRan)
    }
}
