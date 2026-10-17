package de.familyplanner.meals.attendance

import de.familyplanner.core.error.AppException
import de.familyplanner.core.fakes.InMemoryMemberRepository
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.Member
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.meals.fakes.InMemoryAttendanceRepository
import de.familyplanner.meals.fakes.InMemoryDeadlineSettingsRepository
import kotlinx.coroutines.test.runTest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AttendanceServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-14T10:00:00Z"))
    private val memberRepository = InMemoryMemberRepository()
    private val service =
        AttendanceService(
            memberRepository,
            InMemoryAttendanceRepository(),
            DeadlineSettingsService(InMemoryDeadlineSettingsRepository(), clock),
            clock,
        )

    private suspend fun child(): Member = memberRepository.create(aMember(role = Role.CHILD))

    private fun date(value: String) = LocalDate.parse(value)

    private val alternating =
        AttendancePattern.Alternating(
            weekA = setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
            weekB = DayOfWeek.entries.drop(1).toSet(),
            referenceWeek = PlanWeek(date("2026-10-19")),
        )

    @Test
    fun `REQ-MEAL-010 profiles list every child with default when none saved`() =
        runTest {
            val lea = child()
            memberRepository.create(aMember(role = Role.PARENT))

            val profiles = service.profiles()

            assertEquals(listOf(AttendanceProfile.default(lea.id)), profiles)
        }

    @Test
    fun `REQ-MEAL-010 updated profile is returned in the profile list`() =
        runTest {
            val lea = child()

            service.updateProfile(lea.id, true, alternating)

            assertEquals(listOf(AttendanceProfile(lea.id, true, alternating)), service.profiles())
        }

    @Test
    fun `REQ-MEAL-010 alternating pattern without days is rejected`() =
        runTest {
            val lea = child()
            val empty = AttendancePattern.Alternating(emptySet(), emptySet(), PlanWeek(date("2026-10-19")))

            assertFailsWith<AppException.Validation> { service.updateProfile(lea.id, true, empty) }
        }

    @Test
    fun `REQ-MEAL-010 profile for parent is rejected`() =
        runTest {
            val parent = memberRepository.create(aMember(role = Role.PARENT))

            assertFailsWith<AppException.Validation> { service.updateProfile(parent.id, true, AttendancePattern.Always) }
        }

    @Test
    fun `REQ-MEAL-010 unknown member returns not found`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.updateProfile(MemberId("nobody"), true, AttendancePattern.Always) }
        }

    @Test
    fun `REQ-MEAL-011 overlapping exception is rejected, adjacent is accepted`() =
        runTest {
            val lea = child()
            service.addException(lea.id, date("2026-12-01"), date("2026-12-05"), Presence.ABSENT, null)

            assertFailsWith<AppException.Conflict> {
                service.addException(lea.id, date("2026-12-05"), date("2026-12-07"), Presence.ABSENT, null)
            }
            service.addException(lea.id, date("2026-12-06"), date("2026-12-07"), Presence.ABSENT, null)

            assertEquals(2, service.exceptions(lea.id).size)
        }

    @Test
    fun `REQ-MEAL-011 same range for another child does not conflict`() =
        runTest {
            val lea = child()
            val tom = child()
            service.addException(lea.id, date("2026-12-01"), date("2026-12-05"), Presence.ABSENT, null)

            service.addException(tom.id, date("2026-12-01"), date("2026-12-05"), Presence.ABSENT, null)

            assertEquals(1, service.exceptions(tom.id).size)
        }

    @Test
    fun `REQ-MEAL-011 exception longer than 366 days or from after to is rejected`() =
        runTest {
            val lea = child()

            assertFailsWith<AppException.Validation> {
                service.addException(lea.id, date("2026-12-05"), date("2026-12-01"), Presence.ABSENT, null)
            }
            assertFailsWith<AppException.Validation> {
                service.addException(lea.id, date("2026-01-01"), date("2027-01-02"), Presence.ABSENT, null)
            }
            service.addException(lea.id, date("2026-01-01"), date("2027-01-01"), Presence.ABSENT, null)
        }

    @Test
    fun `REQ-MEAL-011 note is trimmed, blank becomes null, 61 chars rejected`() =
        runTest {
            val lea = child()

            val trimmed = service.addException(lea.id, date("2026-12-01"), date("2026-12-01"), Presence.ABSENT, "  Ferien ")
            val blank = service.addException(lea.id, date("2026-12-02"), date("2026-12-02"), Presence.ABSENT, "   ")
            val max = service.addException(lea.id, date("2026-12-03"), date("2026-12-03"), Presence.ABSENT, "x".repeat(60))

            assertEquals("Ferien", trimmed.note)
            assertNull(blank.note)
            assertEquals(60, max.note?.length)
            assertFailsWith<AppException.Validation> {
                service.addException(lea.id, date("2026-12-04"), date("2026-12-04"), Presence.ABSENT, "x".repeat(61))
            }
        }

    @Test
    fun `REQ-MEAL-011 removing unknown exception returns not found`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.removeException(ExceptionId("missing")) }
        }

    @Test
    fun `REQ-MEAL-011 removing an exception restores the pattern`() =
        runTest {
            val lea = child()
            val exception = service.addException(lea.id, date("2026-10-20"), date("2026-10-20"), Presence.ABSENT, null)
            service.updateProfile(lea.id, true, alternating)

            val during =
                service
                    .week(PlanWeek(date("2026-10-19")))
                    .members
                    .single()
                    .days[1]
            service.removeException(exception.id)
            val after =
                service
                    .week(PlanWeek(date("2026-10-19")))
                    .members
                    .single()
                    .days[1]

            assertEquals(AttendanceSource.EXCEPTION, during.source)
            assertEquals(false, during.present)
            assertEquals(AttendanceSource.PATTERN, after.source)
            assertEquals(true, after.present)
        }

    @Test
    fun `REQ-MEAL-012 week attendance contains days and submission deadline per child`() =
        runTest {
            val lea = child()
            service.updateProfile(lea.id, true, alternating)

            val week = service.week(PlanWeek(date("2026-10-26")))

            assertEquals(Instant.parse("2026-10-25T15:00:00Z"), week.orderDeadline)
            assertEquals(Instant.parse("2026-10-22T14:00:00Z"), week.members.single().submissionDeadline)
            assertEquals(
                7,
                week.members
                    .single()
                    .days.size,
            )
        }
}
