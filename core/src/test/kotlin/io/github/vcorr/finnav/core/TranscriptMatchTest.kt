package io.github.vcorr.finnav.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriptMatchTest {
    @Test
    fun `ignores case, punctuation and spacing`() {
        assertTrue(TranscriptMatch.isMatch("Hämeenkatu 10, Tampere", "hämeenkatu 10 tampere"))
        assertTrue(TranscriptMatch.isMatch("Ylöjärvi", "YLÖJÄRVI."))
        assertTrue(TranscriptMatch.isMatch("Tampere-Pirkkala", "Tampere Pirkkala"))
    }

    @Test
    fun `different words do not match`() {
        assertFalse(TranscriptMatch.isMatch("Tampere", "Tampa"))
        assertFalse(TranscriptMatch.isMatch("aja Tampereelle", "Tampereelle"))
        assertFalse(TranscriptMatch.isMatch("mökki", "mokki"))
    }

    @Test
    fun `score reports top1 and rank`() {
        val top = TranscriptMatch.score("Nokia", listOf("Nokia", "nokia oyj"))
        assertTrue(top.top1)
        assertEquals(1, top.rank)

        val second = TranscriptMatch.score("Orivesi", listOf("Ori vesi", "Orivesi"))
        assertFalse(second.top1)
        assertEquals(2, second.rank)
        assertTrue(second.anyOfN)

        val none = TranscriptMatch.score("Lempäälä", emptyList())
        assertFalse(none.top1)
        assertNull(none.rank)
        assertFalse(none.anyOfN)
    }
}
