package com.nuvio.tv.core.sync

import com.nuvio.tv.data.local.AppOnboardingDataStore
import com.nuvio.tv.data.local.ExperienceModeDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.domain.model.HomeLayout
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MayaStreamStartupService @Inject constructor(
    private val appOnboardingDataStore: AppOnboardingDataStore,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val experienceModeDataStore: ExperienceModeDataStore,
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
    }
}
