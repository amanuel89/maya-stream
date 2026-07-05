package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream

fun Stream.playbackMergeKey(): String =
    infoHash?.lowercase()?.let { hash -> "$addonName|$hash:${fileIdx ?: ""}" }
        ?: "$addonName|${getStreamUrl() ?: externalUrl ?: ytId ?: "${name}:${title}"}"
