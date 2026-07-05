package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamQualityUpgradeTest {

    private val context = StreamRankingContext(
        policy = StreamSelectionPolicy.FAST_START,
        installedAddonNames = setOf("Addon"),
        allowTorrents = true
    )

    @Test
    fun `selectUpgradeCandidate picks higher resolution stream`() {
        val current = stream(name = "720p", quality = 720)
        val upgrade = stream(name = "1080p", quality = 1080)
        val picked = StreamQualityUpgrade.selectUpgradeCandidate(
            allStreams = listOf(current, upgrade),
            currentStream = current,
            currentResolution = 720,
            currentBingeGroup = null,
            triedKeys = emptySet(),
            context = context
        )
        assertEquals(upgrade, picked)
    }

    @Test
    fun `selectUpgradeCandidate skips same or lower resolution`() {
        val current = stream(name = "1080p", quality = 1080)
        val same = stream(name = "1080p alt", quality = 1080, addon = "Other")
        val lower = stream(name = "720p", quality = 720)
        val picked = StreamQualityUpgrade.selectUpgradeCandidate(
            allStreams = listOf(current, same, lower),
            currentStream = current,
            currentResolution = 1080,
            currentBingeGroup = null,
            triedKeys = emptySet(),
            context = context
        )
        assertNull(picked)
    }

    @Test
    fun `selectUpgradeCandidate prefers binge group match`() {
        val current = stream(name = "720p", quality = 720, bingeGroup = "group-a")
        val binge1080 = stream(name = "1080p binge", quality = 1080, bingeGroup = "group-a")
        val other1080 = stream(name = "1080p other", quality = 1080, bingeGroup = "group-b", addon = "Other")
        val picked = StreamQualityUpgrade.selectUpgradeCandidate(
            allStreams = listOf(current, binge1080, other1080),
            currentStream = current,
            currentResolution = 720,
            currentBingeGroup = "group-a",
            triedKeys = emptySet(),
            context = context
        )
        assertEquals(binge1080, picked)
    }

    @Test
    fun `canAttemptUpgrade blocks while buffering`() {
        assertTrue(
            !StreamQualityUpgrade.canAttemptUpgrade(
                enabled = true,
                upgradeCount = 0,
                lastUpgradeAtMs = 0L,
                stablePlaybackSinceMs = System.currentTimeMillis() - 60_000L,
                nowMs = System.currentTimeMillis(),
                playheadMs = 30_000L,
                isBuffering = true,
                userPinnedSource = false,
                pendingFailoverCancel = false,
                hasRenderedFirstFrame = true
            )
        )
    }

    @Test
    fun `resolvePlaybackResolution uses max of video height and metadata`() {
        val stream = stream(name = "720p", quality = 720)
        assertEquals(1080, StreamQualityUpgrade.resolvePlaybackResolution(1080, stream))
        assertEquals(720, StreamQualityUpgrade.resolvePlaybackResolution(480, stream))
    }

    private fun stream(
        name: String,
        quality: Int,
        addon: String = "Addon",
        bingeGroup: String? = null
    ): Stream = Stream(
        name = name,
        title = null,
        description = null,
        url = "https://cdn.example/$name.mp4",
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = bingeGroup?.let {
            com.nuvio.tv.domain.model.StreamBehaviorHints(
                notWebReady = null,
                bingeGroup = it,
                countryWhitelist = null,
                proxyHeaders = null,
                filename = null
            )
        },
        addonName = addon,
        addonLogo = null,
        qualityValue = quality
    )
}
