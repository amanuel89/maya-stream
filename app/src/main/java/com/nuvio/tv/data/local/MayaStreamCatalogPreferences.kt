package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MayaStreamCatalogPreferences @Inject constructor(
    private val factory: ProfileDataStoreFactory
) {
    companion object {
        private const val FEATURE = "maya_stream_catalog"
    }

    private val store = factory.get(profileId = 1, featureName = FEATURE)
    private val catalogHashKey = stringPreferencesKey("plugin_sources_hash")
    private val lastSyncedCatalogKey = stringPreferencesKey("last_synced_plugin_sources")

    suspend fun getCatalogHash(): String? = store.data.first()[catalogHashKey]

    suspend fun getLastSyncedCatalog(): String? = store.data.first()[lastSyncedCatalogKey]

    suspend fun saveSyncState(catalogHash: String, catalogBody: String) {
        store.edit { prefs ->
            prefs[catalogHashKey] = catalogHash
            prefs[lastSyncedCatalogKey] = catalogBody
        }
    }
}
