package com.notrishabhjain.taskmind.domain.intake

object TitleNormalizer {

    private val WHITESPACE = Regex("\\s+")

    private val POLITENESS_PREFIXES = listOf("please", "kindly", "pls", "plz", "zara", "thoda")

    private val EDGE_CHARS = " \t\n\r.,;:!?\"'`()[]{}<>\u2013\u2014\u2026\u0964\u0965-".toSet()

    fun normalize(raw: String): String = raw.replace(WHITESPACE, " ").trim()

    fun titleKey(raw: String): String {
        val collapsed = normalize(raw).lowercase()
        if (collapsed.isBlank()) return ""
        var key = stripPolitenessPrefixes(collapsed)
        key = collapseSpaces(key.trim(EDGE_CHARS::contains))
        if (key.isBlank()) {
            key = collapsed.trim(EDGE_CHARS::contains)
        }
        return key
    }

    private fun stripPolitenessPrefixes(input: String): String {
        var current = input.trim(EDGE_CHARS::contains)
        while (true) {
            val stripped = POLITENESS_PREFIXES.firstOrNull { prefix ->
                current.startsWith(prefix) &&
                    current.length > prefix.length &&
                    !current[prefix.length].isLetterOrDigit()
            } ?: return current
            current = current.removeRange(0, stripped.length).trim(EDGE_CHARS::contains)
        }
    }

    private fun collapseSpaces(input: String): String = input.replace(WHITESPACE, " ").trim()
}
