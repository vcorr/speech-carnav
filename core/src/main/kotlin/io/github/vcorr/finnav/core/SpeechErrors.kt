package io.github.vcorr.finnav.core

/** Names for android.speech.SpeechRecognizer error codes, so logs stay readable without the SDK. */
object SpeechErrors {
    private val NAMES = mapOf(
        1 to "NETWORK_TIMEOUT",
        2 to "NETWORK",
        3 to "AUDIO",
        4 to "SERVER",
        5 to "CLIENT",
        6 to "SPEECH_TIMEOUT",
        7 to "NO_MATCH",
        8 to "RECOGNIZER_BUSY",
        9 to "INSUFFICIENT_PERMISSIONS",
        10 to "TOO_MANY_REQUESTS",
        11 to "SERVER_DISCONNECTED",
        12 to "LANGUAGE_NOT_SUPPORTED",
        13 to "LANGUAGE_UNAVAILABLE",
        14 to "CANNOT_CHECK_SUPPORT",
        15 to "CANNOT_LISTEN_TO_DOWNLOAD_EVENTS",
    )

    fun name(code: Int): String = NAMES[code] ?: "UNKNOWN_$code"
}
