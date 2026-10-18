package de.familyplanner.android.feature.meals.domain.plan

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Clock
import java.time.LocalDate

private const val UNKNOWN_NAME = "?"

/** Verbindungsprobleme, bei denen der gespeicherte Stand gezeigt wird (ADR-0011). */
private fun AppError.allowsCachedPlan() = this == AppError.Network || this == AppError.Certificate || this == AppError.Server

class LoadWeekPlanUseCase(
    private val repository: MealPlanRepository,
    private val cache: PlanCache,
    private val sessions: SessionStore,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke(weekStart: LocalDate): WeekPlanLoad {
        val memberId = sessions.current?.memberId ?: return WeekPlanLoad.Failed(AppError.Unauthorized)
        return when (val result = loadRemote(weekStart)) {
            is ApiResult.Success -> {
                cache.write(memberId, result.data, clock.instant().atZone(BerlinZone).toLocalDate())
                WeekPlanLoad.Fresh(result.data)
            }
            is ApiResult.Failure -> {
                val cached = if (result.error.allowsCachedPlan()) cache.read(memberId, weekStart) else null
                if (cached != null) WeekPlanLoad.Cached(cached, result.error) else WeekPlanLoad.Failed(result.error)
            }
        }
    }

    private suspend fun loadRemote(weekStart: LocalDate): ApiResult<WeekPlanSnapshot> =
        coroutineScope {
            val week = async { repository.week(weekStart) }
            val members = async { repository.members() }
            val weekResult = week.await()
            val membersResult = members.await()
            if (weekResult !is ApiResult.Success) return@coroutineScope weekResult as ApiResult.Failure
            if (membersResult !is ApiResult.Success) return@coroutineScope membersResult as ApiResult.Failure
            val names = membersResult.data.associate { it.id to it.name }
            val plan = weekResult.data
            // Ein evtl. gerade gelöschtes Mitglied ist kein Fehler: Name unbekannt, Gericht trotzdem zeigen.
            val days = plan.days.map { PlannedDay(it.date, it.dish, it.dish?.let { dish -> names[dish.cookId] ?: UNKNOWN_NAME }) }
            ApiResult.Success(WeekPlanSnapshot(plan.weekStart, days, clock.instant()))
        }
}

data class DishEditorData(
    val week: WeekPlan,
    val parents: List<PlanMember>,
)

class LoadDishEditorUseCase(
    private val repository: MealPlanRepository,
) {
    suspend operator fun invoke(weekStart: LocalDate): ApiResult<DishEditorData> =
        coroutineScope {
            val week = async { repository.week(weekStart) }
            val members = async { repository.members() }
            val weekResult = week.await()
            val membersResult = members.await()
            if (weekResult !is ApiResult.Success) return@coroutineScope weekResult as ApiResult.Failure
            if (membersResult !is ApiResult.Success) return@coroutineScope membersResult as ApiResult.Failure
            ApiResult.Success(DishEditorData(weekResult.data, membersResult.data.filter { it.isParent }))
        }
}

class SaveDishUseCase(
    private val repository: MealPlanRepository,
) {
    suspend operator fun invoke(
        dishId: String?,
        date: LocalDate,
        cookId: String,
        name: String,
        ingredientsText: String,
    ): ApiResult<Dish> {
        validateDish(name, ingredientsText)?.let { return it }
        val draft = DishDraft(date, cookId, name.trim(), ingredientLines(ingredientsText))
        return if (dishId == null) repository.createDish(draft) else repository.updateDish(dishId, draft)
    }
}

class DeleteDishUseCase(
    private val repository: MealPlanRepository,
) {
    suspend operator fun invoke(dishId: String): ApiResult<Unit> = repository.deleteDish(dishId)
}

/** Bündelt die Editor-Use-Cases (hält die ViewModel-Parameterliste kurz). */
class DishUseCases(
    val load: LoadDishEditorUseCase,
    val save: SaveDishUseCase,
    val delete: DeleteDishUseCase,
)
