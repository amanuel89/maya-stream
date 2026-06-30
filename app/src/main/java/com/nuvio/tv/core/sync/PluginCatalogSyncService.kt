package com.nuvio.tv.core.sync

import android.util.Log
import com.nuvio.tv.core.plugin.PluginManager
import com.nuvio.tv.data.local.MayaStreamCatalogPreferences
import com.nuvio.tv.data.repository.RemoteCatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PluginCatalogSyncService"

data class PluginCatalogSyncResult(
    val addedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val catalogChanged: Boolean,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = errorMessage == null
}

@Singleton
class PluginCatalogSyncService @Inject constructor(
    private val remoteCatalogRepository: RemoteCatalogRepository,
    private val pluginManager: PluginManager,
    private val catalogPreferences: MayaStreamCatalogPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    fun scheduleStartupSync() {
        scope.launch {
            syncFromGitHub(force = false)
        }
    }

    suspend fun syncFromGitHub(force: Boolean): PluginCatalogSyncResult = syncMutex.withLock {
        val sourcesResult = remoteCatalogRepository.fetchPluginSourceUrls()
        if (sourcesResult.isFailure) {
            val message = sourcesResult.exceptionOrNull()?.message ?: "Failed to fetch plugin sources"
            Log.e(TAG, message)
            return PluginCatalogSyncResult(
                addedCount = 0,
                skippedCount = 0,
                failedCount = 0,
                catalogChanged = false,
                errorMessage = message
            )
        }

        val payload = sourcesResult.getOrThrow()
        val catalogBody = payload.rawBody
        val urls = payload.urls
        val catalogHash = sha256(catalogBody)
        val previousHash = catalogPreferences.getCatalogHash()
        val catalogChanged = previousHash != null && previousHash != catalogHash
        val installedRepos = pluginManager.repositories.first()
        val hasInstalledRepos = installedRepos.isNotEmpty()

        if (!force && hasInstalledRepos && previousHash == catalogHash) {
            Log.d(TAG, "Plugin catalog unchanged, skipping sync")
            return PluginCatalogSyncResult(
                addedCount = 0,
                skippedCount = urls.size,
                failedCount = 0,
                catalogChanged = false
            )
        }

        if (!force && !hasInstalledRepos && urls.isEmpty()) {
            return PluginCatalogSyncResult(
                addedCount = 0,
                skippedCount = 0,
                failedCount = 0,
                catalogChanged = false,
                errorMessage = "Plugin source catalog is empty"
            )
        }

        val installedUrls = installedRepos
            .map { normalizeRepoUrl(it.url) }
            .toMutableSet()

        var added = 0
        var skipped = 0
        var failed = 0

        for (url in urls) {
            val normalized = normalizeRepoUrl(url)
            if (normalized in installedUrls) {
                skipped++
                continue
            }
            val result = pluginManager.addRepository(url)
            if (result.isSuccess) {
                added++
                installedUrls.add(normalized)
            } else {
                failed++
                Log.w(TAG, "Failed to add repository $url: ${result.exceptionOrNull()?.message}")
            }
        }

        catalogPreferences.saveSyncState(catalogHash = catalogHash, catalogBody = catalogBody)

        Log.d(
            TAG,
            "Plugin catalog sync complete (force=$force, changed=$catalogChanged): added=$added skipped=$skipped failed=$failed"
        )

        return PluginCatalogSyncResult(
            addedCount = added,
            skippedCount = skipped,
            failedCount = failed,
            catalogChanged = catalogChanged || previousHash == null
        )
    }

    private fun normalizeRepoUrl(url: String): String =
        url.trim().trimEnd('/').lowercase()

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            ((byte.toInt() shr 4) and 0xF).toString(16) + (byte.toInt() and 0xF).toString(16)
        }
    }
}
