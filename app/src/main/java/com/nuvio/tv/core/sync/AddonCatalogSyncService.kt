package com.nuvio.tv.core.sync

import android.util.Log
import com.nuvio.tv.data.local.MayaStreamCatalogPreferences
import com.nuvio.tv.data.repository.RemoteCatalogRepository
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.repository.AddonRepository
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

private const val TAG = "AddonCatalogSyncService"

data class AddonCatalogSyncResult(
    val addedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val catalogChanged: Boolean,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = errorMessage == null
}

@Singleton
class AddonCatalogSyncService @Inject constructor(
    private val remoteCatalogRepository: RemoteCatalogRepository,
    private val addonRepository: AddonRepository,
    private val catalogPreferences: MayaStreamCatalogPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    fun scheduleStartupSync() {
        scope.launch {
            syncFromGitHub(force = false)
        }
    }

    suspend fun syncFromGitHub(force: Boolean): AddonCatalogSyncResult = syncMutex.withLock {
        val catalogResult = remoteCatalogRepository.fetchAddonCatalog()
        if (catalogResult.isFailure) {
            val message = catalogResult.exceptionOrNull()?.message ?: "Failed to fetch addon catalog"
            Log.e(TAG, message)
            return AddonCatalogSyncResult(
                addedCount = 0,
                skippedCount = 0,
                failedCount = 0,
                catalogChanged = false,
                errorMessage = message
            )
        }

        val entries = catalogResult.getOrThrow()
        val catalogBody = remoteCatalogRepository.lastFetchedAddonCatalogBody().orEmpty()
        val catalogHash = sha256(catalogBody.ifBlank { entries.joinToString("|") { it.url } })
        val previousHash = catalogPreferences.getAddonCatalogHash()
        val catalogChanged = previousHash != null && previousHash != catalogHash
        val installedAddons = addonRepository.getInstalledAddons().first()
        val hasInstalledAddons = installedAddons.isNotEmpty()

        if (!force && hasInstalledAddons && previousHash == catalogHash) {
            Log.d(TAG, "Addon catalog unchanged, skipping sync")
            return AddonCatalogSyncResult(
                addedCount = 0,
                skippedCount = entries.size,
                failedCount = 0,
                catalogChanged = false
            )
        }

        if (!force && !hasInstalledAddons && entries.isEmpty()) {
            return AddonCatalogSyncResult(
                addedCount = 0,
                skippedCount = 0,
                failedCount = 0,
                catalogChanged = false,
                errorMessage = "Addon catalog is empty"
            )
        }

        val installedUrls = installedAddons
            .map { normalizeUrl(it.baseUrl) }
            .toMutableSet()

        var added = 0
        var skipped = 0
        var failed = 0

        for (entry in entries) {
            val url = entry.url.trim()
            if (url.isBlank()) continue
            val normalized = normalizeUrl(url)
            if (normalized in installedUrls) {
                skipped++
                continue
            }
            when (val result = addonRepository.fetchAddon(url)) {
                is NetworkResult.Success -> {
                    addonRepository.addAddon(url)
                    added++
                    installedUrls.add(normalized)
                    Log.d(TAG, "Installed addon from catalog: ${entry.name}")
                }
                is NetworkResult.Error -> {
                    failed++
                    Log.w(TAG, "Failed to install addon ${entry.name}: ${result.message}")
                }
                NetworkResult.Loading -> Unit
            }
        }

        catalogPreferences.saveAddonSyncState(catalogHash = catalogHash, catalogBody = catalogBody)

        Log.d(
            TAG,
            "Addon catalog sync complete (force=$force, changed=$catalogChanged): added=$added skipped=$skipped failed=$failed"
        )

        return AddonCatalogSyncResult(
            addedCount = added,
            skippedCount = skipped,
            failedCount = failed,
            catalogChanged = catalogChanged || previousHash == null
        )
    }

    private fun normalizeUrl(url: String): String =
        url.trim().trimEnd('/').lowercase()

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            ((byte.toInt() shr 4) and 0xF).toString(16) + (byte.toInt() and 0xF).toString(16)
        }
    }
}
