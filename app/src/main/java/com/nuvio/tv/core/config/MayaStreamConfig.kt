package com.nuvio.tv.core.config

import com.nuvio.tv.BuildConfig

object MayaStreamConfig {
    const val APP_DISPLAY_NAME = "ማያ Stream"

    private const val DEFAULT_CATALOG_BASE =
        "https://raw.githubusercontent.com/amanuel89/maya-stream/main/catalog"

    val catalogBaseUrl: String
        get() = BuildConfig.MAYA_STREAM_CATALOG_BASE_URL.trim().ifBlank { DEFAULT_CATALOG_BASE }

    val pluginSourcesUrl: String
        get() = "$catalogBaseUrl/plugin-sources.json"

    val pluginsCatalogUrl: String
        get() = "$catalogBaseUrl/plugins.json"

    val addonsCatalogUrl: String
        get() = "$catalogBaseUrl/addons.json"
}
