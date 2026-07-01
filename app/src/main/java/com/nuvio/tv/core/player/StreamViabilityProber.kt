package com.nuvio.tv.core.player

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.screens.player.PlayerMediaSourceFactory
import com.nuvio.tv.ui.screens.player.PlayerPlaybackNetworking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ViabilityProbeConfig(
    val enabled: Boolean = true,
    val skipOnMetered: Boolean = true,
    val maxProbesPerSession: Int = 3,
    val cacheTtlMs: Long = 5 * 60 * 1000L,
    val slowTtfbMs: Long = 1500L
)

data class ViabilityProbeResult(
    val ranked: List<RankedStream>,
    val probeRan: Boolean
)

@Singleton
class StreamViabilityProber @Inject constructor() {

    private val cache = object : LinkedHashMap<String, CachedProbeEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedProbeEntry>?): Boolean {
            return size > 64
        }
    }

    suspend fun applyViability(
        ranked: List<RankedStream>,
        config: ViabilityProbeConfig,
        isMeteredNetwork: Boolean,
        probesRemaining: Int
    ): ViabilityProbeResult {
        if (ranked.isEmpty() || !config.enabled || probesRemaining <= 0) {
            return ViabilityProbeResult(ranked, probeRan = false)
        }
        if (config.skipOnMetered && isMeteredNetwork) {
            return ViabilityProbeResult(ranked, probeRan = false)
        }

        val httpCandidates = ranked
            .filter { isHttpProbeCandidate(it.stream) }
            .take(config.maxProbesPerSession.coerceAtMost(probesRemaining))

        if (httpCandidates.isEmpty()) {
            return ViabilityProbeResult(ranked, probeRan = false)
        }

        val now = System.currentTimeMillis()
        val adjusted = ranked.toMutableList()
        var probeRan = false

        val uncached = mutableListOf<RankedStream>()
        for (candidate in httpCandidates) {
            val url = candidate.stream.getStreamUrl() ?: continue
            val headers = candidate.stream.behaviorHints?.proxyHeaders?.request.orEmpty()
            val cacheKey = probeCacheKey(url, headers)
            val cached = synchronized(cache) { cache[cacheKey] }
            if (cached != null && now - cached.cachedAtMs <= config.cacheTtlMs) {
                probeRan = true
                applyDelta(adjusted, candidate, cached.delta, cached.metadata)
            } else {
                uncached.add(candidate)
            }
        }

        if (uncached.isNotEmpty()) {
            probeRan = true
            val probeResults = withContext(Dispatchers.IO) {
                uncached.take(config.maxProbesPerSession).map { candidate ->
                    async {
                        probeCandidate(candidate, config)
                    }
                }.awaitAll()
            }
            probeResults.forEach { (candidate, delta, metadata) ->
                val url = candidate.stream.getStreamUrl() ?: return@forEach
                val headers = candidate.stream.behaviorHints?.proxyHeaders?.request.orEmpty()
                synchronized(cache) {
                    cache[probeCacheKey(url, headers)] = CachedProbeEntry(
                        delta = delta,
                        metadata = metadata,
                        cachedAtMs = System.currentTimeMillis()
                    )
                }
                applyDelta(adjusted, candidate, delta, metadata)
            }
        }

        val sorted = adjusted.sortedByDescending { it.adjustedTotalScore }
        return ViabilityProbeResult(sorted, probeRan)
    }

    private fun applyDelta(
        list: MutableList<RankedStream>,
        candidate: RankedStream,
        delta: Double,
        metadata: StreamProbeMetadata?
    ) {
        val index = list.indexOfFirst { it.stream === candidate.stream }
        if (index < 0) return
        val current = list[index]
        list[index] = current.copy(
            breakdown = current.breakdown.copy(viabilityDelta = delta),
            probeMetadata = metadata ?: current.probeMetadata
        )
    }

    private fun probeCandidate(
        ranked: RankedStream,
        config: ViabilityProbeConfig
    ): Triple<RankedStream, Double, StreamProbeMetadata?> {
        val stream = ranked.stream
        val url = stream.getStreamUrl() ?: return Triple(ranked, 0.0, null)
        val headers = stream.behaviorHints?.proxyHeaders?.request.orEmpty()
        val startedAt = System.currentTimeMillis()
        return try {
            val outcome = probeHttp(url, headers)
            val ttfbMs = System.currentTimeMillis() - startedAt
            var delta = outcome.delta
            if (ttfbMs > config.slowTtfbMs && outcome.delta > 0) {
                delta -= 3.0
            }
            Triple(
                ranked,
                delta,
                StreamProbeMetadata(
                    responseHeaders = outcome.responseHeaders,
                    sniffedMimeType = outcome.sniffedMime,
                    ttfbMs = ttfbMs
                )
            )
        } catch (_: Exception) {
            Triple(ranked, -100.0, null)
        }
    }

    private data class HttpProbeOutcome(
        val delta: Double,
        val responseHeaders: Map<String, String> = emptyMap(),
        val sniffedMime: String? = null
    )

    private fun probeHttp(url: String, headers: Map<String, String>): HttpProbeOutcome {
        val rangeResult = runCatching { probeWithRange(url, headers) }.getOrNull()
        if (rangeResult != null) return rangeResult
        val headResult = runCatching { probeWithHead(url, headers) }.getOrNull()
        return headResult ?: HttpProbeOutcome(delta = -100.0)
    }

    private fun probeWithRange(url: String, headers: Map<String, String>): HttpProbeOutcome? {
        val connection = PlayerPlaybackNetworking.openConnection(
            url = url,
            headers = headers,
            method = "GET",
            connectTimeoutMs = PROBE_TIMEOUT_MS,
            readTimeoutMs = PROBE_TIMEOUT_MS,
            range = "bytes=0-1023"
        )
        return try {
            val code = connection.responseCode
            val responseHeaders = readResponseHeaders(connection)
            if (code !in 200..299) return HttpProbeOutcome(delta = -100.0, responseHeaders = responseHeaders)
            var delta = 8.0
            if (responseHeaders.entries.any { it.key.equals("Accept-Ranges", true) && it.value.contains("bytes", true) }) {
                delta += 2.0
            }
            val sniffed = PlayerMediaSourceFactory.inferMimeType(
                url = connection.url?.toString() ?: url,
                filename = null,
                responseHeaders = responseHeaders
            )
            HttpProbeOutcome(delta = delta, responseHeaders = responseHeaders, sniffedMime = sniffed)
        } finally {
            connection.disconnect()
        }
    }

    private fun probeWithHead(url: String, headers: Map<String, String>): HttpProbeOutcome? {
        val connection = PlayerPlaybackNetworking.openConnection(
            url = url,
            headers = headers,
            method = "HEAD",
            connectTimeoutMs = PROBE_TIMEOUT_MS,
            readTimeoutMs = PROBE_TIMEOUT_MS
        )
        return try {
            val code = connection.responseCode
            val responseHeaders = readResponseHeaders(connection)
            if (code !in 200..299) return HttpProbeOutcome(delta = -100.0, responseHeaders = responseHeaders)
            var delta = 6.0
            if (responseHeaders.entries.any { it.key.equals("Accept-Ranges", true) && it.value.contains("bytes", true) }) {
                delta += 2.0
            }
            HttpProbeOutcome(delta = delta, responseHeaders = responseHeaders)
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponseHeaders(connection: HttpURLConnection): Map<String, String> {
        return buildMap {
            connection.headerFields.forEach { (key, values) ->
                if (key.isNullOrBlank()) return@forEach
                val value = values?.firstOrNull { it.isNotBlank() }?.trim() ?: return@forEach
                put(key, value)
            }
        }
    }

    private fun isHttpProbeCandidate(stream: Stream): Boolean {
        if (stream.isTorrent() || stream.isExternal()) return false
        if (stream.needsLocalDebridResolve()) return false
        val url = stream.getStreamUrl() ?: return false
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }

    private fun probeCacheKey(url: String, headers: Map<String, String>): String {
        return buildString {
            append(url)
            headers.entries.sortedBy { it.key }.forEach { (key, value) ->
                append('|').append(key).append('=').append(value)
            }
        }
    }

    private data class CachedProbeEntry(
        val delta: Double,
        val metadata: StreamProbeMetadata?,
        val cachedAtMs: Long
    )

    companion object {
        private const val PROBE_TIMEOUT_MS = 2000

        fun isMeteredNetwork(context: Context): Boolean {
            val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
    }
}
