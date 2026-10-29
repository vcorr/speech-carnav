package io.github.vcorr.finnav.core

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NavUriTest {
    @Test
    fun `encodes Finnish letters as UTF-8`() {
        assertEquals("%C3%A4%C3%B6%C3%A5%C3%84%C3%96%C3%85", NavUri.encode("äöåÄÖÅ"))
    }

    @Test
    fun `encodes spaces and commas, keeps unreserved characters`() {
        assertEquals("H%C3%A4meenkatu%2010%2C%20Tampere", NavUri.encode("Hämeenkatu 10, Tampere"))
        assertEquals("Tampere-Pirkkala_1.~", NavUri.encode("Tampere-Pirkkala_1.~"))
    }

    @Test
    fun `text destination is trimmed and whitespace collapsed`() {
        assertEquals(
            "google.navigation:q=S%C3%A4rk%C3%A4nniemi%20Tampere",
            NavUri.googleNavigation("  Särkänniemi \n Tampere "),
        )
    }

    @Test
    fun `blank destination is rejected`() {
        assertFailsWith<IllegalArgumentException> { NavUri.googleNavigation("   ") }
    }

    @Test
    fun `coordinates use a decimal point even under a Finnish default locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fi-FI"))
            assertEquals("google.navigation:q=61.497800,23.761000", NavUri.googleNavigation(61.4978, 23.761))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `out-of-range coordinates are rejected`() {
        assertFailsWith<IllegalArgumentException> { NavUri.googleNavigation(91.0, 23.0) }
    }
}
