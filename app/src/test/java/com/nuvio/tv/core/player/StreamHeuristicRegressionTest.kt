package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamHeuristicRegressionTest {

  private val context = StreamRankingContext(
      policy = StreamSelectionPolicy.FAST_START,
      installedAddonNames = setOf("Torrentio"),
      allowTorrents = true
  )

  @Test
  fun regression_plugin_m3u8_720p_beats_torrentio_1080p() {
    val torrentio = stream(
        addonName = "Torrentio",
        url = null,
        infoHash = "a".repeat(40),
        name = "1080p WEB-DL",
        qualityValue = 1080,
        sizeBytes = 2L * 1024 * 1024 * 1024
    )
    val plugin = stream(
        addonName = "Vixsrc",
        url = "https://cdn.example.com/movie.m3u8",
        name = "Vixsrc 720p",
        qualityValue = 720,
        sizeBytes = 1_200L * 1024 * 1024
    )

    assertEquals(plugin, StreamHeuristicEngine.selectBest(listOf(torrentio, plugin), context))
  }

  @Test
  fun regression_remux_loses_to_webdl_same_res() {
    val remux = stream(
        name = "Movie 1080p BluRay REMUX",
        qualityValue = 1080,
        sizeBytes = 30L * 1024 * 1024 * 1024
    )
    val webdl = stream(
        name = "Movie 1080p WEB-DL",
        qualityValue = 1080,
        sizeBytes = 2L * 1024 * 1024 * 1024
    )

    assertEquals(
        webdl,
        StreamHeuristicEngine.selectBest(listOf(remux, webdl), context.copy(installedAddonNames = emptySet()))
    )
  }

  @Test
  fun regression_size_sweet_spot_beats_oversized() {
    val sweet = stream(
        name = "Movie 720p",
        qualityValue = 720,
        sizeBytes = 1_500L * 1024 * 1024
    )
    val oversized = stream(
        name = "Movie 720p",
        qualityValue = 720,
        sizeBytes = 12L * 1024 * 1024 * 1024
    )

    assertEquals(
        sweet,
        StreamHeuristicEngine.selectBest(listOf(oversized, sweet), context.copy(installedAddonNames = emptySet()))
    )
  }

  private fun stream(
      addonName: String = "Plugin",
      url: String? = "https://cdn.example.com/movie.m3u8",
      infoHash: String? = null,
      name: String,
      qualityValue: Int,
      sizeBytes: Long
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
      qualityValue = qualityValue,
      sizeBytes = sizeBytes
  )
}
