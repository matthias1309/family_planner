package de.familyplanner.android.feature.meals.data.attendance

import de.familyplanner.android.feature.meals.data.mapAllOrNull
import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.data.parseInstant
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceDay
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceException
import de.familyplanner.android.feature.meals.domain.attendance.AttendancePattern
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceProfile
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceSource
import de.familyplanner.android.feature.meals.domain.attendance.Presence
import de.familyplanner.android.feature.meals.domain.attendance.RawChildWeek
import de.familyplanner.android.feature.meals.domain.attendance.WeekAttendanceRaw
import de.familyplanner.api.model.AttendanceDay as ApiAttendanceDay
import de.familyplanner.api.model.AttendanceException as ApiAttendanceException
import de.familyplanner.api.model.AttendancePattern as ApiAttendancePattern
import de.familyplanner.api.model.AttendanceProfile as ApiAttendanceProfile
import de.familyplanner.api.model.AttendanceSource as ApiAttendanceSource
import de.familyplanner.api.model.MemberWeekAttendance as ApiMemberWeekAttendance
import de.familyplanner.api.model.PatternType as ApiPatternType
import de.familyplanner.api.model.Presence as ApiPresence
import de.familyplanner.api.model.WeekAttendance as ApiWeekAttendance

internal fun ApiPresence.toDomain(): Presence =
    when (this) {
        ApiPresence.PRESENT -> Presence.PRESENT
        ApiPresence.ABSENT -> Presence.ABSENT
    }

internal fun Presence.toApi(): ApiPresence =
    when (this) {
        Presence.PRESENT -> ApiPresence.PRESENT
        Presence.ABSENT -> ApiPresence.ABSENT
    }

/** `null`, wenn ein Wechselmodell ohne lesbare Referenzwoche kommt — der Aufrufer meldet dann einen Serverfehler. */
internal fun ApiAttendancePattern.toDomain(): AttendancePattern? =
    when (type) {
        ApiPatternType.ALWAYS -> AttendancePattern.Always
        ApiPatternType.ALTERNATING ->
            parseDate(referenceWeek)?.let { reference ->
                AttendancePattern.Alternating(
                    weekA.mapTo(mutableSetOf()) { it.toDomain() },
                    weekB.mapTo(mutableSetOf()) { it.toDomain() },
                    reference,
                )
            }
    }

internal fun AttendancePattern.toApi(): ApiAttendancePattern =
    when (this) {
        AttendancePattern.Always -> ApiAttendancePattern(ApiPatternType.ALWAYS, emptySet(), emptySet(), null)
        is AttendancePattern.Alternating ->
            ApiAttendancePattern(
                ApiPatternType.ALTERNATING,
                weekA.mapTo(mutableSetOf()) { it.toApi() },
                weekB.mapTo(mutableSetOf()) { it.toApi() },
                referenceWeek.toString(),
            )
    }

internal fun ApiAttendanceProfile.toDomain(): AttendanceProfile? = pattern.toDomain()?.let { AttendanceProfile(memberId, cooks, it) }

internal fun ApiAttendanceException.toDomain(): AttendanceException? {
    val start = parseDate(from) ?: return null
    val end = parseDate(to) ?: return null
    return AttendanceException(id, memberId, start, end, presence.toDomain(), note)
}

internal fun ApiWeekAttendance.toDomain(): WeekAttendanceRaw? {
    val start = parseDate(weekStart) ?: return null
    val deadline = parseInstant(orderDeadline) ?: return null
    val children = members.mapAllOrNull { it.toDomain() } ?: return null
    return WeekAttendanceRaw(start, deadline, children)
}

private fun ApiMemberWeekAttendance.toDomain(): RawChildWeek? {
    val parsedDays = days.mapAllOrNull { it.toDomain() } ?: return null
    val deadline = submissionDeadline?.let { parseInstant(it) ?: return null }
    return RawChildWeek(memberId, cooks, parsedDays, deadline)
}

private fun ApiAttendanceDay.toDomain(): AttendanceDay? = parseDate(date)?.let { AttendanceDay(it, present, source.toDomain()) }

private fun ApiAttendanceSource.toDomain(): AttendanceSource =
    when (this) {
        ApiAttendanceSource.PATTERN -> AttendanceSource.PATTERN
        ApiAttendanceSource.EXCEPTION -> AttendanceSource.EXCEPTION
    }
