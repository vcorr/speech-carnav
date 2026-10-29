package io.github.vcorr.finnav.core

/**
 * Records when each pipeline stage was first reached, in milliseconds since the timer was created.
 * [clock] must be monotonic (on Android, SystemClock.elapsedRealtime).
 */
class StageTimer(private val clock: () -> Long) {
    private val start = clock()
    private val marks = LinkedHashMap<String, Long>()

    /** Records [stage] the first time it is reached; later calls for the same stage are ignored. */
    fun mark(stage: String) {
        if (stage !in marks) marks[stage] = clock() - start
    }

    fun elapsed(stage: String): Long? = marks[stage]

    /** Duration between two recorded stages, or null if either is missing. */
    fun between(from: String, to: String): Long? {
        val a = marks[from] ?: return null
        val b = marks[to] ?: return null
        return b - a
    }

    fun summary(): String = marks.entries.joinToString(" ") { "${it.key}=${it.value}" }
}
