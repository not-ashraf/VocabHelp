package com.example.vocabhelp.sm2

import kotlin.math.ceil

/** SM-2 spaced repetition state. Pure data — no DB / time concerns. */
data class Sm2State(
    val ef: Float,
    val intervalDays: Int,
    val repetitions: Int,
) {
    companion object {
        val INITIAL: Sm2State = Sm2State(ef = 2.5f, intervalDays = 0, repetitions = 0)
    }
}

/**
 * Apply one SM-2 review. `quality` is the SuperMemo q-grade in [0, 5]:
 *   5 = correct on first tap (perfect recall)
 *   3 = correct after a hint
 *   1 = wrong tap (lapse)
 *
 * On lapse (q < 3): reset reps -> 0, interval -> 1 day, EF stays bounded at >= 1.3.
 * On success: grow reps; interval cycles through 1d, 6d, then prev * EF.
 */
fun applySm2(prev: Sm2State, quality: Int): Sm2State {
    val q = quality.coerceIn(0, 5)
    if (q < 3) {
        return Sm2State(
            ef = prev.ef.coerceAtLeast(MIN_EF),
            intervalDays = 1,
            repetitions = 0,
        )
    }
    val newReps = prev.repetitions + 1
    val newInterval = when (newReps) {
        1 -> 1
        2 -> 6
        else -> ceil(prev.intervalDays * prev.ef).toInt().coerceAtLeast(1)
    }
    val newEf = (prev.ef + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f)))
        .coerceAtLeast(MIN_EF)
    return Sm2State(ef = newEf, intervalDays = newInterval, repetitions = newReps)
}

private const val MIN_EF = 1.3f
