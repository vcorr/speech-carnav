package io.github.vcorr.finnav.core

import kotlin.math.sqrt

data class Interval(val low: Double, val high: Double)

object Stats {
    /** Wilson score interval for a binomial proportion; z = 1.96 gives 95 %. */
    fun wilson(successes: Int, trials: Int, z: Double = 1.96): Interval {
        require(trials > 0 && successes in 0..trials) { "Need 0 <= successes <= trials and trials > 0" }
        val n = trials.toDouble()
        val p = successes / n
        val z2 = z * z
        val denominator = 1 + z2 / n
        val centre = p + z2 / (2 * n)
        val margin = z * sqrt(p * (1 - p) / n + z2 / (4 * n * n))
        return Interval(
            low = ((centre - margin) / denominator).coerceIn(0.0, 1.0),
            high = ((centre + margin) / denominator).coerceIn(0.0, 1.0),
        )
    }
}

enum class GateVerdict { NEED_MORE_TRIALS, PASS, FAIL }

/** PLAN.md §1: at least 45 correct of at least 50 trials, i.e. a 90 % success rate over ≥ 50 trials. */
object AccuracyGate {
    const val MIN_TRIALS = 50
    const val MIN_RATE = 0.9

    fun verdict(successes: Int, trials: Int): GateVerdict = when {
        trials < MIN_TRIALS -> GateVerdict.NEED_MORE_TRIALS
        successes >= MIN_RATE * trials -> GateVerdict.PASS
        else -> GateVerdict.FAIL
    }
}
