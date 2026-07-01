package com.nuvio.tv.core.locale

object AppLocales {
    const val PREFS_NAME = "app_locale"
    const val KEY_TAG = "locale_tag"
    const val KEY_CHOSEN = "language_chosen"

    val supported: List<Pair<String, Int>> = listOf(
        "en" to com.nuvio.tv.R.string.language_english,
        "am" to com.nuvio.tv.R.string.language_amharic,
        "om" to com.nuvio.tv.R.string.language_oromo
    )

    fun normalizeTag(tag: String?): String = tag?.trim().orEmpty()

    fun isSupported(tag: String?): Boolean {
        val normalized = normalizeTag(tag)
        return normalized.isEmpty() || supported.any { it.first == normalized }
    }
}
