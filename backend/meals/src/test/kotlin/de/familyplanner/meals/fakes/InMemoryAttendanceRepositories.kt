package de.familyplanner.meals.fakes

import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.AttendanceException
import de.familyplanner.meals.attendance.AttendanceProfile
import de.familyplanner.meals.attendance.AttendanceRepository
import de.familyplanner.meals.attendance.DeadlineSettings
import de.familyplanner.meals.attendance.DeadlineSettingsRepository
import de.familyplanner.meals.attendance.ExceptionId
import de.familyplanner.meals.attendance.PlanWeek
import java.time.Instant
import java.time.LocalDate

class InMemoryAttendanceRepository : AttendanceRepository {
    private val profiles = mutableMapOf<MemberId, AttendanceProfile>()
    private val exceptions = mutableListOf<AttendanceException>()

    override suspend fun findProfiles(): List<AttendanceProfile> = profiles.values.toList()

    override suspend fun findProfile(memberId: MemberId): AttendanceProfile? = profiles[memberId]

    override suspend fun saveProfile(
        profile: AttendanceProfile,
        at: Instant,
    ): AttendanceProfile = profile.also { profiles[it.memberId] = it }

    override suspend fun findExceptions(memberId: MemberId): List<AttendanceException> =
        exceptions.filter { it.memberId == memberId }.sortedBy { it.from }

    override suspend fun findExceptionsBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<AttendanceException> = exceptions.filter { !it.from.isAfter(to) && !it.to.isBefore(from) }.sortedBy { it.from }

    override suspend fun createExceptionIfNoOverlap(exception: AttendanceException): Boolean {
        if (exceptions.any { it.memberId == exception.memberId && it.overlaps(exception) }) return false
        exceptions += exception
        return true
    }

    override suspend fun deleteException(id: ExceptionId): Boolean = exceptions.removeAll { it.id == id }
}

class InMemoryDeadlineSettingsRepository : DeadlineSettingsRepository {
    private val settings = mutableMapOf<PlanWeek, DeadlineSettings>()

    val all: List<DeadlineSettings> get() = settings.values.toList()

    override suspend fun findEffective(week: PlanWeek): DeadlineSettings? =
        settings.values.filter { !it.effectiveFrom.monday.isAfter(week.monday) }.maxByOrNull { it.effectiveFrom.monday }

    override suspend fun findScheduledAfter(week: PlanWeek): DeadlineSettings? =
        settings.values.filter { it.effectiveFrom.monday.isAfter(week.monday) }.minByOrNull { it.effectiveFrom.monday }

    override suspend fun save(
        settings: DeadlineSettings,
        at: Instant,
    ) {
        this.settings[settings.effectiveFrom] = settings
    }
}
