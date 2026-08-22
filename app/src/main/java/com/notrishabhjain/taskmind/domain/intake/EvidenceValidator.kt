package com.notrishabhjain.taskmind.domain.intake

sealed interface EvidenceCheck {
    data object Valid : EvidenceCheck
    data object EmptyEvidence : EvidenceCheck
    data object MissingSource : EvidenceCheck
    data class NotInSource(val normalizedEvidence: String) : EvidenceCheck
}

object EvidenceValidator {

    private val WHITESPACE = Regex("\\s+")

    fun validate(evidence: String?, sourceText: String?): EvidenceCheck {
        val needle = evidence?.let(::canonical)?.orEmpty()
        if (needle.isEmpty()) return EvidenceCheck.EmptyEvidence
        val haystack = sourceText?.let(::canonical)?.orEmpty()
        if (haystack.isEmpty()) return EvidenceCheck.MissingSource
        return if (haystack.contains(needle)) EvidenceCheck.Valid else EvidenceCheck.NotInSource(needle)
    }

    private fun canonical(text: String): String = text.replace(WHITESPACE, " ").trim().lowercase()
}
