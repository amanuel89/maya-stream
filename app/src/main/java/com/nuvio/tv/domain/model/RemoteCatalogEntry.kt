package com.nuvio.tv.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteCatalogManifest(
    @Json(name = "version") val version: Int = 1,
    @Json(name = "name") val name: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "entries") val entries: List<RemoteCatalogEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RemoteCatalogEntry(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "url") val url: String,
    @Json(name = "tags") val tags: List<String> = emptyList()
)
