package de.familyplanner.android.feature.meals.ui

import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.core.ui.formatTime
import de.familyplanner.android.feature.meals.domain.attendance.AttendancePattern
import java.time.DayOfWeek
import java.time.Instant

/** Eigene Namen statt `getDisplayName`: Punkte und Schreibweise hängen sonst von ICU/Gerät ab. */
private val ShortNames =
    mapOf(
        DayOfWeek.MONDAY to "Mo",
        DayOfWeek.TUESDAY to "Di",
        DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr",
        DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So",
    )
private val LongNames =
    mapOf(
        DayOfWeek.MONDAY to "Montag",
        DayOfWeek.TUESDAY to "Dienstag",
        DayOfWeek.WEDNESDAY to "Mittwoch",
        DayOfWeek.THURSDAY to "Donnerstag",
        DayOfWeek.FRIDAY to "Freitag",
        DayOfWeek.SATURDAY to "Samstag",
        DayOfWeek.SUNDAY to "Sonntag",
    )

/** Montag bis Sonntag — Reihenfolge für Tageszellen und Auswahl. */
val WeekDays: List<DayOfWeek> = DayOfWeek.entries

private const val MIN_RANGE_LENGTH = 3

fun weekdayShort(day: DayOfWeek): String = ShortNames.getValue(day)

fun weekdayLong(day: DayOfWeek): String = LongNames.getValue(day)

/** „Do 22.10., 16:00“ in Berliner Zeit. */
fun formatDeadline(instant: Instant): String {
    val local = instant.atZone(BerlinZone)
    return "${weekdayShort(local.dayOfWeek)} ${formatDate(local.toLocalDate())}, ${formatTime(instant)}"
}

/** Zusammenhängende Tage ab drei als Bereich, sonst einzeln: `Mo, Di, Do–Sa`. */
fun summarizeDays(days: Set<DayOfWeek>): String {
    if (days.isEmpty()) return "–"
    val runs = mutableListOf<List<DayOfWeek>>()
    for (day in WeekDays.filter { it in days }) {
        val last = runs.lastOrNull()
        if (last != null && last.last().plus(1) == day) runs[runs.lastIndex] = last + day else runs += listOf(day)
    }
    return runs.joinToString(", ") { run ->
        if (run.size >= MIN_RANGE_LENGTH) {
            "${weekdayShort(run.first())}–${weekdayShort(run.last())}"
        } else {
            run.joinToString(", ") { weekdayShort(it) }
        }
    }
}

fun patternSummary(pattern: AttendancePattern): String =
    when (pattern) {
        AttendancePattern.Always -> "immer da"
        is AttendancePattern.Alternating ->
            "Wechselmodell · A: ${summarizeDays(pattern.weekA)} · B: ${summarizeDays(pattern.weekB)}"
    }
