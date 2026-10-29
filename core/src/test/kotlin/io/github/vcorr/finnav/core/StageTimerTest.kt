package io.github.vcorr.finnav.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StageTimerTest {
    @Test
    fun `records first occurrence of each stage relative to creation`() {
        var now = 1_000L
        val timer = StageTimer { now }
        now = 1_300L; timer.mark("ready")
        now = 2_100L; timer.mark("speech-start")
        now = 2_500L; timer.mark("ready")

        assertEquals(300L, timer.elapsed("ready"))
        assertEquals(800L, timer.between("ready", "speech-start"))
        assertNull(timer.between("ready", "results"))
        assertEquals("ready=300 speech-start=1100", timer.summary())
    }
}
