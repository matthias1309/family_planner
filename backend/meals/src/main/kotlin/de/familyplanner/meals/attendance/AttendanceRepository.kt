package de.familyplanner.meals.attendance

import de.familyplanner.core.member.MemberId
import java.time.Instant
import java.time.LocalDate

interface AttendanceRepository {
    suspend fun findProfiles(): List<AttendanceProfile>

    suspend fun findProfile(memberId: MemberId): AttendanceProfile?

    /** Inserts or replaces the profile of its member. */
    suspend fun saveProfile(
        profile: AttendanceProfile,
        at: Instant,
    ): AttendanceProfile

    /** Exceptions of one member, sorted by start date. */
    suspend fun findExceptions(memberId: MemberId): List<AttendanceException>

    /** Exceptions of all members that touch the inclusive range [from]..[to]. */
    suspend fun findExceptionsBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<AttendanceException>

    /** Atomically inserts unless it overlaps an existing exception of the same member; returns false on overlap. */
    suspend fun createExceptionIfNoOverlap(exception: AttendanceException): Boolean

    suspend fun deleteException(id: ExceptionId): Boolean
}
