package io.github.vcorr.finnav.spike

import android.content.Context

/** Spike switches, persisted so a test setup survives the app being reopened by voice. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("spike", Context.MODE_PRIVATE)

    private fun flag(key: String, default: Boolean) = sp.getBoolean(key, default)
    private fun setFlag(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()

    var autoListen: Boolean
        get() = flag("autoListen", true)
        set(v) = setFlag("autoListen", v)

    var launchMaps: Boolean
        get() = flag("launchMaps", true)
        set(v) = setFlag("launchMaps", v)

    var confirmAloud: Boolean
        get() = flag("confirmAloud", true)
        set(v) = setFlag("confirmAloud", v)

    /** PLAN.md §4.1: launch Maps while the confirmation plays, rather than after it. */
    var mapsDuringSpeech: Boolean
        get() = flag("mapsDuringSpeech", true)
        set(v) = setFlag("mapsDuringSpeech", v)

    var dismissKeyguard: Boolean
        get() = flag("dismissKeyguard", false)
        set(v) = setFlag("dismissKeyguard", v)

    var biasing: Boolean
        get() = flag("biasing", false)
        set(v) = setFlag("biasing", v)

    var corpusMode: Boolean
        get() = flag("corpusMode", false)
        set(v) = setFlag("corpusMode", v)

    var driving: Boolean
        get() = flag("driving", false)
        set(v) = setFlag("driving", v)

    /** "" = system default, [SpeechProbe.ON_DEVICE], or a flattened ComponentName. */
    var recogniser: String
        get() = sp.getString("recogniser", "").orEmpty()
        set(v) = sp.edit().putString("recogniser", v).apply()

    var corpusTrial: Int
        get() = sp.getInt("corpusTrial", 0)
        set(v) = sp.edit().putInt("corpusTrial", v).apply()

    /** Trials are tallied per condition; biased runs are kept apart from unbiased ones. */
    val condition: String
        get() = (if (driving) "driving" else "parked") + (if (biasing) "+bias" else "")
}
