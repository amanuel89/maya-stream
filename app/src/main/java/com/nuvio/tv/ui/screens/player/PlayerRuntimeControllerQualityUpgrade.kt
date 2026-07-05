package com.nuvio.tv.ui.screens.player

import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.nuvio.tv.core.player.StreamQualityUpgrade
import com.nuvio.tv.core.player.StreamQualityUpgradePolicy
import com.nuvio.tv.core.player.StreamRankingContext
import com.nuvio.tv.core.player.StreamSelectionPolicy
import com.nuvio.tv.core.player.playbackMergeKey
import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "QUALITY_UPGRADE"

@UnstableApi
internal fun PlayerRuntimeController.resetQualityUpgradeSession() {
    qualityUpgradeCount = 0
    lastQualityUpgradeAtMs = 0L
    triedQualityUpgradeKeys.clear()
    qualityUpgradeMonitorJob?.cancel()
    qualityUpgradeMonitorJob = null
}

@UnstableApi
internal fun PlayerRuntimeController.prefetchSourceStreamsForFailoverIfNeeded() {
    if (!playbackFailoverOnErrorEnabled &&
        !playbackFailoverOnRebufferEnabled &&
        !playbackQualityUpgradeEnabled
    ) {
        return
    }
    if (contentId.isNullOrBlank() && currentVideoId.isNullOrBlank()) return
    loadSourceStreams(forceRefresh = false)
}

@UnstableApi
internal fun PlayerRuntimeController.scheduleQualityUpgradeMonitor() {
    if (!playbackQualityUpgradeEnabled || isUsingMpvEngine()) return
    qualityUpgradeMonitorJob?.cancel()
    qualityUpgradeMonitorJob = scope.launch {
        delay(StreamQualityUpgradePolicy.INITIAL_DELAY_MS)
        while (isActive) {
            evaluateQualityUpgradeIfEligible()
            delay(StreamQualityUpgradePolicy.PERIODIC_CHECK_MS)
        }
    }
}

@UnstableApi
internal fun PlayerRuntimeController.evaluateQualityUpgradeIfEligible() {
    if (isUsingMpvEngine()) return
    val state = _uiState.value
    val nowMs = System.currentTimeMillis()
    val playheadMs = _exoPlayer?.currentPosition ?: 0L
    if (!StreamQualityUpgrade.canAttemptUpgrade(
            enabled = playbackQualityUpgradeEnabled,
            upgradeCount = qualityUpgradeCount,
            lastUpgradeAtMs = lastQualityUpgradeAtMs,
            stablePlaybackSinceMs = stablePlaybackSinceMs,
            nowMs = nowMs,
            playheadMs = playheadMs,
            isBuffering = state.isBuffering,
            userPinnedSource = userPinnedSource,
            pendingFailoverCancel = failoverCancelJob?.isActive == true,
            hasRenderedFirstFrame = hasRenderedFirstFrame
        )
    ) {
        return
    }

    val currentStream = buildCurrentStreamSnapshotForUpgrade() ?: return
    val allStreams = state.sourceAllStreams
    if (allStreams.isEmpty()) return

    val currentResolution = StreamQualityUpgrade.resolvePlaybackResolution(
        videoHeight = currentVideoHeight,
        stream = currentStream
    )
    if (currentResolution <= 0) return

    val context = failoverRankingContext ?: StreamRankingContext(
        policy = StreamSelectionPolicy.QUALITY_FIRST,
        installedAddonNames = failoverInstalledAddonNames,
        allowTorrents = qualityUpgradeAllowTorrents
    )
    val candidate = StreamQualityUpgrade.selectUpgradeCandidate(
        allStreams = allStreams,
        currentStream = currentStream,
        currentResolution = currentResolution,
        currentBingeGroup = currentStreamBingeGroup,
        triedKeys = triedQualityUpgradeKeys,
        context = context
    ) ?: return

    executeSilentQualityUpgrade(
        target = candidate,
        previousStream = currentStream,
        resumePositionMs = playheadMs.coerceAtLeast(0L)
    )
}

@UnstableApi
private fun PlayerRuntimeController.executeSilentQualityUpgrade(
    target: Stream,
    previousStream: Stream,
    resumePositionMs: Long
) {
    triedQualityUpgradeKeys.add(previousStream.playbackMergeKey())
    qualityUpgradeCount += 1
    lastQualityUpgradeAtMs = System.currentTimeMillis()
    markStablePlaybackAnchor()
    Log.i(
        TAG,
        "Upgrading from=${previousStream.playbackMergeKey()} to=${target.playbackMergeKey()} " +
            "pos=$resumePositionMs count=$qualityUpgradeCount"
    )
    switchToSourceStream(
        stream = target,
        resumePositionMs = resumePositionMs,
        autoFailover = true,
        isUndo = false,
        silentQualityUpgrade = true
    )
}

@UnstableApi
private fun PlayerRuntimeController.buildCurrentStreamSnapshotForUpgrade(): Stream? {
    val addon = currentAddonName ?: _uiState.value.currentStreamAddonName
    if (addon.isNullOrBlank()) return null
    return Stream(
        name = _uiState.value.currentStreamName,
        title = null,
        description = null,
        url = currentStreamUrl.takeIf { it.isNotBlank() },
        ytId = null,
        infoHash = currentInfoHash,
        fileIdx = currentFileIdx,
        externalUrl = null,
        behaviorHints = null,
        addonName = addon,
        addonLogo = currentAddonLogo,
        qualityValue = (currentVideoHeight ?: 0).takeIf { it > 0 } ?: -1
    )
}
