package io.github.vcorr.finnav.spike

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.github.vcorr.finnav.core.SpeechErrors
import io.github.vcorr.finnav.core.StageTimer

/**
 * One fi-FI recognition at a time through a chosen RecognitionService (S2), timing each stage.
 * Must be used from the main thread.
 */
class SpeechProbe(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onFinished(outcome: Outcome)
    }

    data class Outcome(
        val recogniser: String,
        val nBest: List<String>,
        val confidences: List<Float>,
        val error: String?,
        val timer: StageTimer,
        val recordingDevices: String,
    )

    private var recognizer: SpeechRecognizer? = null

    val isListening: Boolean get() = recognizer != null

    fun start(choice: String, biasing: List<String>?) {
        cancel()
        val timer = StageTimer { SystemClock.elapsedRealtime() }
        val (sr, label) = try {
            create(choice)
        } catch (e: RuntimeException) {
            listener.onFinished(Outcome(choice, emptyList(), emptyList(), "CREATE_FAILED: ${e.message}", timer, ""))
            return
        }
        recognizer = sr
        timer.mark("created")
        var recordingDevices = ""

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                timer.mark("ready")
                listener.onReady()
            }

            override fun onBeginningOfSpeech() {
                timer.mark("speech-start")
                recordingDevices = Diagnostics.activeRecordingDevices(context)
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() = timer.mark("speech-end")

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    timer.mark("first-partial")
                    listener.onPartial(text)
                }
            }

            override fun onResults(results: Bundle?) {
                timer.mark("results")
                val nBest = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.toList().orEmpty()
                finish(Outcome(label, nBest, confidences, null, timer, recordingDevices))
            }

            override fun onError(error: Int) {
                timer.mark("error")
                finish(Outcome(label, emptyList(), emptyList(), SpeechErrors.name(error), timer, recordingDevices))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        sr.startListening(recognitionIntent(biasing))
    }

    /** Some services report an error after results; only the first outcome counts. */
    private fun finish(outcome: Outcome) {
        if (recognizer == null) return
        release()
        listener.onFinished(outcome)
    }

    fun cancel() {
        recognizer?.cancel()
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    /** Reports which Finnish variants the chosen service claims to support, online and on-device. */
    fun checkSupport(choice: String, report: (String) -> Unit) {
        val (sr, label) = try {
            create(choice)
        } catch (e: RuntimeException) {
            report("Support check $choice: cannot create (${e.message})")
            return
        }
        sr.checkRecognitionSupport(recognitionIntent(null), context.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                fun fi(list: List<String>) = list.filter { it.startsWith("fi") }.ifEmpty { listOf("-") }.joinToString()
                report(
                    "Support $label: online=${fi(recognitionSupport.onlineLanguages)} " +
                        "installed=${fi(recognitionSupport.installedOnDeviceLanguages)} " +
                        "downloadable=${fi(recognitionSupport.supportedOnDeviceLanguages)} " +
                        "pending=${fi(recognitionSupport.pendingOnDeviceLanguages)}",
                )
                sr.destroy()
            }

            override fun onError(error: Int) {
                report("Support $label: error ${SpeechErrors.name(error)}")
                sr.destroy()
            }
        })
    }

    private fun create(choice: String): Pair<SpeechRecognizer, String> = when (choice) {
        ON_DEVICE -> {
            check(SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) { "on-device recognition unavailable" }
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context) to ON_DEVICE
        }
        "" -> SpeechRecognizer.createSpeechRecognizer(context) to "default"
        else -> {
            val component = checkNotNull(ComponentName.unflattenFromString(choice)) { "bad component $choice" }
            SpeechRecognizer.createSpeechRecognizer(context, component) to component.flattenToShortString()
        }
    }

    private fun recognitionIntent(biasing: List<String>?) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (biasing != null) putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(biasing))
    }

    companion object {
        const val ON_DEVICE = "on-device"
        const val LANGUAGE = "fi-FI"
    }
}
