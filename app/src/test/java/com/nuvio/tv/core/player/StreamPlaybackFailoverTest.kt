package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPlaybackFailoverTest {

    @Test
    fun `selectNextCandidate excludes current and tried streams`() {
        val current = stream("A", url = "https://a.com/1.mp4", quality = 1080)
        val tried = stream("B", url = "https://b.com/2.mp4", quality = 1080)
        val candidate = stream("C", url = "https://c.com/3.mp4", quality = 720)

        val pick = StreamPlaybackFailover.selectNextCandidate(
            allStreams = listOf(current, tried, candidate),
            currentStream = current,
            triedKeys = setOf(tried.playbackMergeKey()),
            context = StreamRankingContext(),
            currentQualityValue = 1080,
            currentBingeGroup = null
        )

        assertNotNull(pick)
        assertEquals(candidate, pick!!.stream)
    }

    @Test
    fun `quality proximity prefers same resolution band`() {
        val streams = listOf(
            stream("Cam", url = "https://x.com/cam.mp4", quality = 2160),
            stream("HD", url = "https://x.com/hd.mp4", quality = 720)
        )
        val filtered = StreamPlaybackFailover.filterByQualityProximity(streams, currentQualityValue = 720)
        assertEquals(1, filtered.size)
        assertEquals(720, filtered.first().qualityValue)
    }

    @Test
    fun `canAttemptFailover respects grace period`() {
        val now = 100_000L
        assertFalse(
            StreamPlaybackFailover.canAttemptFailover(
                trigger = FailoverTrigger.ERROR,
                onErrorEnabled = true,
                onRebufferEnabled = false,
                failoverCount = 0,
                lastFailoverAtMs = 0L,
                stablePlaybackSinceMs = now - 30_000L,
                nowMs = now,
                playheadMs = 60_000L,
                pendingCancelActive = false,
                userPinnedSource = false
            )
        )
    }

    @Test
    fun `canAttemptFailover blocks when user pinned source`() {
        assertFalse(
            StreamPlaybackFailover.canAttemptFailover(
                trigger = FailoverTrigger.ERROR,
                onErrorEnabled = true,
                onRebufferEnabled = false,
                failoverCount = 0,
                lastFailoverAtMs = 0L,
                stablePlaybackSinceMs = 0L,
                nowMs = 200_000L,
                playheadMs = 60_000L,
                pendingCancelActive = false,
                userPinnedSource = true
            )
        )
    }

    @Test
    fun `shouldTriggerRebufferFailover after threshold in window`() {
        val now = 1_000_000L
        val timestamps = listOf(
            now - 240_000L,
            now - 180_000L,
            now - 120_000L,
            now - 60_000L
        )
        assertTrue(
            StreamPlaybackFailover.shouldTriggerRebufferFailover(
                rebufferTimestampsMs = timestamps,
                sustainedRebufferStartedAtMs = 0L,
                nowMs = now
            )
        )
    }

    @Test
    fun `selectNextCandidate returns null when no alternates`() {
        val current = stream("Only", url = "https://only.com/v.mp4", quality = 1080)
        assertNull(
            StreamPlaybackFailover.selectNextCandidate(
                allStreams = listOf(current),
                currentStream = current,
                triedKeys = emptySet(),
                context = StreamRankingContext(),
                currentQualityValue = 1080,
                currentBingeGroup = null
            )
        )
    }

    private fun stream(name: String, url: String, quality: Int): Stream = Stream(
        name = name,
        title = name,
        description = null,
        url = url,
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = name,
        addonLogo = null,
        qualityValue = quality
    )
}
