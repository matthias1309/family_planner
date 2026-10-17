package de.familyplanner.meals.attendance

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

const val EXCEPTION_NOTE_MAX = 60
const val EXCEPTION_MAX_DAYS = 366L

data class MemberWeekAttendance(
    val memberId: MemberId,
    val cooks: Boolean,
    val days: List<AttendanceDay>,
    val submissionDeadline: Instant?,
)

data class WeekAttendance(
    val week: PlanWeek,
    val orderDeadline: Instant,
    val members: List<MemberWeekAttendance>,
)

class AttendanceService(
    private val members: MemberRepository,
    private val attendance: AttendanceRepository,
    private val settings: DeadlineSettingsService,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(AttendanceService::class.java)

    /** Profiles of all children in member order; children without a saved profile get the default. */
    suspend fun profiles(): List<AttendanceProfile> {
        val children = members.findAll().filter { it.role == Role.CHILD }
        val saved = attendance.findProfiles().associateBy { it.memberId }
        return children.map { saved[it.id] ?: AttendanceProfile.default(it.id) }
    }

    suspend fun updateProfile(
        memberId: MemberId,
        cooks: Boolean,
        pattern: AttendancePattern,
    ): AttendanceProfile {
        requireChild(memberId)
        validate(pattern)
        val saved = attendance.saveProfile(AttendanceProfile(memberId, cooks, pattern), clock.instant())
        log.info("Attendance profile saved: {}", memberId.value)
        return saved
    }

    suspend fun exceptions(memberId: MemberId): List<AttendanceException> {
        requireChild(memberId)
        return attendance.findExceptions(memberId)
    }

    suspend fun addException(
        memberId: MemberId,
        from: LocalDate,
        to: LocalDate,
        presence: Presence,
        note: String?,
    ): AttendanceException {
        requireChild(memberId)
        validateRange(from, to)
        val exception = AttendanceException(ExceptionId(newId()), memberId, from, to, presence, validatedNote(note), clock.instant())
        if (!attendance.createExceptionIfNoOverlap(exception)) {
            throw AppException.Conflict("Zeitraum überschneidet sich mit einer bestehenden Ausnahme")
        }
        log.info("Attendance exception created: {}", exception.id.value)
        return exception
    }

    suspend fun removeException(id: ExceptionId) {
        if (!attendance.deleteException(id)) throw AppException.NotFound("Ausnahme nicht gefunden")
        log.info("Attendance exception removed: {}", id.value)
    }

    suspend fun week(week: PlanWeek): WeekAttendance {
        val profiles = profiles()
        val exceptions = attendance.findExceptionsBetween(week.previous().monday, week.days.last())
        val deadlineSettings = settings.effectiveFor(week)
        val perMember =
            profiles.map { profile ->
                val own = exceptions.filter { it.memberId == profile.memberId }
                MemberWeekAttendance(
                    memberId = profile.memberId,
                    cooks = profile.cooks,
                    days = attendanceFor(week, profile, own),
                    submissionDeadline = submissionDeadline(week, deadlineSettings, profile, own),
                )
            }
        return WeekAttendance(week, orderDeadline(week, deadlineSettings), perMember)
    }

    private suspend fun requireChild(memberId: MemberId) {
        val member = members.findById(memberId) ?: throw AppException.NotFound("Mitglied nicht gefunden")
        if (member.role != Role.CHILD) throw AppException.Validation("Anwesenheit gilt nur für Kinder")
    }

    private fun validate(pattern: AttendancePattern) {
        if (pattern is AttendancePattern.Alternating && (pattern.weekA + pattern.weekB).isEmpty()) {
            throw AppException.Validation("Das Muster braucht mindestens einen Anwesenheitstag")
        }
    }

    private fun validateRange(
        from: LocalDate,
        to: LocalDate,
    ) {
        if (to.isBefore(from)) throw AppException.Validation("Das Ende darf nicht vor dem Beginn liegen")
        if (ChronoUnit.DAYS.between(from, to) + 1 > EXCEPTION_MAX_DAYS) {
            throw AppException.Validation("Eine Ausnahme darf höchstens $EXCEPTION_MAX_DAYS Tage dauern")
        }
    }

    private fun validatedNote(note: String?): String? {
        val trimmed = note?.trim().orEmpty()
        if (trimmed.length > EXCEPTION_NOTE_MAX) {
            throw AppException.Validation("Die Notiz darf höchstens $EXCEPTION_NOTE_MAX Zeichen lang sein")
        }
        return trimmed.ifEmpty { null }
    }
}
