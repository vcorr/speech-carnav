package io.github.vcorr.finnav.core

import java.util.Locale

/** Builds the URIs that hand a destination to a navigation app. */
object NavUri {
    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    /**
     * Turn-by-turn navigation in Google Maps to a free-text destination.
     * Maps runs its own search and picks the first hit.
     */
    fun googleNavigation(query: String): String {
        val q = query.trim().replace(WHITESPACE, " ")
        require(q.isNotEmpty()) { "Destination must not be blank" }
        return "google.navigation:q=" + encode(q)
    }

    /** Turn-by-turn navigation in Google Maps to a coordinate. */
    fun googleNavigation(lat: Double, lng: Double): String {
        require(lat in -90.0..90.0 && lng in -180.0..180.0) { "Coordinate out of range: $lat,$lng" }
        return "google.navigation:q=${coordinate(lat)},${coordinate(lng)}"
    }

    // Locale.ROOT: under a Finnish default locale "%f" would print "61,497800".
    private fun coordinate(value: Double) = String.format(Locale.ROOT, "%.6f", value)

    /** RFC 3986 percent-encoding of UTF-8 bytes; only unreserved characters pass through. */
    fun encode(text: String): String {
        val out = StringBuilder()
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (b < 0x80 && (c.isLetterOrDigit() || c in UNRESERVED_SYMBOLS)) {
                out.append(c)
            } else {
                out.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
        return out.toString()
    }

    private val WHITESPACE = Regex("\\s+")
    private const val UNRESERVED_SYMBOLS = "-._~"
    private const val HEX = "0123456789ABCDEF"
}
