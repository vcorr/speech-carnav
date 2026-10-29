package io.github.vcorr.finnav.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunLogTest {
    private fun record(condition: String, expected: String?, nBest: List<String>) = RunRecord(
        timestamp = "2026-10-29T18:00:00+02:00",
        condition = condition,
        trigger = "launcher:FinNav",
        keyguardLocked = true,
        recogniser = "google",
        expected = expected,
        nBest = nBest,
        confidences = listOf(0.91f),
        match = expected?.let { TranscriptMatch.score(it, nBest) },
        error = null,
        timings = "ready=300",
        navUri = null,
    )

    @Test
    fun `line has one field per header column`() {
        val line = RunLog.toTsvLine(record("parked", "Nokia", listOf("Nokia")))
        assertEquals(RunLog.HEADER.split('\t').size, line.split('\t').size)
        assertTrue("0.91" in line)
    }

    @Test
    fun `tabs and newlines inside fields are flattened`() {
        assertEquals("a b c", RunLog.escape("a\tb\r\nc"))
        val line = RunLog.toTsvLine(record("parked", "Nokia", listOf("No\tkia\n")))
        assertEquals(RunLog.HEADER.split('\t').size, line.split('\t').size)
    }

    @Test
    fun `tally counts corpus trials per condition and skips free runs`() {
        val tsv = listOf(
            RunLog.HEADER,
            RunLog.toTsvLine(record("parked", "Nokia", listOf("Nokia"))),
            RunLog.toTsvLine(record("parked", "Orivesi", listOf("Ori vesi", "Orivesi"))),
            RunLog.toTsvLine(record("parked", "Pirkkala", emptyList())),
            RunLog.toTsvLine(record("driving", "Tampere", listOf("Tampere"))),
            RunLog.toTsvLine(record("parked", null, listOf("Hervanta"))),
        ).joinToString("\n")

        val tally = RunLog.tally(tsv)
        assertEquals(Tally(trials = 3, top1 = 1, anyOfN = 2), tally["parked"])
        assertEquals(Tally(trials = 1, top1 = 1, anyOfN = 1), tally["driving"])
    }

    @Test
    fun `empty log tallies to nothing`() {
        assertEquals(emptyMap(), RunLog.tally(""))
    }
}
