package de.familyplanner.meals.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.meals.attendance.AttendanceException
import de.familyplanner.meals.attendance.AttendancePattern
import de.familyplanner.meals.attendance.AttendanceProfile
import de.familyplanner.meals.attendance.DeadlineSettings
import de.familyplanner.meals.attendance.ExceptionId
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.attendance.Presence
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedAttendanceRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-14T10:00:00Z")
    private lateinit var attendance: ExposedAttendanceRepository
    private lateinit var settings: ExposedDeadlineSettingsRepository
    private var memberId = MemberId("unset")

    @BeforeTest
    fun setUp() =
        runTest {
            val db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
            val member = ExposedMemberRepository(db).create(aMember())
            memberId = member.id
            attendance = ExposedAttendanceRepository(db)
            settings = ExposedDeadlineSettingsRepository(db)
        }

    private fun exception(
        id: String,
        from: String,
        to: String,
        member: MemberId = memberId,
    ) = AttendanceException(ExceptionId(id), member, LocalDate.parse(from), LocalDate.parse(to), Presence.ABSENT, "Ferien", now)

    @Test
    fun `REQ-MEAL-010 profile upsert round-trips pattern masks`() =
        runTest {
            val alternating =
                AttendancePattern.Alternating(
                    weekA = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                    weekB = DayOfWeek.entries.toSet(),
                    referenceWeek = PlanWeek(LocalDate.parse("2026-10-19")),
                )

            attendance.saveProfile(AttendanceProfile(memberId, cooks = true, pattern = alternating), now)
            val stored = attendance.findProfile(memberId)
            attendance.saveProfile(AttendanceProfile(memberId, cooks = false, pattern = AttendancePattern.Always), now)

            assertEquals(AttendanceProfile(memberId, true, alternating), stored)
            assertEquals(listOf(AttendanceProfile(memberId, false, AttendancePattern.Always)), attendance.findProfiles())
        }

    @Test
    fun `REQ-MEAL-010 unknown profile is null`() =
        runTest {
            assertNull(attendance.findProfile(MemberId("00000000-0000-0000-0000-000000000000")))
        }

    @Test
    fun `REQ-MEAL-011 overlapping exception is rejected, adjacent is accepted`() =
        runTest {
            val first = attendance.createExceptionIfNoOverlap(exception("a", "2026-12-01", "2026-12-05"))
            val overlap = attendance.createExceptionIfNoOverlap(exception("b", "2026-12-05", "2026-12-07"))
            val adjacent = attendance.createExceptionIfNoOverlap(exception("c", "2026-12-06", "2026-12-07"))

            assertEquals(listOf(true, false, true), listOf(first, overlap, adjacent))
            assertEquals(listOf("a", "c"), attendance.findExceptions(memberId).map { it.id.value })
        }

    @Test
    fun `REQ-MEAL-011 exceptions between returns touching ranges sorted and delete removes`() =
        runTest {
            attendance.createExceptionIfNoOverlap(exception("late", "2026-12-20", "2026-12-24"))
            attendance.createExceptionIfNoOverlap(exception("early", "2026-12-01", "2026-12-05"))

            val inRange = attendance.findExceptionsBetween(LocalDate.parse("2026-12-05"), LocalDate.parse("2026-12-20"))
            val deleted = attendance.deleteException(ExceptionId("early"))
            val deletedAgain = attendance.deleteException(ExceptionId("early"))

            assertEquals(listOf("early", "late"), inRange.map { it.id.value })
            assertTrue(deleted)
            assertFalse(deletedAgain)
        }

    @Test
    fun `REQ-MEAL-013 settings effective and scheduled lookups use effectiveFrom`() =
        runTest {
            fun week(date: String) = PlanWeek(LocalDate.parse(date))

            fun at(
                date: String,
                day: DayOfWeek,
            ) = DeadlineSettings(day, LocalTime.of(18, 0), LocalTime.of(12, 0), week(date))
            settings.save(at("2026-10-05", DayOfWeek.FRIDAY), now)
            settings.save(at("2026-10-26", DayOfWeek.SATURDAY), now)
            settings.save(at("2026-10-26", DayOfWeek.MONDAY), now)

            assertNull(settings.findEffective(week("2026-09-28")))
            assertEquals(DayOfWeek.FRIDAY, settings.findEffective(week("2026-10-19"))?.orderDeadlineDay)
            assertEquals(DayOfWeek.MONDAY, settings.findEffective(week("2026-11-02"))?.orderDeadlineDay)
            assertEquals(week("2026-10-26"), settings.findScheduledAfter(week("2026-10-19"))?.effectiveFrom)
            assertNull(settings.findScheduledAfter(week("2026-10-26")))
            assertEquals(LocalTime.of(12, 0), settings.findEffective(week("2026-10-19"))?.childDeadlineTime)
        }
}
