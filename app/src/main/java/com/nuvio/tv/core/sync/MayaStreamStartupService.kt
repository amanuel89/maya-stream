package com.nuvio.tv.core.sync

import android.util.Log
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.data.local.AppOnboardingDataStore
import com.nuvio.tv.data.local.ExperienceModeDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.domain.model.HomeLayout
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MayaStreamStartupService"

@Singleton
class MayaStreamStartupService @Inject constructor(
    private val appOnboardingDataStore: AppOnboardingDataStore,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val experienceModeDataStore: ExperienceModeDataStore,
    private val addonCatalogSyncService: AddonCatalogSyncService,
    private val pluginCatalogSyncService: PluginCatalogSyncService,
) {
    suspend fun prepareHomeScreen() {
        appOnboardingDataStore.setHasSeenAuthQrOnFirstLaunch(true)
        if (!layoutPreferenceDataStore.hasChosenLayout.first()) {
            layoutPreferenceDataStore.setLayout(HomeLayout.MODERN)
        }
        if (experienceModeDataStore.mode.first() == null) {
            experienceModeDataStore.setMode(ExperienceMode.ADVANCED)
        }
        if (!experienceModeDataStore.addonSetupSkipped.first()) {
            experienceModeDataStore.setAddonSetupSkipped(true)
        }

        val addonResult = addonCatalogSyncService.syncFromGitHub(force = false)
        Log.d(
            TAG,
            "Addon catalog sync: added=${addonResult.addedCount} skipped=${addonResult.skippedCount} failed=${addonResult.failedCount}"
        )

        if (AppFeaturePolicy.pluginsEnabled) {
            val pluginResult = pluginCatalogSyncService.syncFromGitHub(force = false)
            Log.d(
                TAG,
                "Plugin catalog sync: added=${pluginResult.addedCount} skipped=${pluginResult.skippedCount} failed=${pluginResult.failedCount}"
            )
            if (pluginResult.errorMessage != null) {
                Log.w(TAG, "Plugin catalog sync error: ${pluginResult.errorMessage}")
            }
        }
    }
}
