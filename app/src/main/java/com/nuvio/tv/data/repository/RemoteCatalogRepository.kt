package com.nuvio.tv.data.repository

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.config.MayaStreamConfig
import com.nuvio.tv.core.network.GitHubRawUrlResolver
import com.nuvio.tv.domain.model.PluginSourceManifest
import com.nuvio.tv.domain.model.RemoteCatalogEntry
import com.nuvio.tv.domain.model.RemoteCatalogManifest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
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
  private val okHttpClient: OkHttpClient,
  @ApplicationContext private val context: Context
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val manifestAdapter = moshi.adapter(RemoteCatalogManifest::class.java)
    private val sourceManifestAdapter = moshi.adapter(PluginSourceManifest::class.java)

    @Volatile
    private var lastAddonCatalogBody: String? = null

    fun lastFetchedAddonCatalogBody(): String? = lastAddonCatalogBody

    suspend fun fetchPluginSourceUrls(): Result<PluginSourceFetchResult> =
        withContext(Dispatchers.IO) {
            fetchText(
                MayaStreamConfig.pluginSourcesUrl,
                assetFile = "maya_stream/catalog/plugin-sources.json"
            )
                .mapCatching { body -> parsePluginSources(body) }
                .recoverCatching { primaryError ->
                    Log.w(TAG, "plugin-sources.json unavailable, falling back to plugins.json: ${primaryError.message}")
                    val fallbackBody = fetchText(
                        MayaStreamConfig.pluginsCatalogUrl,
                        assetFile = "maya_stream/catalog/plugins.json"
                    ).getOrThrow()
                    parsePluginSources(fallbackBody)
                }
        }

    suspend fun fetchPluginCatalog(): Result<List<RemoteCatalogEntry>> =
        fetchPluginSourceUrls().map { it.entries }

    suspend fun fetchAddonCatalog(): Result<List<RemoteCatalogEntry>> =
        fetchCatalog(MayaStreamConfig.addonsCatalogUrl, assetFile = "maya_stream/catalog/addons.json")

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

    private suspend fun fetchText(url: String, assetFile: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            val networkResult = fetchTextFromNetwork(url)
            if (networkResult.isSuccess) {
                return@withContext networkResult
            }

            val assetPath = assetFile ?: assetPathForUrl(url)
            if (assetPath != null) {
                readAsset(assetPath)?.let { body ->
                    Log.w(TAG, "Using bundled catalog fallback for $assetPath (remote failed)")
                    return@withContext Result.success(body)
                }
            }

            networkResult
        }

    private fun fetchTextFromNetwork(url: String): Result<String> {
        return try {
            val rawUrl = GitHubRawUrlResolver.toRawUrl(url)
            val request = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "MayaStream/1.0")
                .header("Accept", "application/json, text/plain")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(Exception("Catalog request failed (${response.code})"))
                } else {
                    Result.success(response.body?.string().orEmpty())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch catalog from $url: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun readAsset(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Bundled catalog not found at $path: ${e.message}")
            null
        }
    }

    private fun assetPathForUrl(url: String): String? = when {
        url.contains("plugin-sources.json") -> "maya_stream/catalog/plugin-sources.json"
        url.contains("plugins.json") -> "maya_stream/catalog/plugins.json"
        url.contains("addons.json") -> "maya_stream/catalog/addons.json"
        else -> null
    }

    private suspend fun fetchCatalog(url: String, assetFile: String): Result<List<RemoteCatalogEntry>> =
        fetchText(url, assetFile = assetFile).mapCatching { body ->
            lastAddonCatalogBody = body
            if (body.isBlank()) return@mapCatching emptyList()
            val manifest = manifestAdapter.fromJson(body)
                ?: throw IllegalArgumentException("Invalid catalog JSON")
            manifest.entries.map { entry ->
                entry.copy(url = GitHubRawUrlResolver.toRawUrl(entry.url))
            }
        }
}
