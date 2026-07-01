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
    private val addonCatalogHashKey = stringPreferencesKey("addon_catalog_hash")
    private val lastSyncedAddonCatalogKey = stringPreferencesKey("last_synced_addon_catalog")

    suspend fun getCatalogHash(): String? = store.data.first()[catalogHashKey]

    suspend fun getLastSyncedCatalog(): String? = store.data.first()[lastSyncedCatalogKey]

    suspend fun getAddonCatalogHash(): String? = store.data.first()[addonCatalogHashKey]

    suspend fun saveSyncState(catalogHash: String, catalogBody: String) {
        store.edit { prefs ->
            prefs[catalogHashKey] = catalogHash
            prefs[lastSyncedCatalogKey] = catalogBody
        }
    }

    suspend fun saveAddonSyncState(catalogHash: String, catalogBody: String) {
        store.edit { prefs ->
            prefs[addonCatalogHashKey] = catalogHash
            prefs[lastSyncedAddonCatalogKey] = catalogBody
        }
    }
}
