package io.github.vcorr.finnav.spike

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.vcorr.finnav.core.Finland
import java.util.Locale

/** S6 (informational): what the platform geocoder makes of a Finnish destination. */
object GeocodeProbe {
    fun lookup(context: Context, text: String, report: (String) -> Unit) {
        if (!Geocoder.isPresent()) {
            report("Geocoder not present")
            return
        }
        val main = Handler(Looper.getMainLooper())
        val started = SystemClock.elapsedRealtime()
        Geocoder(context, Locale.forLanguageTag("fi-FI")).getFromLocationName(
            text, 5, Finland.SOUTH, Finland.WEST, Finland.NORTH, Finland.EAST,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    val ms = SystemClock.elapsedRealtime() - started
                    val lines = addresses.mapIndexed { i, a ->
                        "  ${i + 1}. ${a.getAddressLine(0)} (${a.latitude}, ${a.longitude})"
                    }
                    main.post { report((listOf("Geocode \"$text\": ${addresses.size} hits in $ms ms") + lines).joinToString("\n")) }
                }

                override fun onError(errorMessage: String?) {
                    main.post { report("Geocode \"$text\" failed: $errorMessage") }
                }
            },
        )
    }
}
