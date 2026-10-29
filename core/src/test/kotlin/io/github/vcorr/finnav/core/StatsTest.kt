package io.github.vcorr.finnav.core

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsTest {
    private fun assertInterval(low: Double, high: Double, actual: Interval) {
        assertEquals(low, actual.low, 0.0005)
        assertEquals(high, actual.high, 0.0005)
    }

    @Test
    fun `wilson interval matches reference values`() {
        // Reference values computed independently (PLAN.md §1).
        assertInterval(0.5958, 0.9821, Stats.wilson(9, 10))
        assertInterval(0.7864, 0.9565, Stats.wilson(45, 50))
        assertInterval(0.0, 0.2775, Stats.wilson(0, 10))
        assertInterval(0.7225, 1.0, Stats.wilson(10, 10))
    }

    @Test
    fun `gate needs fifty trials and ninety percent`() {
        assertEquals(GateVerdict.NEED_MORE_TRIALS, AccuracyGate.verdict(10, 10))
        assertEquals(GateVerdict.PASS, AccuracyGate.verdict(45, 50))
        assertEquals(GateVerdict.FAIL, AccuracyGate.verdict(44, 50))
        assertEquals(GateVerdict.PASS, AccuracyGate.verdict(54, 60))
    }
}
