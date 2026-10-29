package io.github.vcorr.finnav.core

import java.util.Locale

/** How a recogniser's n-best list compares with the utterance that was actually spoken. */
data class MatchResult(
    /** The top result matches. This is the accuracy figure the Phase 0 gate uses. */
    val top1: Boolean,
    /** 1-based position of the first matching alternative, or null if none matched. */
    val rank: Int?,
) {
    val anyOfN: Boolean get() = rank != null
}

/** Strict comparison of a transcript with ground truth, ignoring case, punctuation and spacing. */
object TranscriptMatch {
    private val PUNCTUATION_OR_SYMBOL = Regex("[\\p{P}\\p{S}]")
    private val WHITESPACE = Regex("\\s+")

    fun normalise(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(PUNCTUATION_OR_SYMBOL, " ")
        .replace(WHITESPACE, " ")
        .trim()

    fun isMatch(expected: String, heard: String): Boolean = normalise(expected) == normalise(heard)

    fun score(expected: String, nBest: List<String>): MatchResult {
        val index = nBest.indexOfFirst { isMatch(expected, it) }
        return MatchResult(top1 = index == 0, rank = if (index >= 0) index + 1 else null)
    }
}
