package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHeuristicEngineTest {

    private val context = StreamRankingContext(
        policy = StreamSelectionPolicy.FAST_START,
        installedAddonNames = setOf("Torrentio"),
        allowTorrents = true
    )

    @Test
    fun prefers_plugin_m3u8_over_addon_torrent() {
        val torrentio = stream(
            addonName = "Torrentio",
            url = null,
            infoHash = "a".repeat(40),
            name = "1080p"
        )
        val plugin = stream(
            addonName = "Yoru's Repo",
            url = "https://cdn.example.com/movie.m3u8",
            name = "Vixsrc 720p",
            qualityValue = 720
        )

        val selected = StreamHeuristicEngine.selectBest(listOf(torrentio, plugin), context)
        assertEquals(plugin, selected)
    }

    @Test
    fun prefers_720p_over_1080p_for_similar_delivery() {
        val hd = stream(
            url = "https://cdn.example.com/hd.m3u8",
            name = "Stream 1080p",
            qualityValue = 1080
        )
        val ideal = stream(
            url = "https://cdn.example.com/sd.m3u8",
            name = "Stream 720p",
            qualityValue = 720
        )

        val selected = StreamHeuristicEngine.selectBest(listOf(hd, ideal), context.copy(installedAddonNames = emptySet()))
        assertEquals(ideal, selected)
    }

    @Test
    fun rejects_junk_cam_releases() {
        val cam = stream(
            url = "https://cdn.example.com/cam.m3u8",
            name = "CAM rip 720p"
        )

        val selected = StreamHeuristicEngine.selectBest(listOf(cam), context.copy(installedAddonNames = emptySet()))
        assertNull(selected)
    }

    @Test
    fun skips_torrent_streams_when_p2p_disabled() {
        val torrent = stream(
            addonName = "Torrentio",
            url = null,
            infoHash = "a".repeat(40),
            name = "1080p"
        )

        val selected = StreamHeuristicEngine.selectBest(
            streams = listOf(torrent),
            context = context.copy(allowTorrents = false)
        )
        assertNull(selected)
    }

    @Test
    fun dominant_winner_requires_confidence_gap() {
        val closeA = ranked(total = 86.0, delivery = 90)
        val closeB = ranked(total = 80.0, delivery = 70)
        assertTrue(!StreamHeuristicEngine.isDominantWinner(listOf(closeA, closeB), StreamSelectionPolicy.FAST_START))
    }

    private fun ranked(total: Double, delivery: Int): RankedStream {
        return RankedStream(
            stream = stream(),
            breakdown = ScoreBreakdown(
                delivery = delivery,
                quality = 80,
                size = 80,
                health = 70,
                penalty = 0,
                totalScore = total
            )
        )
    }

    private fun stream(
        addonName: String = "Plugin",
        url: String? = "https://cdn.example.com/movie.m3u8",
        infoHash: String? = null,
        name: String = "Stream",
        qualityValue: Int = 720
    ): Stream = Stream(
        name = name,
        title = name,
        description = null,
        url = url,
        ytId = null,
        infoHash = infoHash,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = addonName,
        addonLogo = null,
        qualityValue = qualityValue
    )
}
