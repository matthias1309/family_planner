package de.familyplanner.android.feature.meals.domain.cooking

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.meals.fakes.FakeCookingRepository
import de.familyplanner.android.feature.meals.fakes.FakeMealPlanRepository
import de.familyplanner.android.feature.meals.fakes.FakeReminderSync
import de.familyplanner.android.feature.meals.fakes.cookStatus
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.statusOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UseCasesTest {
    private val cooking = FakeCookingRepository()
    private val plan = FakeMealPlanRepository()
    private val sync = FakeReminderSync()

    @Test
    fun `REQ-MEAL-033 status joins child names`() =
        runTest {
            cooking.statusResult = { ApiResult.Success(statusOf(it, cookStatus("c-1"), cookStatus("c-1", CookState.SUBMITTED))) }

            val overview = (LoadWeekStatusUseCase(cooking, plan)(planWeek) as ApiResult.Success).data

            assertEquals(listOf("Ben", "Ben"), overview.cooks.map { it.name })
            assertEquals(planWeek, overview.weekStart)
        }

    @Test
    fun `REQ-MEAL-033 unknown child shows question mark`() =
        runTest {
            cooking.statusResult = { ApiResult.Success(statusOf(it, cookStatus("gone"))) }

            val overview = (LoadWeekStatusUseCase(cooking, plan)(planWeek) as ApiResult.Success).data

            assertEquals("?", overview.cooks.single().name)
        }

    @Test
    fun `REQ-MEAL-033 failed member request fails the status`() =
        runTest {
            plan.membersResult = ApiResult.Failure(AppError.Network)

            assertEquals(ApiResult.Failure(AppError.Network), LoadWeekStatusUseCase(cooking, plan)(planWeek))
        }

    @Test
    fun `REQ-MEAL-033 failed status request fails the status`() =
        runTest {
            cooking.statusResult = { ApiResult.Failure(AppError.Server) }

            assertEquals(ApiResult.Failure(AppError.Server), LoadWeekStatusUseCase(cooking, plan)(planWeek))
        }

    @Test
    fun `REQ-MEAL-030 approve passes the dish id`() =
        runTest {
            ReviewDishUseCase(cooking).approve("d-2")

            assertEquals(listOf("d-2"), cooking.approved)
        }

    @Test
    fun `REQ-MEAL-030 reject validates reason before sending`() =
        runTest {
            val result = ReviewDishUseCase(cooking).reject("d-2", "  ")

            assertIs<AppError.Validation>((result as ApiResult.Failure).error)
            assertTrue(cooking.rejected.isEmpty())
        }

    @Test
    fun `REQ-MEAL-030 reject sends trimmed reason`() =
        runTest {
            ReviewDishUseCase(cooking).reject("d-2", "  Mehr Gemüse ")

            assertEquals(listOf("d-2" to "Mehr Gemüse"), cooking.rejected)
        }

    @Test
    fun `REQ-MEAL-023 cancel validates and trims reason`() =
        runTest {
            val invalid = CancelCookingUseCase(cooking, sync)(planWeek, "c-1", "")
            CancelCookingUseCase(cooking, sync)(planWeek, "c-1", " krank ")

            assertIs<AppError.Validation>((invalid as ApiResult.Failure).error)
            assertEquals(listOf(Triple(planWeek, "c-1", "krank")), cooking.cancelled)
        }

    @Test
    fun `REQ-MEAL-023 withdraw passes week and member`() =
        runTest {
            WithdrawCancellationUseCase(cooking, sync)(planWeek, "c-1")

            assertEquals(listOf(planWeek to "c-1"), cooking.withdrawn)
        }

    @Test
    fun `REQ-MEAL-050 cancel and withdraw request a reminder sync only on success`() =
        runTest {
            CancelCookingUseCase(cooking, sync)(planWeek, "c-1", "krank")
            WithdrawCancellationUseCase(cooking, sync)(planWeek, "c-1")
            assertEquals(2, sync.requests)

            cooking.cancelResult = ApiResult.Failure(AppError.Network)
            CancelCookingUseCase(cooking, sync)(planWeek, "c-1", "krank")
            CancelCookingUseCase(cooking, sync)(planWeek, "c-1", "")
            assertEquals(2, sync.requests)
        }
}
