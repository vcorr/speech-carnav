package io.github.vcorr.finnav.core

/** One recognition attempt in the Phase 0 run log. */
data class RunRecord(
    /** ISO-8601 local time, supplied by the caller. */
    val timestamp: String,
    /** Test condition, e.g. "parked", "driving", "parked+bias". Trials are tallied per condition. */
    val condition: String,
    /** How the app was opened: launcher alias, hands-free intent, button. */
    val trigger: String,
    val keyguardLocked: Boolean,
    val recogniser: String,
    /** Ground truth in corpus mode, otherwise null. */
    val expected: String?,
    val nBest: List<String>,
    val confidences: List<Float>,
    val match: MatchResult?,
    val error: String?,
    /** [StageTimer.summary] output. */
    val timings: String,
    val navUri: String?,
)

data class Tally(val trials: Int, val top1: Int, val anyOfN: Int)

/** Tab-separated run log: one header line, one line per [RunRecord]. Opens in any spreadsheet. */
object RunLog {
    private val COLUMNS = listOf(
        "timestamp", "condition", "trigger", "locked", "recogniser", "expected",
        "top1", "rank", "nbest", "confidences", "error", "timings", "nav_uri",
    )
    val HEADER: String = COLUMNS.joinToString("\t")

    fun toTsvLine(r: RunRecord): String = listOf(
        r.timestamp,
        r.condition,
        r.trigger,
        if (r.keyguardLocked) "1" else "0",
        r.recogniser,
        r.expected.orEmpty(),
        r.match?.let { if (it.top1) "1" else "0" }.orEmpty(),
        r.match?.rank?.toString().orEmpty(),
        r.nBest.joinToString(" | "),
        r.confidences.joinToString(" | ") { "%.2f".format(java.util.Locale.ROOT, it) },
        r.error.orEmpty(),
        r.timings,
        r.navUri.orEmpty(),
    ).joinToString("\t") { escape(it) }

    /** Tabs and line breaks would corrupt the row structure. */
    fun escape(field: String): String = field.replace(Regex("[\t\r\n]+"), " ")

    /** Corpus trials per condition, read back from the log text. Rows without ground truth are skipped. */
    fun tally(tsv: String): Map<String, Tally> {
        val lines = tsv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyMap()
        val header = lines.first().split('\t')
        val conditionAt = header.indexOf("condition")
        val expectedAt = header.indexOf("expected")
        val top1At = header.indexOf("top1")
        val rankAt = header.indexOf("rank")
        if (listOf(conditionAt, expectedAt, top1At, rankAt).any { it < 0 }) return emptyMap()

        val result = LinkedHashMap<String, Tally>()
        for (line in lines.drop(1)) {
            val fields = line.split('\t')
            if (fields.size != header.size || fields[expectedAt].isEmpty()) continue
            val old = result[fields[conditionAt]] ?: Tally(0, 0, 0)
            result[fields[conditionAt]] = Tally(
                trials = old.trials + 1,
                top1 = old.top1 + if (fields[top1At] == "1") 1 else 0,
                anyOfN = old.anyOfN + if (fields[rankAt].isNotEmpty()) 1 else 0,
            )
        }
        return result
    }
}
