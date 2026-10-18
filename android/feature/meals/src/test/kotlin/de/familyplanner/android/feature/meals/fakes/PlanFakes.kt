package de.familyplanner.android.feature.meals.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishDraft
import de.familyplanner.android.feature.meals.domain.plan.MealPlanRepository
import de.familyplanner.android.feature.meals.domain.plan.PlanCache
import de.familyplanner.android.feature.meals.domain.plan.PlanDay
import de.familyplanner.android.feature.meals.domain.plan.PlanMember
import de.familyplanner.android.feature.meals.domain.plan.WeekPlan
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import de.familyplanner.android.feature.meals.domain.recipe.displayText
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate

val thisWeek: LocalDate = LocalDate.of(2026, 10, 12)
val parentAnna = PlanMember("p-1", "Anna", true)
val parentTom = PlanMember("p-2", "Tom", true)
val childBen = PlanMember("c-1", "Ben", false)
val someDish = Dish("d-1", LocalDate.of(2026, 10, 12), "p-1", "Spaghetti", listOf("Nudeln", "Hackfleisch"))

fun weekPlanOf(
    start: LocalDate,
    vararg dishes: Dish,
) = WeekPlan(start, (0L..6L).map { offset -> start.plusDays(offset).let { day -> PlanDay(day, dishes.firstOrNull { it.date == day }) } })

class FakeMealPlanRepository : MealPlanRepository {
    var weekResult: (LocalDate) -> ApiResult<WeekPlan> = { ApiResult.Success(weekPlanOf(it, someDish.copy(date = it))) }
    var membersResult: ApiResult<List<PlanMember>> = ApiResult.Success(listOf(parentAnna, parentTom, childBen))
    var createResult: ApiResult<Dish>? = null
    var updateResult: ApiResult<Dish>? = null
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val requestedWeeks = mutableListOf<LocalDate>()
    val created = mutableListOf<DishDraft>()
    val updated = mutableListOf<Pair<String, DishDraft>>()
    val deleted = mutableListOf<String>()

    /** Hält die Antwort für eine Woche zurück, bis das Gate geöffnet wird (Wochenwechsel-Rennen). */
    val weekGates = mutableMapOf<LocalDate, CompletableDeferred<Unit>>()

    /** Hält das Speichern zurück (Doppelklick-Test). */
    var createGate: CompletableDeferred<Unit>? = null

    override suspend fun week(weekStart: LocalDate): ApiResult<WeekPlan> {
        requestedWeeks += weekStart
        weekGates[weekStart]?.await()
        return weekResult(weekStart)
    }

    override suspend fun members() = membersResult

    override suspend fun createDish(draft: DishDraft): ApiResult<Dish> {
        created += draft
        createGate?.await()
        return createResult ?: ApiResult.Success(draft.toDish("d-new"))
    }

    override suspend fun updateDish(
        dishId: String,
        draft: DishDraft,
    ): ApiResult<Dish> {
        updated += dishId to draft
        return updateResult ?: ApiResult.Success(draft.toDish(dishId))
    }

    override suspend fun deleteDish(dishId: String): ApiResult<Unit> {
        deleted += dishId
        return deleteResult
    }
}

private fun DishDraft.toDish(id: String) =
    Dish(id, date, cookId, name, ingredients.map { it.displayText() }, recipeId = recipe?.recipeId, servings = recipe?.servings)

class InMemoryPlanCache : PlanCache {
    val stored = mutableMapOf<Pair<String, LocalDate>, WeekPlanSnapshot>()
    val writes = mutableListOf<Triple<String, WeekPlanSnapshot, LocalDate>>()

    override suspend fun read(
        memberId: String,
        weekStart: LocalDate,
    ) = stored[memberId to weekStart]

    override suspend fun write(
        memberId: String,
        week: WeekPlanSnapshot,
        today: LocalDate,
    ) {
        writes += Triple(memberId, week, today)
        stored[memberId to week.weekStart] = week
    }
}
