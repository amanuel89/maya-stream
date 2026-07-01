package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastStreamSelectorTest {

    @Test
    fun `prefers plugin direct stream over addon torrent`() {
        val torrentio = stream(
            addonName = "Torrentio",
            url = null,
            infoHash = "a".repeat(40),
            name = "1080p"
        )
        val plugin = stream(
            addonName = "Yoru's Repo",
            url = "https://cdn.example.com/movie.m3u8",
            name = "Vixsrc 720p"
        )

        val selected = FastStreamSelector.selectQuickPlayStream(
            streams = listOf(torrentio, plugin),
            installedAddonNames = setOf("Torrentio")
        )

        assertEquals(plugin, selected)
    }

    @Test
    fun `prefers 720p over 1080p for similar delivery`() {
        val hd = stream(
            addonName = "Plugin",
            url = "https://cdn.example.com/hd.m3u8",
            name = "Stream 1080p"
        )
        val ideal = stream(
            addonName = "Plugin",
            url = "https://cdn.example.com/sd.m3u8",
            name = "Stream 720p"
        )

        val selected = FastStreamSelector.selectQuickPlayStream(
            streams = listOf(hd, ideal),
            installedAddonNames = emptySet()
        )

        assertEquals(ideal, selected)
    }

    @Test
    fun `rejects junk cam releases`() {
        val cam = stream(
            addonName = "Plugin",
            url = "https://cdn.example.com/cam.m3u8",
            name = "CAM rip 720p"
        )

        val selected = FastStreamSelector.selectQuickPlayStream(
            streams = listOf(cam),
            installedAddonNames = emptySet()
        )

        assertNull(selected)
    }

    @Test
    fun `skips torrent streams when p2p disabled`() {
        val torrent = stream(
            addonName = "Torrentio",
            url = null,
            infoHash = "a".repeat(40),
            name = "1080p"
        )

        val selected = FastStreamSelector.selectQuickPlayStream(
            streams = listOf(torrent),
            installedAddonNames = setOf("Torrentio"),
            allowTorrents = false
        )

        assertNull(selected)
    }

    private fun stream(
        addonName: String,
        url: String?,
        infoHash: String? = null,
        name: String = "Stream"
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
        addonLogo = null
    )
}
