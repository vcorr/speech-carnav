package io.github.vcorr.finnav.spike

import android.content.Context
import io.github.vcorr.finnav.core.RunLog
import io.github.vcorr.finnav.core.RunRecord
import java.io.File
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Two files in the app's external files directory (readable over USB / adb pull):
 * runs.tsv holds one row per recognition attempt, notes.txt holds diagnostics and events.
 */
class SpikeLog(context: Context) {
    private val dir: File = context.getExternalFilesDir(null) ?: context.filesDir
    private val runs = File(dir, "runs.tsv")
    private val notes = File(dir, "notes.txt")

    val location: String get() = dir.absolutePath

    fun now(): String = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString()

    fun append(record: RunRecord) {
        if (!runs.exists()) runs.writeText(RunLog.HEADER + "\n")
        runs.appendText(RunLog.toTsvLine(record) + "\n")
    }

    fun note(text: String): String {
        val line = "${now()} $text"
        notes.appendText(line + "\n")
        return line
    }

    fun runsText(): String = if (runs.exists()) runs.readText() else ""

    fun notesText(): String = if (notes.exists()) notes.readText() else ""

    fun clear() {
        runs.delete()
        notes.delete()
    }
}
