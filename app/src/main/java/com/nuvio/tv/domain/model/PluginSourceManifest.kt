package com.nuvio.tv.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PluginSourceManifest(
    @Json(name = "version") val version: Int = 1,
    @Json(name = "name") val name: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "repositories") val repositories: List<String> = emptyList()
)
