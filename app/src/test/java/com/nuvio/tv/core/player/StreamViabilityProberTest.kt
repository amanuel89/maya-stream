package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StreamViabilityProberTest {

    private val prober = StreamViabilityProber()

    @Test
    fun skips_probe_when_disabled() = runTest {
        val ranked = listOf(sampleRanked())
        val result = prober.applyViability(
            ranked = ranked,
            config = ViabilityProbeConfig(enabled = false),
            isMeteredNetwork = false,
            probesRemaining = 3
        )
        assertFalse(result.probeRan)
        assertEquals(ranked.first().stream, result.ranked.first().stream)
    }

    @Test
    fun skips_probe_on_metered_network() = runTest {
        val ranked = listOf(sampleRanked())
        val result = prober.applyViability(
            ranked = ranked,
            config = ViabilityProbeConfig(enabled = true, skipOnMetered = true),
            isMeteredNetwork = true,
            probesRemaining = 3
        )
        assertFalse(result.probeRan)
    }

    private fun sampleRanked(): RankedStream {
        val stream = Stream(
            name = "Test",
            title = "Test",
            description = null,
            url = "https://cdn.example.com/movie.m3u8",
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = null,
            behaviorHints = null,
            addonName = "Plugin",
            addonLogo = null
        )
        return StreamHeuristicEngine.scoreStream(
            stream = stream,
            context = StreamRankingContext()
        )
    }
}
