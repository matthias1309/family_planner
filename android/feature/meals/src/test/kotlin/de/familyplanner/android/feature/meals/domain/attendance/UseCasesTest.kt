package de.familyplanner.android.feature.meals.domain.attendance

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.meals.fakes.FakeAttendanceRepository
import de.familyplanner.android.feature.meals.fakes.alternatingPattern
import de.familyplanner.android.feature.meals.fakes.childWeek
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.weekOf
import kotlinx.coroutines.test.runTest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UseCasesTest {
    private val repository = FakeAttendanceRepository()
    private val from = LocalDate.of(2026, 12, 20)

    @Test
    fun `REQ-MEAL-012 week overview joins attendance with member names`() =
        runTest {
            repository.weekResult = { ApiResult.Success(weekOf(childWeek("c-1"), childWeek("c-9"))) }

            val overview = (LoadWeekOverviewUseCase(repository)(planWeek) as ApiResult.Success).data

            assertEquals(listOf("Ben", "?"), overview.children.map { it.name })
            assertEquals(planWeek, overview.weekStart)
        }

    @Test
    fun `REQ-MEAL-012 week overview fails when attendance fails`() =
        runTest {
            repository.weekResult = { ApiResult.Failure(AppError.Network) }

            assertEquals(AppError.Network, (LoadWeekOverviewUseCase(repository)(planWeek) as ApiResult.Failure).error)
        }

    @Test
    fun `REQ-MEAL-012 week overview fails when member names fail`() =
        runTest {
            repository.namesResult = ApiResult.Failure(AppError.Server)

            assertEquals(AppError.Server, (LoadWeekOverviewUseCase(repository)(planWeek) as ApiResult.Failure).error)
        }

    @Test
    fun `REQ-MEAL-010 profiles are joined with names`() =
        runTest {
            val profiles = (LoadProfilesUseCase(repository)() as ApiResult.Success).data

            assertEquals(listOf("Ben", "Tim"), profiles.map { it.name })
        }

    @Test
    fun `REQ-MEAL-010 profiles fail when the profile request fails`() =
        runTest {
            repository.profilesResult = ApiResult.Failure(AppError.Forbidden)

            assertEquals(AppError.Forbidden, (LoadProfilesUseCase(repository)() as ApiResult.Failure).error)
        }

    @Test
    fun `REQ-MEAL-010 profiles fail when member names fail`() =
        runTest {
            repository.namesResult = ApiResult.Failure(AppError.Network)

            assertEquals(AppError.Network, (LoadProfilesUseCase(repository)() as ApiResult.Failure).error)
        }

    @Test
    fun `REQ-MEAL-010 invalid pattern is not sent`() =
        runTest {
            val empty = AttendanceProfile("c-1", true, AttendancePattern.Alternating(emptySet(), emptySet(), from))

            val result = SaveProfileUseCase(repository)(empty)

            assertIs<ApiResult.Failure>(result)
            assertTrue(repository.savedProfiles.isEmpty())
        }

    @Test
    fun `REQ-MEAL-010 valid profile is saved`() =
        runTest {
            val profile = AttendanceProfile("c-1", true, alternatingPattern)

            SaveProfileUseCase(repository)(profile)

            assertEquals(listOf(profile), repository.savedProfiles)
        }

    @Test
    fun `REQ-MEAL-011 invalid exception is not sent`() =
        runTest {
            val result = AddExceptionUseCase(repository)("c-1", from, from.minusDays(1), Presence.ABSENT, null)

            assertIs<ApiResult.Failure>(result)
            assertTrue(repository.addedExceptions.isEmpty())
        }

    @Test
    fun `REQ-MEAL-011 note is trimmed before sending`() =
        runTest {
            AddExceptionUseCase(repository)("c-1", from, from, Presence.PRESENT, "  Ferien  ")
            AddExceptionUseCase(repository)("c-1", from, from, Presence.PRESENT, "   ")

            assertEquals("Ferien", repository.addedExceptions[0][4])
            assertEquals(null, repository.addedExceptions[1][4])
        }

    @Test
    fun `REQ-MEAL-011 exceptions are loaded and deleted through the repository`() =
        runTest {
            assertIs<ApiResult.Success<*>>(LoadExceptionsUseCase(repository)("c-1"))
            DeleteExceptionUseCase(repository)("e-1")

            assertEquals(listOf("e-1"), repository.deletedExceptions)
        }

    @Test
    fun `REQ-MEAL-013 settings are loaded and saved through the repository`() =
        runTest {
            assertIs<ApiResult.Success<*>>(LoadDeadlineSettingsUseCase(repository)())
            SaveDeadlineSettingsUseCase(repository)(DayOfWeek.SATURDAY, LocalTime.of(18, 0), LocalTime.of(16, 0))

            assertEquals(listOf<Any>(DayOfWeek.SATURDAY, LocalTime.of(18, 0), LocalTime.of(16, 0)), repository.savedSettings.single())
        }
}
