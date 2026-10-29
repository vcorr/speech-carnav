package io.github.vcorr.finnav.spike

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.vcorr.finnav.R
import io.github.vcorr.finnav.core.AccuracyGate
import io.github.vcorr.finnav.core.Corpus
import io.github.vcorr.finnav.core.GateVerdict
import io.github.vcorr.finnav.core.MatchResult
import io.github.vcorr.finnav.core.NavUri
import io.github.vcorr.finnav.core.RunLog
import io.github.vcorr.finnav.core.RunRecord
import io.github.vcorr.finnav.core.Stats
import io.github.vcorr.finnav.core.TranscriptMatch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Phase 0 spike screen. Opened by voice ("open FinNav"), by the launcher, or by the
 * hands-free voice-search intent; shows over the lock screen; listens in Finnish; speaks the
 * result back; starts Google Maps. Every attempt is logged for docs/SPIKES.md.
 */
class SpikeActivity : Activity(), SpeechProbe.Listener {

    private lateinit var prefs: Prefs
    private lateinit var log: SpikeLog
    private lateinit var probe: SpeechProbe
    private lateinit var speaker: Speaker
    private val main = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var prompt: TextView
    private lateinit var heard: TextView
    private lateinit var score: TextView
    private lateinit var recogniserButton: Button
    private lateinit var destination: EditText
    private lateinit var notesView: TextView
    private val recentNotes = ArrayDeque<String>()

    // Launch context, recorded with every run (S4).
    private var trigger = "none"
    private var launchedAt: Long? = null
    private var captureLocked = false
    private var captureSinceLaunch: Long? = null

    private var pendingCapture = false
    private var focusRequest: AudioFocusRequest? = null
    private var currentRun: Run? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)
        log = SpikeLog(this)
        probe = SpeechProbe(this, this)
        setContentView(buildUi())
        log.notesText().lines().filter { it.isNotBlank() }.takeLast(MAX_NOTES).forEach { recentNotes.addFirst(it) }
        notesView.text = recentNotes.joinToString("\n")
        speaker = Speaker(this) { note(it) }
        refreshCorpus()
        handleLaunch(intent, fresh = savedInstanceState == null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunch(intent, fresh = true)
    }

    override fun onDestroy() {
        currentRun?.save()
        probe.cancel()
        speaker.shutdown()
        releaseFocus()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun handleLaunch(intent: Intent?, fresh: Boolean) {
        val power = getSystemService(PowerManager::class.java)
        trigger = Diagnostics.trigger(intent)
        launchedAt = SystemClock.elapsedRealtime()
        note("Launch: ${Diagnostics.launchContext(this, intent)} locked=${keyguardLocked()} interactive=${power.isInteractive}")
        val handsFree = intent?.action == RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE
        if (fresh && (prefs.autoListen || handsFree)) startCapture()
    }

    // ---- Capture pipeline -------------------------------------------------------------------

    private fun startCapture() {
        if (probe.isListening) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCapture = true
            status.setText(R.string.status_no_permission)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        currentRun?.save()
        currentRun = null
        captureLocked = keyguardLocked()
        captureSinceLaunch = launchedAt?.let { SystemClock.elapsedRealtime() - it }
        launchedAt = null
        requestFocus()
        status.setText(R.string.status_listening)
        heard.text = ""
        probe.start(prefs.recogniser, if (prefs.biasing) Corpus.biasingStrings() else null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MIC) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            status.setText(R.string.status_ready)
            if (pendingCapture) {
                pendingCapture = false
                startCapture()
            }
        }
    }

    override fun onReady() {
        status.setText(R.string.status_listening)
    }

    override fun onPartial(text: String) {
        heard.text = text
    }

    override fun onFinished(outcome: SpeechProbe.Outcome) {
        val expected = if (prefs.corpusMode) Corpus.entryFor(prefs.corpusTrial).utterance else null
        val match = expected?.let { TranscriptMatch.score(it, outcome.nBest) }
        val run = Run(outcome, expected, match).also { currentRun = it }
        status.setText(R.string.status_ready)
        heard.text = describe(outcome, match)
        note("Result via ${outcome.recogniser}: ${outcome.nBest.firstOrNull() ?: outcome.error} mic=${outcome.recordingDevices}")

        val top = outcome.nBest.firstOrNull()
        when {
            prefs.corpusMode -> {
                run.save()
                prefs.corpusTrial = prefs.corpusTrial + 1
                refreshCorpus()
                val verdict = getString(if (match?.top1 == true) R.string.say_correct else R.string.say_wrong)
                val next = getString(R.string.say_next, Corpus.entryFor(prefs.corpusTrial).utterance)
                speaker.speak("$verdict. $next") { releaseFocus() }
            }
            top == null -> {
                run.save()
                speaker.speak(getString(R.string.say_not_heard)) { releaseFocus() }
            }
            else -> confirmAndNavigate(run, top)
        }
    }

    /** PLAN.md §4.1: Maps can start while the confirmation is spoken, or after it. Both are timed. */
    private fun confirmAndNavigate(run: Run, text: String) {
        val uri = NavUri.googleNavigation(text)
        val timer = run.outcome.timer
        val navigate = {
            if (prefs.launchMaps) {
                run.navUri = uri
                timer.mark("maps-intent")
                NavLauncher.launch(this, uri, prefs.dismissKeyguard, ::note)
            }
        }
        if (!prefs.confirmAloud) {
            navigate()
            run.save()
            releaseFocus()
            return
        }
        val phrase = getString(R.string.say_navigating, text)
        if (prefs.mapsDuringSpeech) {
            speaker.speak(phrase) {
                timer.mark("tts-done")
                run.save()
                releaseFocus()
            }
            navigate()
        } else {
            speaker.speak(phrase) {
                timer.mark("tts-done")
                navigate()
                run.save()
                releaseFocus()
            }
        }
    }

    /** One recognition attempt; written to the run log exactly once. */
    private inner class Run(val outcome: SpeechProbe.Outcome, val expected: String?, val match: MatchResult?) {
        private val locked = captureLocked
        private val sinceLaunch = captureSinceLaunch
        var navUri: String? = null
        private var saved = false

        fun save() {
            if (saved) return
            saved = true
            log.append(
                RunRecord(
                    timestamp = log.now(),
                    condition = prefs.condition,
                    trigger = trigger,
                    keyguardLocked = locked,
                    recogniser = outcome.recogniser,
                    expected = expected,
                    nBest = outcome.nBest,
                    confidences = outcome.confidences,
                    match = match,
                    error = outcome.error,
                    timings = (sinceLaunch?.let { "since-launch=$it " } ?: "") + outcome.timer.summary(),
                    navUri = navUri,
                ),
            )
            refreshScore()
        }
    }

    // ---- Manual S1 / S5 / S6 tests ----------------------------------------------------------

    private fun typedDestination(): String? = destination.text.toString().trim().ifEmpty { null }

    private fun navigateTyped(delayMs: Long) {
        val text = typedDestination() ?: return
        val uri = NavUri.googleNavigation(text)
        if (delayMs == 0L) {
            NavLauncher.launch(this, uri, prefs.dismissKeyguard, ::note)
            return
        }
        // S1(d): with the screen off this is a background start and is expected to be blocked.
        note("Maps intent in ${delayMs / 1000} s: lock the phone now")
        main.postDelayed({
            note("Delayed start firing: locked=${keyguardLocked()}")
            NavLauncher.launch(this, uri, false, ::note)
        }, delayMs)
    }

    private fun cycleRecogniser() {
        val options = listOf("", SpeechProbe.ON_DEVICE) +
            Diagnostics.recognitionServices(this).map { it.flattenToString() }
        prefs.recogniser = options[(options.indexOf(prefs.recogniser) + 1) % options.size]
        updateRecogniserLabel()
    }

    private fun updateRecogniserLabel() {
        val name = when (val choice = prefs.recogniser) {
            "" -> getString(R.string.recogniser_default)
            SpeechProbe.ON_DEVICE -> getString(R.string.recogniser_on_device)
            else -> ComponentName.unflattenFromString(choice)?.packageName ?: choice
        }
        recogniserButton.text = getString(R.string.btn_recogniser, name)
    }

    private fun share() {
        val body = "runs.tsv (${log.location}):\n${log.runsText()}\n\nnotes.txt:\n${log.notesText()}"
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "FinNav spike log")
            .putExtra(Intent.EXTRA_TEXT, body)
        startActivity(Intent.createChooser(send, null))
    }

    // ---- Corpus and score -------------------------------------------------------------------

    private fun stepCorpus(delta: Int) {
        prefs.corpusTrial = prefs.corpusTrial + delta
        refreshCorpus()
    }

    private fun refreshCorpus() {
        if (prefs.corpusMode) {
            val trial = prefs.corpusTrial
            val size = Corpus.entries.size
            prompt.text = getString(R.string.corpus_prompt, Math.floorMod(trial, size) + 1, size, Corpus.entryFor(trial).utterance)
            prompt.visibility = View.VISIBLE
        } else {
            prompt.visibility = View.GONE
        }
        refreshScore()
    }

    private fun refreshScore() {
        val condition = prefs.condition
        val tally = RunLog.tally(log.runsText())[condition]
        score.text = if (tally == null || tally.trials == 0) {
            getString(R.string.corpus_no_trials, condition)
        } else {
            val ci = Stats.wilson(tally.top1, tally.trials)
            val verdict = when (AccuracyGate.verdict(tally.top1, tally.trials)) {
                GateVerdict.PASS -> getString(R.string.gate_pass)
                GateVerdict.FAIL -> getString(R.string.gate_fail)
                GateVerdict.NEED_MORE_TRIALS -> getString(R.string.gate_more, AccuracyGate.MIN_TRIALS)
            }
            getString(
                R.string.corpus_score, condition, tally.top1, tally.trials,
                (ci.low * 100).roundToInt(), (ci.high * 100).roundToInt(), verdict,
            )
        }
    }

    private fun describe(outcome: SpeechProbe.Outcome, match: MatchResult?): String = buildString {
        outcome.error?.let { appendLine("! $it") }
        outcome.nBest.forEachIndexed { i, text ->
            val confidence = outcome.confidences.getOrNull(i)?.let { " (%.2f)".format(Locale.ROOT, it) }.orEmpty()
            appendLine("${i + 1}. $text$confidence")
        }
        match?.let { appendLine(if (it.top1) "✓" else "✗" + (it.rank?.let { r -> " (#$r)" }.orEmpty())) }
        append(outcome.timer.summary())
    }

    // ---- Audio focus, notes, helpers --------------------------------------------------------

    private fun requestFocus() {
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        val result = getSystemService(AudioManager::class.java).requestAudioFocus(request)
        focusRequest = request
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) note("Audio focus not granted ($result)")
    }

    private fun releaseFocus() {
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun keyguardLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun note(text: String) {
        recentNotes.addFirst(log.note(text))
        while (recentNotes.size > MAX_NOTES) recentNotes.removeAt(recentNotes.lastIndex)
        notesView.text = recentNotes.joinToString("\n")
    }

    private fun buildUi(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        status = label(28f).apply { setText(R.string.status_ready) }
        prompt = label(22f)
        heard = label(18f)
        score = label(14f)
        listOf(status, prompt, heard, score).forEach { column.addView(it) }

        column.addView(row(
            button(R.string.btn_listen) { startCapture() },
            button(R.string.btn_prev) { stepCorpus(-1) },
            button(R.string.btn_next) { stepCorpus(1) },
        ))
        recogniserButton = Button(this).apply { setOnClickListener { cycleRecogniser() } }
        updateRecogniserLabel()
        column.addView(recogniserButton)
        column.addView(button(R.string.btn_check_support) { probe.checkSupport(prefs.recogniser, ::note) })

        column.addView(option(R.string.opt_auto_listen, prefs.autoListen) { prefs.autoListen = it })
        column.addView(option(R.string.opt_launch_maps, prefs.launchMaps) { prefs.launchMaps = it })
        column.addView(option(R.string.opt_confirm, prefs.confirmAloud) { prefs.confirmAloud = it })
        column.addView(option(R.string.opt_maps_during_speech, prefs.mapsDuringSpeech) { prefs.mapsDuringSpeech = it })
        column.addView(option(R.string.opt_dismiss_keyguard, prefs.dismissKeyguard) { prefs.dismissKeyguard = it })
        column.addView(option(R.string.opt_biasing, prefs.biasing) { prefs.biasing = it; refreshScore() })
        column.addView(option(R.string.opt_corpus, prefs.corpusMode) { prefs.corpusMode = it; refreshCorpus() })
        column.addView(option(R.string.opt_driving, prefs.driving) { prefs.driving = it; refreshScore() })

        destination = EditText(this).apply {
            setHint(R.string.hint_destination)
            setText(getString(R.string.default_destination))
            setSingleLine(true)
        }
        column.addView(destination)
        column.addView(row(
            button(R.string.btn_nav_now) { navigateTyped(0) },
            button(R.string.btn_geocode) { typedDestination()?.let { GeocodeProbe.lookup(this, it, ::note) } },
        ))
        column.addView(button(R.string.btn_nav_delayed) { navigateTyped(10_000) })
        column.addView(row(
            button(R.string.btn_tts_test) { speaker.speak(getString(R.string.tts_test_phrase)) { note("TTS test finished") } },
            button(R.string.btn_diagnostics) { Diagnostics.report(this).forEach(::note) },
        ))
        column.addView(row(
            button(R.string.btn_share) { share() },
            button(R.string.btn_clear) {
                log.clear()
                recentNotes.clear()
                notesView.text = ""
                refreshScore()
            },
        ))
        notesView = label(12f).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        column.addView(notesView)
        return ScrollView(this).apply { addView(column) }
    }

    private fun label(sizeSp: Float) = TextView(this).apply {
        textSize = sizeSp
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun button(text: Int, action: () -> Unit) = Button(this).apply {
        setText(text)
        setOnClickListener { action() }
    }

    private fun option(text: Int, value: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        setText(text)
        isChecked = value
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val REQUEST_MIC = 1
        const val MAX_NOTES = 80
    }
}
