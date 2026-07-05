package com.nuvio.tv.ui.screens.player

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.nuvio.tv.core.player.FailoverTrigger
import com.nuvio.tv.core.player.StreamHeuristicEngine
import com.nuvio.tv.core.player.StreamPlaybackFailover
import com.nuvio.tv.core.player.StreamPlaybackFailoverPolicy
import com.nuvio.tv.core.player.StreamRankingContext
import com.nuvio.tv.core.player.StreamSelectionPolicy
import com.nuvio.tv.core.player.playbackMergeKey
import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SOURCE_FAILOVER"

@UnstableApi
internal fun PlayerRuntimeController.resetPlaybackFailoverSession() {
    triedStreamKeys.clear()
    playbackFailoverCount = 0
    lastPlaybackFailoverAtMs = 0L
    stablePlaybackSinceMs = 0L
    rebufferTimestampsMs.clear()
    userPinnedSource = false
    clearFailoverUndo()
    cancelFailoverCountdown()
    resetQualityUpgradeSession()
    _uiState.update {
        it.copy(
            failoverCancelVisible = false,
            failoverCancelMessage = "",
            failoverCancelCountdownSec = 0
        )
    }
}

@UnstableApi
internal fun PlayerRuntimeController.markStablePlaybackAnchor(nowMs: Long = System.currentTimeMillis()) {
    stablePlaybackSinceMs = nowMs
}

@UnstableApi
internal fun PlayerRuntimeController.onUserPinnedSourceStream() {
    userPinnedSource = true
}

@UnstableApi
internal fun PlayerRuntimeController.recordRebufferForFailover(nowMs: Long = System.currentTimeMillis()) {
    rebufferTimestampsMs.add(nowMs)
    val windowStart = nowMs - StreamPlaybackFailoverPolicy.REBUFFER_WINDOW_MS
    rebufferTimestampsMs.removeAll { it < windowStart }
    if (playbackFailoverOnRebufferEnabled) {
        evaluateRebufferFailover(nowMs)
    }
}

@UnstableApi
internal fun PlayerRuntimeController.evaluateSustainedRebufferFailover(
    nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime()
) {
    if (!playbackFailoverOnRebufferEnabled) return
    if (rebufferStartedAtMs <= 0L) return
    val sustainedMs = nowElapsedMs - rebufferStartedAtMs
    if (sustainedMs >= StreamPlaybackFailoverPolicy.SUSTAINED_REBUFFER_MS) {
        maybeSchedulePlaybackSourceFailover(FailoverTrigger.REBUFFER)
    }
}

@UnstableApi
private fun PlayerRuntimeController.evaluateRebufferFailover(nowMs: Long) {
    if (StreamPlaybackFailover.shouldTriggerRebufferFailover(
            rebufferTimestampsMs = rebufferTimestampsMs,
            sustainedRebufferStartedAtMs = rebufferStartedAtMs,
            nowMs = nowMs
        )
    ) {
        maybeSchedulePlaybackSourceFailover(FailoverTrigger.REBUFFER)
    }
}

@UnstableApi
internal fun PlayerRuntimeController.maybeSchedulePlaybackSourceFailover(
    trigger: FailoverTrigger,
    skipCancelWindow: Boolean = false
): Boolean {
    if (isUsingMpvEngine()) return false
    val nowMs = System.currentTimeMillis()
    val playheadMs = _exoPlayer?.currentPosition ?: 0L
    if (!StreamPlaybackFailover.canAttemptFailover(
            trigger = trigger,
            onErrorEnabled = playbackFailoverOnErrorEnabled,
            onRebufferEnabled = playbackFailoverOnRebufferEnabled,
            failoverCount = playbackFailoverCount,
            lastFailoverAtMs = lastPlaybackFailoverAtMs,
            stablePlaybackSinceMs = stablePlaybackSinceMs,
            nowMs = nowMs,
            playheadMs = playheadMs,
            pendingCancelActive = failoverCancelJob?.isActive == true,
            userPinnedSource = userPinnedSource
        )
    ) {
        return false
    }

    val currentStream = buildCurrentStreamSnapshot() ?: return false
    triedStreamKeys.add(currentStream.playbackMergeKey())

    val context = failoverRankingContext ?: StreamRankingContext(
        policy = StreamSelectionPolicy.FAST_START,
        installedAddonNames = failoverInstalledAddonNames,
        allowTorrents = true
    )
    val candidate = StreamPlaybackFailover.selectNextCandidate(
        allStreams = _uiState.value.sourceAllStreams,
        currentStream = currentStream,
        triedKeys = triedStreamKeys,
        context = context,
        currentQualityValue = (currentVideoHeight ?: 0).takeIf { it > 0 } ?: currentStream.qualityValue,
        currentBingeGroup = currentStreamBingeGroup
    ) ?: run {
        Log.w(TAG, "No failover candidate trigger=$trigger tried=${triedStreamKeys.size}")
        return false
    }

    scope.launch {
        val hardStopped = _exoPlayer?.playbackState == Player.STATE_IDLE
        val useCancelWindow = !skipCancelWindow && !(trigger == FailoverTrigger.ERROR && hardStopped)
        if (useCancelWindow) {
            scheduleFailoverWithCancelWindow(
                target = candidate.stream,
                trigger = trigger,
                resumePositionMs = playheadMs.coerceAtLeast(0L),
                previousStream = currentStream
            )
        } else {
            executePlaybackSourceFailover(
                target = candidate.stream,
                trigger = trigger,
                resumePositionMs = playheadMs.coerceAtLeast(0L),
                previousStream = currentStream,
                isUndo = false
            )
        }
    }
    return true
}

@UnstableApi
private fun PlayerRuntimeController.scheduleFailoverWithCancelWindow(
    target: Stream,
    trigger: FailoverTrigger,
    resumePositionMs: Long,
    previousStream: Stream
) {
    cancelFailoverCountdown()
    val label = target.name?.takeIf { it.isNotBlank() } ?: target.addonName
    var remainingSec = (StreamPlaybackFailoverPolicy.PRE_SWITCH_CANCEL_MS / 1000L).toInt()
    _uiState.update {
        it.copy(
            failoverCancelVisible = true,
            failoverCancelMessage = label,
            failoverCancelCountdownSec = remainingSec
        )
    }
    failoverCancelJob = scope.launch {
        while (remainingSec > 0) {
            delay(1_000L)
            remainingSec -= 1
            _uiState.update { it.copy(failoverCancelCountdownSec = remainingSec.coerceAtLeast(0)) }
        }
        _uiState.update {
            it.copy(
                failoverCancelVisible = false,
                failoverCancelMessage = "",
                failoverCancelCountdownSec = 0
            )
        }
        executePlaybackSourceFailover(
            target = target,
            trigger = trigger,
            resumePositionMs = resumePositionMs,
            previousStream = previousStream,
            isUndo = false
        )
    }
}

@UnstableApi
internal fun PlayerRuntimeController.cancelPendingFailoverSwitch() {
    if (failoverCancelJob?.isActive != true) return
    cancelFailoverCountdown()
    Log.i(TAG, "Failover cancelled by user")
    _uiState.update {
        it.copy(
            failoverCancelVisible = false,
            failoverCancelMessage = "",
            failoverCancelCountdownSec = 0
        )
    }
}

@UnstableApi
private fun PlayerRuntimeController.cancelFailoverCountdown() {
    failoverCancelJob?.cancel()
    failoverCancelJob = null
}

@UnstableApi
private fun PlayerRuntimeController.executePlaybackSourceFailover(
    target: Stream,
    trigger: FailoverTrigger,
    resumePositionMs: Long,
    previousStream: Stream,
    isUndo: Boolean
) {
    if (!isUndo) {
        undoPreviousStream = previousStream
        undoPreviousPositionMs = resumePositionMs
        undoExpiresAtMs = System.currentTimeMillis() + StreamPlaybackFailoverPolicy.UNDO_WINDOW_MS
        playbackFailoverCount += 1
        lastPlaybackFailoverAtMs = System.currentTimeMillis()
    }
    markStablePlaybackAnchor()
    Log.i(
        TAG,
        "Switching trigger=$trigger undo=$isUndo from=${previousStream.playbackMergeKey()} " +
            "to=${target.playbackMergeKey()} pos=$resumePositionMs count=$playbackFailoverCount"
    )
    switchToSourceStream(
        stream = target,
        resumePositionMs = resumePositionMs,
        autoFailover = !isUndo,
        isUndo = isUndo
    )
    if (!isUndo) {
        _uiState.update { it.copy(showFailoverUndo = true) }
        scheduleFailoverUndoExpiry()
    }
}

@UnstableApi
internal fun PlayerRuntimeController.undoLastPlaybackFailover() {
    val previous = undoPreviousStream ?: return
    if (System.currentTimeMillis() > undoExpiresAtMs) {
        clearFailoverUndo()
        return
    }
    val position = undoPreviousPositionMs
    clearFailoverUndo()
    executePlaybackSourceFailover(
        target = previous,
        trigger = FailoverTrigger.ERROR,
        resumePositionMs = position,
        previousStream = buildCurrentStreamSnapshot() ?: previous,
        isUndo = true
    )
}

@UnstableApi
private fun PlayerRuntimeController.clearFailoverUndo() {
    undoPreviousStream = null
    undoPreviousPositionMs = 0L
    undoExpiresAtMs = 0L
    failoverUndoExpiryJob?.cancel()
    _uiState.update { it.copy(showFailoverUndo = false) }
}

@UnstableApi
private fun PlayerRuntimeController.scheduleFailoverUndoExpiry() {
    failoverUndoExpiryJob?.cancel()
    failoverUndoExpiryJob = scope.launch {
        delay(StreamPlaybackFailoverPolicy.UNDO_WINDOW_MS)
        clearFailoverUndo()
    }
}

@UnstableApi
private fun PlayerRuntimeController.buildCurrentStreamSnapshot(): Stream? {
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
