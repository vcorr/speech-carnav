package io.github.vcorr.finnav.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechErrorsTest {
    @Test
    fun `known and unknown codes`() {
        assertEquals("NO_MATCH", SpeechErrors.name(7))
        assertEquals("LANGUAGE_NOT_SUPPORTED", SpeechErrors.name(12))
        assertEquals("UNKNOWN_99", SpeechErrors.name(99))
    }
}
