package com.nuvio.tv.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubRawUrlResolverTest {

    @Test
    fun `converts github blob url to raw`() {
        val input = "https://github.com/mayastream/maya-stream/blob/main/catalog/plugins.json"
        val expected = "https://raw.githubusercontent.com/mayastream/maya-stream/main/catalog/plugins.json"
        assertEquals(expected, GitHubRawUrlResolver.toRawUrl(input))
    }

    @Test
    fun `leaves raw url unchanged`() {
        val url = "https://raw.githubusercontent.com/mayastream/maya-stream/main/catalog/addons.json"
        assertEquals(url, GitHubRawUrlResolver.toRawUrl(url))
    }

    @Test
    fun `leaves non github url unchanged`() {
        val url = "https://v3-cinemeta.strem.io/manifest.json"
        assertEquals(url, GitHubRawUrlResolver.toRawUrl(url))
    }
}
