package io.github.vcorr.finnav.spike

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Finnish TTS with navigation-guidance audio attributes, so Android Auto should route it like
 * Maps' own prompts (S5). Completion callbacks are delivered on the main thread.
 */
class Speaker(context: Context, private val onStatus: (String) -> Unit) : TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val pending = HashMap<String, () -> Unit>()
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            onStatus("TTS init failed ($status)")
            return
        }
        val language = tts.setLanguage(Locale.forLanguageTag("fi-FI"))
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) = complete(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = complete(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = complete(utteranceId)
        })
        ready = language >= TextToSpeech.LANG_AVAILABLE
        onStatus("TTS engine=${tts.defaultEngine} fi-FI=${languageStatus(language)}")
    }

    /** Speaks [text]; [onDone] runs when it finishes, fails, or immediately if TTS is unavailable. */
    fun speak(text: String, onDone: () -> Unit = {}) {
        if (!ready) {
            onDone()
            return
        }
        val id = UUID.randomUUID().toString()
        pending[id] = onDone
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun complete(utteranceId: String?) {
        main.post { utteranceId?.let { pending.remove(it) }?.invoke() }
    }

    fun shutdown() {
        tts.shutdown()
        pending.clear()
    }

    private fun languageStatus(code: Int) = when (code) {
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE -> "available"
        TextToSpeech.LANG_AVAILABLE -> "language only"
        TextToSpeech.LANG_MISSING_DATA -> "missing data"
        TextToSpeech.LANG_NOT_SUPPORTED -> "not supported"
        else -> "code $code"
    }
}
