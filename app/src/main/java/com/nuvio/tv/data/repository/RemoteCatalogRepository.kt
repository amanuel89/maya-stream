package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.core.config.MayaStreamConfig
import com.nuvio.tv.core.network.GitHubRawUrlResolver
import com.nuvio.tv.domain.model.PluginSourceManifest
import com.nuvio.tv.domain.model.RemoteCatalogEntry
import com.nuvio.tv.domain.model.RemoteCatalogManifest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteCatalogRepository"

data class PluginSourceFetchResult(
    val urls: List<String>,
    val rawBody: String,
    val entries: List<RemoteCatalogEntry>
)

@Singleton
class RemoteCatalogRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val manifestAdapter = moshi.adapter(RemoteCatalogManifest::class.java)
    private val sourceManifestAdapter = moshi.adapter(PluginSourceManifest::class.java)

    suspend fun fetchPluginSourceUrls(): Result<PluginSourceFetchResult> =
        withContext(Dispatchers.IO) {
            fetchText(MayaStreamConfig.pluginSourcesUrl).mapCatching { body ->
                parsePluginSources(body)
            }.recoverCatching { primaryError ->
                Log.w(TAG, "plugin-sources.json unavailable, falling back to plugins.json: ${primaryError.message}")
                val fallbackBody = fetchText(MayaStreamConfig.pluginsCatalogUrl).getOrThrow()
                parsePluginSources(fallbackBody)
            }
        }

    suspend fun fetchPluginCatalog(): Result<List<RemoteCatalogEntry>> =
        fetchPluginSourceUrls().map { it.entries }

    suspend fun fetchAddonCatalog(): Result<List<RemoteCatalogEntry>> =
        fetchCatalog(MayaStreamConfig.addonsCatalogUrl)

    private fun parsePluginSources(body: String): PluginSourceFetchResult {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            return PluginSourceFetchResult(emptyList(), body, emptyList())
        }

        val sourceManifest = sourceManifestAdapter.fromJson(trimmed)
        if (sourceManifest != null && sourceManifest.repositories.isNotEmpty()) {
            val urls = sourceManifest.repositories.map { GitHubRawUrlResolver.toRawUrl(it) }
            return PluginSourceFetchResult(
                urls = urls,
                rawBody = trimmed,
                entries = urls.map { url -> url.toCatalogEntry() }
            )
        }

        val manifest = manifestAdapter.fromJson(trimmed)
            ?: throw IllegalArgumentException("Invalid plugin source catalog JSON")

        val entries = manifest.entries.map { entry ->
            entry.copy(url = GitHubRawUrlResolver.toRawUrl(entry.url))
        }
        return PluginSourceFetchResult(
            urls = entries.map { it.url },
            rawBody = trimmed,
            entries = entries
        )
    }

    private fun String.toCatalogEntry(): RemoteCatalogEntry {
        val slug = substringAfterLast('/').removeSuffix(".json").ifBlank { "repository" }
        val host = runCatching {
            java.net.URI(this).host.orEmpty()
                .removePrefix("raw.")
                .substringBefore('.')
        }.getOrDefault("github")
        return RemoteCatalogEntry(
            id = sha256(this).take(12),
            name = "$host/$slug",
            description = this,
            url = this
        )
    }

    private fun sha256(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            ((byte.toInt() shr 4) and 0xF).toString(16) + (byte.toInt() and 0xF).toString(16)
        }
    }

    private suspend fun fetchText(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawUrl = GitHubRawUrlResolver.toRawUrl(url)
            val request = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "MayaStream/1.0")
                .header("Accept", "application/json, text/plain")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Catalog request failed (${response.code})")
                    )
                }
                Result.success(response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch catalog from $url: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchCatalog(url: String): Result<List<RemoteCatalogEntry>> =
        fetchText(url).mapCatching { body ->
            if (body.isBlank()) return@mapCatching emptyList()
            val manifest = manifestAdapter.fromJson(body)
                ?: throw IllegalArgumentException("Invalid catalog JSON")
            manifest.entries.map { entry ->
                entry.copy(url = GitHubRawUrlResolver.toRawUrl(entry.url))
            }
        }
}
