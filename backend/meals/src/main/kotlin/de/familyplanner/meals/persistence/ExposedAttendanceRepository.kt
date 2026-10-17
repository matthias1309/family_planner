package de.familyplanner.meals.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.meals.attendance.AttendanceException
import de.familyplanner.meals.attendance.AttendancePattern
import de.familyplanner.meals.attendance.AttendanceProfile
import de.familyplanner.meals.attendance.AttendanceRepository
import de.familyplanner.meals.attendance.ExceptionId
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.attendance.Presence
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.time.LocalDate

class ExposedAttendanceRepository(
    private val db: Database,
) : AttendanceRepository {
    override suspend fun findProfiles(): List<AttendanceProfile> =
        dbQuery(db) { MealAttendanceProfilesTable.selectAll().map { it.toProfile() } }

    override suspend fun findProfile(memberId: MemberId): AttendanceProfile? =
        dbQuery(db) {
            MealAttendanceProfilesTable
                .selectAll()
                .where { MealAttendanceProfilesTable.memberId eq memberId.value }
                .singleOrNull()
                ?.toProfile()
        }

    override suspend fun saveProfile(
        profile: AttendanceProfile,
        at: Instant,
    ): AttendanceProfile =
        dbQuery(db) {
            MealAttendanceProfilesTable.upsert {
                it[memberId] = profile.memberId.value
                it[cooks] = profile.cooks
                it[updatedAt] = at.toEpochMilli()
                when (val pattern = profile.pattern) {
                    AttendancePattern.Always -> {
                        it[patternType] = PATTERN_ALWAYS
                        it[weekAMask] = 0
                        it[weekBMask] = 0
                        it[referenceWeek] = null
                    }
                    is AttendancePattern.Alternating -> {
                        it[patternType] = PATTERN_ALTERNATING
                        it[weekAMask] = pattern.weekA.toMask()
                        it[weekBMask] = pattern.weekB.toMask()
                        it[referenceWeek] = pattern.referenceWeek.monday.toString()
                    }
                }
            }
            profile
        }

    override suspend fun findExceptions(memberId: MemberId): List<AttendanceException> =
        dbQuery(db) {
            MealAttendanceExceptionsTable
                .selectAll()
                .where { MealAttendanceExceptionsTable.memberId eq memberId.value }
                .orderBy(MealAttendanceExceptionsTable.fromDate)
                .map { it.toException() }
        }

    override suspend fun findExceptionsBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<AttendanceException> =
        dbQuery(db) {
            MealAttendanceExceptionsTable
                .selectAll()
                .where { overlapping(from, to) }
                .orderBy(MealAttendanceExceptionsTable.fromDate)
                .map { it.toException() }
        }

    override suspend fun createExceptionIfNoOverlap(exception: AttendanceException): Boolean =
        dbQuery(db) {
            val overlaps =
                MealAttendanceExceptionsTable
                    .selectAll()
                    .where {
                        (MealAttendanceExceptionsTable.memberId eq exception.memberId.value) and
                            overlapping(exception.from, exception.to)
                    }.any()
            if (overlaps) return@dbQuery false
            MealAttendanceExceptionsTable.insert {
                it[id] = exception.id.value
                it[memberId] = exception.memberId.value
                it[fromDate] = exception.from.toString()
                it[toDate] = exception.to.toString()
                it[presence] = exception.presence.name
                it[note] = exception.note
                it[createdAt] = exception.createdAt.toEpochMilli()
            }
            true
        }

    override suspend fun deleteException(id: ExceptionId): Boolean =
        dbQuery(db) { MealAttendanceExceptionsTable.deleteWhere { MealAttendanceExceptionsTable.id eq id.value } > 0 }

    /** Compares ISO date text; valid because the web layer only admits four-digit years (see `MIN_SUPPORTED_YEAR`). */
    private fun overlapping(
        from: LocalDate,
        to: LocalDate,
    ) = (MealAttendanceExceptionsTable.fromDate lessEq to.toString()) and (MealAttendanceExceptionsTable.toDate greaterEq from.toString())
}

private const val PATTERN_ALWAYS = "ALWAYS"
private const val PATTERN_ALTERNATING = "ALTERNATING"

internal fun ResultRow.toProfile(): AttendanceProfile {
    val pattern =
        if (this[MealAttendanceProfilesTable.patternType] == PATTERN_ALTERNATING) {
            AttendancePattern.Alternating(
                weekA = this[MealAttendanceProfilesTable.weekAMask].toDays(),
                weekB = this[MealAttendanceProfilesTable.weekBMask].toDays(),
                referenceWeek = PlanWeek(LocalDate.parse(checkNotNull(this[MealAttendanceProfilesTable.referenceWeek]))),
            )
        } else {
            AttendancePattern.Always
        }
    return AttendanceProfile(MemberId(this[MealAttendanceProfilesTable.memberId]), this[MealAttendanceProfilesTable.cooks], pattern)
}

internal fun ResultRow.toException() =
    AttendanceException(
        id = ExceptionId(this[MealAttendanceExceptionsTable.id]),
        memberId = MemberId(this[MealAttendanceExceptionsTable.memberId]),
        from = LocalDate.parse(this[MealAttendanceExceptionsTable.fromDate]),
        to = LocalDate.parse(this[MealAttendanceExceptionsTable.toDate]),
        presence = Presence.valueOf(this[MealAttendanceExceptionsTable.presence]),
        note = this[MealAttendanceExceptionsTable.note],
        createdAt = Instant.ofEpochMilli(this[MealAttendanceExceptionsTable.createdAt]),
    )
