package de.familyplanner.android.feature.meals.ui

import de.familyplanner.android.feature.meals.domain.attendance.AttendancePattern
import de.familyplanner.android.feature.meals.ui.profile.dateToPickerMillis
import de.familyplanner.android.feature.meals.ui.profile.pickerMillisToDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelsTest {
    @Test
    fun `REQ-MEAL-010 day ranges are summarised`() {
        assertEquals("Di–Do", summarizeDays(setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)))
        assertEquals("Di–So", summarizeDays(DayOfWeek.entries.filter { it >= DayOfWeek.TUESDAY }.toSet()))
        assertEquals("Mo, Mi–Fr", summarizeDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)))
        assertEquals("Mo, Di", summarizeDays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)))
        assertEquals("–", summarizeDays(emptySet()))
    }

    @Test
    fun `REQ-MEAL-010 pattern summary names always and alternating`() {
        assertEquals("immer da", patternSummary(AttendancePattern.Always))
        assertEquals(
            "Wechselmodell · A: Di–Do · B: Sa",
            patternSummary(
                AttendancePattern.Alternating(
                    setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
                    setOf(DayOfWeek.SATURDAY),
                    LocalDate.of(2026, 10, 5),
                ),
            ),
        )
    }

    @Test
    fun `deadlines are shown in Berlin time across the daylight saving change`() {
        assertEquals("Do 22.10., 16:00", formatDeadline(Instant.parse("2026-10-22T14:00:00Z")))
        assertEquals("Do 29.10., 16:00", formatDeadline(Instant.parse("2026-10-29T15:00:00Z")))
    }

    @Test
    fun `REQ-MEAL-011 picker millis are read as UTC dates`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            val millis = Instant.parse("2026-12-20T00:00:00Z").toEpochMilli()

            assertEquals(LocalDate.of(2026, 12, 20), pickerMillisToDate(millis))
            assertEquals(millis, dateToPickerMillis(LocalDate.of(2026, 12, 20)))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun `weekday names are fixed German strings`() {
        assertEquals("Mo", weekdayShort(DayOfWeek.MONDAY))
        assertEquals("Sonntag", weekdayLong(DayOfWeek.SUNDAY))
    }
}
