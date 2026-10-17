package de.familyplanner.android.feature.meals.domain.attendance

import de.familyplanner.android.core.ui.BerlinZone
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private const val WEEKS_PER_CYCLE = 2L

fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Die „aktuelle“ Planwoche ist der Montag nach heute (Berlin) — geplant wird immer die kommende Woche. */
fun currentPlanWeek(now: Instant): LocalDate = mondayOf(now.atZone(BerlinZone).toLocalDate()).plusWeeks(1)

/** `floorMod`, damit die Parität auch vor der Referenzwoche stimmt. */
fun weekTypeOf(
    date: LocalDate,
    referenceWeek: LocalDate,
): WeekType = if (Math.floorMod(ChronoUnit.WEEKS.between(referenceWeek, mondayOf(date)), WEEKS_PER_CYCLE) == 0L) WeekType.A else WeekType.B

/** Referenzwoche so wählen, dass die Kalenderwoche von [today] den Typ [typeOfThisWeek] hat. */
fun referenceWeekFor(
    today: LocalDate,
    typeOfThisWeek: WeekType,
): LocalDate = mondayOf(today).let { if (typeOfThisWeek == WeekType.A) it else it.minusWeeks(1) }
