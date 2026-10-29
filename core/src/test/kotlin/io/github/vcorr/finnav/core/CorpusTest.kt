package io.github.vcorr.finnav.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorpusTest {
    @Test
    fun `corpus matches PLAN section 9`() {
        assertEquals(22, Corpus.entries.size)
        assertEquals(10, Corpus.entries.count { it.category == CorpusCategory.PLACE })
        assertEquals(4, Corpus.entries.count { it.category == CorpusCategory.ALIAS })
    }

    @Test
    fun `trial counter cycles through the corpus`() {
        assertEquals(Corpus.entries[0], Corpus.entryFor(0))
        assertEquals(Corpus.entries[0], Corpus.entryFor(22))
        assertEquals(Corpus.entries[21], Corpus.entryFor(-1))
    }

    @Test
    fun `biasing strings drop inflected phrases and city suffixes`() {
        val bias = Corpus.biasingStrings()
        assertTrue("Hämeenkatu 10" in bias)
        assertTrue(bias.none { it.startsWith("aja ") })
    }
}
