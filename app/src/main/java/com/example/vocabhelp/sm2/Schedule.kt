package com.example.vocabhelp.sm2

import java.util.Calendar
import java.util.TimeZone

/**
 * When a word reviewed at [nowMillis] becomes due again: the *start* of the
 * local day [intervalDays] from now. Using day boundaries (not "now + N×24h")
 * means a word answered at 21:05 is already due at the next evening's 21:00
 * reminder instead of slipping a whole extra day. Calendar handles DST.
 */
fun dueAtStartOfDay(
    nowMillis: Long,
    intervalDays: Int,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long = Calendar.getInstance(timeZone).apply {
    timeInMillis = nowMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, intervalDays)
}.timeInMillis

/** Millis from [nowMillis] until the next [hour]:[minute] local time (today if still ahead, else tomorrow). */
fun millisUntilNext(
    hour: Int,
    minute: Int,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long {
    val target = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
    }
    return target.timeInMillis - nowMillis
}
