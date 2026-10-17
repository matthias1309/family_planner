package de.familyplanner.meals.web

import de.familyplanner.core.error.AppException
import de.familyplanner.meals.attendance.AttendanceDay
import de.familyplanner.meals.attendance.AttendanceException
import de.familyplanner.meals.attendance.AttendancePattern
import de.familyplanner.meals.attendance.AttendanceProfile
import de.familyplanner.meals.attendance.DeadlineSettings
import de.familyplanner.meals.attendance.DeadlineSettingsOverview
import de.familyplanner.meals.attendance.MemberWeekAttendance
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.attendance.WeekAttendance
import java.time.DayOfWeek
import de.familyplanner.api.model.AttendanceDay as ApiAttendanceDay
import de.familyplanner.api.model.AttendanceException as ApiAttendanceException
import de.familyplanner.api.model.AttendancePattern as ApiAttendancePattern
import de.familyplanner.api.model.AttendanceProfile as ApiAttendanceProfile
import de.familyplanner.api.model.AttendanceSource as ApiAttendanceSource
import de.familyplanner.api.model.DeadlineSettings as ApiDeadlineSettings
import de.familyplanner.api.model.DeadlineSettingsOverview as ApiDeadlineSettingsOverview
import de.familyplanner.api.model.MemberWeekAttendance as ApiMemberWeekAttendance
import de.familyplanner.api.model.PatternType as ApiPatternType
import de.familyplanner.api.model.Presence as ApiPresence
import de.familyplanner.api.model.WeekAttendance as ApiWeekAttendance
import de.familyplanner.api.model.Weekday as ApiWeekday
import de.familyplanner.meals.attendance.AttendanceSource as DomainSource
import de.familyplanner.meals.attendance.Presence as DomainPresence

fun ApiWeekday.toDomain(): DayOfWeek = DayOfWeek.valueOf(name)

fun DayOfWeek.toApi(): ApiWeekday = ApiWeekday.valueOf(name)

fun ApiPresence.toDomain(): DomainPresence = DomainPresence.valueOf(name)

fun DomainPresence.toApi(): ApiPresence = ApiPresence.valueOf(name)

fun ApiAttendancePattern.toDomain(): AttendancePattern =
    when (type) {
        ApiPatternType.ALWAYS -> AttendancePattern.Always
        ApiPatternType.ALTERNATING -> {
            val reference = referenceWeek ?: throw AppException.Validation("Das Wechselmodell braucht eine Referenzwoche")
            AttendancePattern.Alternating(
                weekA = weekA.map { it.toDomain() }.toSet(),
                weekB = weekB.map { it.toDomain() }.toSet(),
                referenceWeek = PlanWeek(parseDate(reference, "referenceWeek")),
            )
        }
    }

fun AttendancePattern.toApi(): ApiAttendancePattern =
    when (this) {
        AttendancePattern.Always -> ApiAttendancePattern(type = ApiPatternType.ALWAYS, weekA = emptySet(), weekB = emptySet())
        is AttendancePattern.Alternating ->
            ApiAttendancePattern(
                type = ApiPatternType.ALTERNATING,
                weekA = weekA.sorted().map { it.toApi() }.toSet(),
                weekB = weekB.sorted().map { it.toApi() }.toSet(),
                referenceWeek = referenceWeek.monday.toString(),
            )
    }

fun AttendanceProfile.toApi() = ApiAttendanceProfile(memberId = memberId.value, cooks = cooks, pattern = pattern.toApi())

fun AttendanceException.toApi() =
    ApiAttendanceException(
        id = id.value,
        memberId = memberId.value,
        from = from.toString(),
        to = to.toString(),
        presence = presence.toApi(),
        createdAt = createdAt.toString(),
        note = note,
    )

fun DeadlineSettings.toApi() =
    ApiDeadlineSettings(
        orderDeadlineDay = orderDeadlineDay.toApi(),
        orderDeadlineTime = formatTime(orderDeadlineTime),
        childDeadlineTime = formatTime(childDeadlineTime),
        effectiveFrom = effectiveFrom.monday.toString(),
    )

fun DeadlineSettingsOverview.toApi() = ApiDeadlineSettingsOverview(current = current.toApi(), scheduled = scheduled?.toApi())

fun AttendanceDay.toApi() =
    ApiAttendanceDay(
        date = date.toString(),
        present = present,
        source = if (source == DomainSource.EXCEPTION) ApiAttendanceSource.EXCEPTION else ApiAttendanceSource.PATTERN,
    )

fun MemberWeekAttendance.toApi() =
    ApiMemberWeekAttendance(
        memberId = memberId.value,
        cooks = cooks,
        days = days.map { it.toApi() },
        submissionDeadline = submissionDeadline?.toString(),
    )

fun WeekAttendance.toApi() =
    ApiWeekAttendance(
        weekStart = week.monday.toString(),
        orderDeadline = orderDeadline.toString(),
        members = members.map { it.toApi() },
    )
