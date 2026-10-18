package de.familyplanner.android.feature.meals.domain.plan

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceDay
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceSource
import de.familyplanner.android.feature.meals.domain.attendance.RawChildWeek
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.recipe.RecipeRef
import de.familyplanner.android.feature.meals.fakes.FakeAttendanceRepository
import de.familyplanner.android.feature.meals.fakes.FakeMealPlanRepository
import de.familyplanner.android.feature.meals.fakes.InMemoryPlanCache
import de.familyplanner.android.feature.meals.fakes.benDeadline
import de.familyplanner.android.feature.meals.fakes.childDish
import de.familyplanner.android.feature.meals.fakes.childWeek
import de.familyplanner.android.feature.meals.fakes.parentAnna
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.parentTom
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.someDish
import de.familyplanner.android.feature.meals.fakes.thisWeek
import de.familyplanner.android.feature.meals.fakes.weekOf
import de.familyplanner.android.feature.meals.fakes.weekPlanOf
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UseCasesTest {
    private val repository = FakeMealPlanRepository()
    private val cache = InMemoryPlanCache()
    private val attendance = FakeAttendanceRepository()
    private val sessions = InMemorySessionStore(parentSession)

    // Samstag, 17.10.2026, 12:00 Berlin
    private val now = Instant.parse("2026-10-17T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Berlin"))
    private val loadWeekPlan = LoadWeekPlanUseCase(repository, cache, sessions, clock)

    @Test
    fun `REQ-MEAL-001 week plan joins cook names`() =
        runTest {
            val result = loadWeekPlan(thisWeek)

            val week = assertIs<WeekPlanLoad.Fresh>(result).week
            assertEquals(7, week.days.size)
            assertEquals("Anna", week.days.first().cookName)
            assertEquals(null, week.days[1].cookName)
        }

    @Test
    fun `REQ-MEAL-001 unknown cook shows question mark`() =
        runTest {
            repository.weekResult = { ApiResult.Success(weekPlanOf(it, someDish.copy(cookId = "gone"))) }

            val week = assertIs<WeekPlanLoad.Fresh>(loadWeekPlan(thisWeek)).week

            assertEquals("?", week.days.first().cookName)
        }

    @Test
    fun `REQ-MEAL-002 loaded week is written to the cache with sync time`() =
        runTest {
            val week = assertIs<WeekPlanLoad.Fresh>(loadWeekPlan(thisWeek)).week

            assertEquals(now, week.syncedAt)
            assertEquals(Triple("p-1", week, LocalDate.of(2026, 10, 17)), cache.writes.single())
        }

    @Test
    fun `REQ-MEAL-002 offline returns cached week with error`() =
        runTest {
            loadWeekPlan(thisWeek)
            repository.weekResult = { ApiResult.Failure(AppError.Network) }

            val result = assertIs<WeekPlanLoad.Cached>(loadWeekPlan(thisWeek))

            assertEquals(AppError.Network, result.error)
            assertEquals(thisWeek, result.week.weekStart)
        }

    @Test
    fun `REQ-MEAL-002 certificate and server errors also fall back to the cache`() =
        runTest {
            loadWeekPlan(thisWeek)

            listOf(AppError.Certificate, AppError.Server).forEach { error ->
                repository.weekResult = { ApiResult.Failure(error) }
                assertEquals(error, assertIs<WeekPlanLoad.Cached>(loadWeekPlan(thisWeek)).error)
            }
        }

    @Test
    fun `REQ-MEAL-002 offline without cache fails`() =
        runTest {
            repository.weekResult = { ApiResult.Failure(AppError.Network) }

            assertEquals(WeekPlanLoad.Failed(AppError.Network), loadWeekPlan(thisWeek))
        }

    @Test
    fun `REQ-MEAL-002 forbidden does not fall back to the cache`() =
        runTest {
            loadWeekPlan(thisWeek)
            repository.weekResult = { ApiResult.Failure(AppError.Forbidden) }

            assertEquals(WeekPlanLoad.Failed(AppError.Forbidden), loadWeekPlan(thisWeek))
        }

    @Test
    fun `REQ-MEAL-002 failed member request is not cached`() =
        runTest {
            repository.membersResult = ApiResult.Failure(AppError.Network)

            assertEquals(WeekPlanLoad.Failed(AppError.Network), loadWeekPlan(thisWeek))
            assertTrue(cache.writes.isEmpty())
        }

    @Test
    fun `REQ-MEAL-002 without session loading fails as unauthorized`() =
        runTest {
            val result = LoadWeekPlanUseCase(repository, cache, InMemorySessionStore(), clock)(thisWeek)

            assertEquals(WeekPlanLoad.Failed(AppError.Unauthorized), result)
        }

    @Test
    fun `REQ-MEAL-031 editor offers parents and cooking children`() =
        runTest {
            val data = (LoadDishEditorUseCase(repository, attendance)(planWeek) as ApiResult.Success).data

            assertEquals(listOf(parentAnna, parentTom), data.parents)
            assertEquals(planWeek, data.week.weekStart)
            assertEquals(listOf("c-1"), data.children.map { it.memberId })
        }

    @Test
    fun `REQ-MEAL-021 editor lists cooking children with present days`() =
        runTest {
            attendance.weekResult = { ApiResult.Success(weekOf(childWeek(present = true), start = it)) }

            val child = (LoadDishEditorUseCase(repository, attendance)(planWeek) as ApiResult.Success).data.children.single()

            assertEquals("Ben", child.name)
            assertEquals((0L..6L).map { planWeek.plusDays(it) }.toSet(), child.presentDays)
            assertEquals(benDeadline, child.submissionDeadline)
        }

    @Test
    fun `REQ-MEAL-021 child that does not cook is no candidate`() =
        runTest {
            attendance.weekResult = { ApiResult.Success(weekOf(childWeek(cooks = false, deadline = null), start = it)) }

            val data = (LoadDishEditorUseCase(repository, attendance)(planWeek) as ApiResult.Success).data

            assertTrue(data.children.isEmpty())
        }

    @Test
    fun `REQ-MEAL-021 owner of an invalid dish stays a candidate`() =
        runTest {
            attendance.weekResult = { ApiResult.Success(weekOf(childWeek(cooks = false, deadline = null, present = false), start = it)) }
            repository.weekResult = { ApiResult.Success(weekPlanOf(it, childDish.copy(dayValid = false))) }

            val child = (LoadDishEditorUseCase(repository, attendance)(planWeek) as ApiResult.Success).data.children.single()

            assertEquals("c-1", child.memberId)
            assertTrue(child.presentDays.isEmpty())
        }

    @Test
    fun `REQ-MEAL-020 failed attendance request fails the editor`() =
        runTest {
            attendance.weekResult = { ApiResult.Failure(AppError.Network) }

            assertEquals(ApiResult.Failure(AppError.Network), LoadDishEditorUseCase(repository, attendance)(planWeek))
        }

    @Test
    fun `REQ-MEAL-020 child dish requires ingredients when saving`() =
        runTest {
            val result = SaveDishUseCase(repository)(null, DishInput(planWeek, "c-1", "Curry", ""), ingredientsRequired = true)

            assertIs<AppError.Validation>((result as ApiResult.Failure).error)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun `REQ-MEAL-032 editor load fails when members fail`() =
        runTest {
            repository.membersResult = ApiResult.Failure(AppError.Network)

            assertEquals(ApiResult.Failure(AppError.Network), LoadDishEditorUseCase(repository, attendance)(thisWeek))
        }

    @Test
    fun `REQ-MEAL-032 invalid dish is not sent`() =
        runTest {
            val result = SaveDishUseCase(repository)(null, DishInput(thisWeek, "p-1", " ", ""))

            assertIs<AppError.Validation>((result as ApiResult.Failure).error)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun `REQ-MEAL-032 new dish is created with trimmed name and cleaned ingredients`() =
        runTest {
            SaveDishUseCase(repository)(null, DishInput(thisWeek, "p-1", "  Curry ", " Reis \n\n Erbsen "))

            assertEquals(DishDraft(thisWeek, "p-1", "Curry", listOf(Ingredient("Reis"), Ingredient("Erbsen"))), repository.created.single())
        }

    @Test
    fun `REQ-MEAL-062 save sends parsed ingredients and recipe ref`() =
        runTest {
            SaveDishUseCase(repository)(null, DishInput(thisWeek, "p-1", "Carbonara", "625 g Spaghetti\nSalz", RecipeRef("r-1", 5)))

            val draft = repository.created.single()
            assertEquals(listOf(Ingredient("Spaghetti", 625.0, "g"), Ingredient("Salz")), draft.ingredients)
            assertEquals(RecipeRef("r-1", 5), draft.recipe)
        }

    @Test
    fun `REQ-MEAL-062 editor counts parents and present children per day`() =
        runTest {
            val days =
                (0L..6L).map { AttendanceDay(planWeek.plusDays(it), it in 1L..3L, AttendanceSource.PATTERN) }
            attendance.weekResult =
                { ApiResult.Success(weekOf(RawChildWeek("c-1", true, days, Instant.parse("2026-10-15T14:00:00Z")), start = it)) }

            val data = (LoadDishEditorUseCase(repository, attendance)(planWeek) as ApiResult.Success).data

            assertEquals(2, data.presentByDate[planWeek])
            assertEquals(3, data.presentByDate[planWeek.plusDays(1)])
            assertEquals(3, data.presentByDate[planWeek.plusDays(3)])
            assertEquals(2, data.presentByDate[planWeek.plusDays(4)])
        }

    @Test
    fun `REQ-MEAL-032 existing dish is updated`() =
        runTest {
            SaveDishUseCase(repository)("d-1", DishInput(thisWeek.plusDays(1), "p-2", "Curry", ""))

            assertEquals("d-1" to DishDraft(thisWeek.plusDays(1), "p-2", "Curry", emptyList()), repository.updated.single())
        }

    @Test
    fun `REQ-MEAL-032 delete passes the dish id`() =
        runTest {
            DeleteDishUseCase(repository)("d-1")

            assertEquals(listOf("d-1"), repository.deleted)
        }
}
