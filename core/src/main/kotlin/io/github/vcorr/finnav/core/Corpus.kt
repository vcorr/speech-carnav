package io.github.vcorr.finnav.core

enum class CorpusCategory { PLACE, STREET, INFLECTED, ALIAS }

data class CorpusEntry(val category: CorpusCategory, val utterance: String)

/** The PLAN.md §9 test corpus: what the tester says, and therefore what the recogniser should return. */
object Corpus {
    val entries: List<CorpusEntry> = buildList {
        listOf(
            "Tampere", "Pirkkala", "Nokia", "Ylöjärvi", "Lempäälä",
            "Kangasala", "Orivesi", "Hervanta", "Särkänniemi", "Ideapark",
        ).forEach { add(CorpusEntry(CorpusCategory.PLACE, it)) }
        listOf(
            "Hämeenkatu 10, Tampere", "Hervannan valtaväylä", "Pirkkalan lentoasema",
            "Sammonkatu", "Itsenäisyydenkatu",
        ).forEach { add(CorpusEntry(CorpusCategory.STREET, it)) }
        listOf(
            "aja Tampereelle", "navigoi Hervantaan", "vie minut Ideaparkkiin",
        ).forEach { add(CorpusEntry(CorpusCategory.INFLECTED, it)) }
        listOf("koti", "mökki", "työ", "sali").forEach { add(CorpusEntry(CorpusCategory.ALIAS, it)) }
    }

    /** Entry for a running trial counter, cycling through the corpus. */
    fun entryFor(trial: Int): CorpusEntry = entries[Math.floorMod(trial, entries.size)]

    /**
     * Strings to bias the recogniser towards. Biasing with the corpus itself flatters accuracy,
     * so biased runs are logged under a separate condition and never pooled with unbiased ones.
     */
    fun biasingStrings(): List<String> = entries
        .filter { it.category != CorpusCategory.INFLECTED }
        .map { it.utterance.substringBefore(',') }
}
