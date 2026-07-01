package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamScoreBucketsTest {

    @Test
    fun delivery_prefers_hls_over_mp4() {
        val hls = deliveryStream(url = "https://cdn.example.com/movie.m3u8")
        val mp4 = deliveryStream(url = "https://cdn.example.com/movie.mp4")
        assertTrue(StreamScoreBuckets.deliveryScore(hls) > StreamScoreBuckets.deliveryScore(mp4))
    }

    @Test
    fun quality_prefers_720_over_1080_for_fast_start() {
        val hd = stream(name = "Stream 1080p", qualityValue = 1080)
        val ideal = stream(name = "Stream 720p", qualityValue = 720)
        assertTrue(
            StreamScoreBuckets.qualityScore(ideal, StreamSelectionPolicy.FAST_START) >
                StreamScoreBuckets.qualityScore(hd, StreamSelectionPolicy.FAST_START)
        )
    }

    @Test
    fun size_sweet_spot_scores_highest() {
        val sweet = stream(sizeBytes = 1_500L * 1024 * 1024)
        val huge = stream(sizeBytes = 12L * 1024 * 1024 * 1024)
        assertTrue(StreamScoreBuckets.sizeScore(sweet) > StreamScoreBuckets.sizeScore(huge))
    }

    @Test
    fun remux_penalty_detected() {
        assertTrue(StreamScoreBuckets.hasRemuxPenalty(stream(name = "Movie 1080p BluRay REMUX")))
    }

    private fun deliveryStream(url: String) = stream(url = url)

    private fun stream(
        name: String = "Stream",
        url: String? = "https://cdn.example.com/stream.m3u8",
        qualityValue: Int = 720,
        sizeBytes: Long? = null
    ): Stream = Stream(
        name = name,
        title = name,
        description = null,
        url = url,
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = "Plugin",
        addonLogo = null,
        qualityValue = qualityValue,
        sizeBytes = sizeBytes
    )
}
