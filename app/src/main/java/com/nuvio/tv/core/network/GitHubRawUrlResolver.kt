package com.nuvio.tv.core.network

object GitHubRawUrlResolver {

    private val RAW_HOST = Regex("raw\\.githubusercontent\\.com", RegexOption.IGNORE_CASE)
    private val BLOB_URL = Regex(
        pattern = """https?://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val TREE_URL = Regex(
        pattern = """https?://github\.com/([^/]+)/([^/]+)/tree/([^/]+)/?(.*)""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun toRawUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return trimmed

        val withoutFragment = trimmed.substringBefore('#')
        if (RAW_HOST.containsMatchIn(withoutFragment)) {
            return withoutFragment
        }

        BLOB_URL.matchEntire(withoutFragment)?.let { match ->
            val (owner, repo, branch, filePath) = match.destructured
            return "https://raw.githubusercontent.com/$owner/$repo/$branch/$filePath"
        }

        TREE_URL.matchEntire(withoutFragment)?.let { match ->
            val (owner, repo, branch, folderPath) = match.destructured
            val resolvedPath = if (folderPath.endsWith(".json", ignoreCase = true)) {
                folderPath
            } else {
                listOf(folderPath.trim('/'), "manifest.json")
                    .filter { it.isNotBlank() }
                    .joinToString("/")
            }
            return "https://raw.githubusercontent.com/$owner/$repo/$branch/$resolvedPath"
        }

        return withoutFragment
    }
}
