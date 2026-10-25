package de.familyplanner.android.feature.meals.domain.plan

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceRepository
import de.familyplanner.android.feature.meals.domain.cooking.ReviewDishUseCase
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.ingredientsFromText
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSync
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
            val childIds =
                membersResult.data
                    .filterNot { it.isParent }
                    .map { it.id }
                    .toSet()
            val plan = weekResult.data
            // Ein evtl. gerade gelöschtes Mitglied ist kein Fehler: Name unbekannt, Gericht trotzdem zeigen.
            val days =
                plan.days.map {
                    PlannedDay(
                        it.date,
                        it.dish,
                        it.dish?.let { dish -> names[dish.cookId] ?: UNKNOWN_NAME },
                        cookIsChild = it.dish?.cookId in childIds,
                    )
                }
            ApiResult.Success(WeekPlanSnapshot(plan.weekStart, days, clock.instant()))
        }
}

class LoadDishEditorUseCase(
    private val repository: MealPlanRepository,
    private val attendance: AttendanceRepository,
) {
    suspend operator fun invoke(weekStart: LocalDate): ApiResult<DishEditorData> =
        coroutineScope {
            val week = async { repository.week(weekStart) }
            val members = async { repository.members() }
            val presence = async { attendance.week(weekStart) }
            val weekResult = week.await()
            val membersResult = members.await()
            val presenceResult = presence.await()
            if (weekResult !is ApiResult.Success) return@coroutineScope weekResult as ApiResult.Failure
            if (membersResult !is ApiResult.Success) return@coroutineScope membersResult as ApiResult.Failure
            if (presenceResult !is ApiResult.Success) return@coroutineScope presenceResult as ApiResult.Failure
            val names = membersResult.data.associate { it.id to it.name }
            // Kocht in der Woche — oder besitzt dort noch ein Gericht (z. B. mit ungültigem Tag, REQ-MEAL-021).
            val owners =
                weekResult.data.days
                    .mapNotNull { it.dish?.cookId }
                    .toSet()
            val children =
                presenceResult.data.children
                    .filter { (it.cooks && it.submissionDeadline != null) || it.memberId in owners }
                    .map { child ->
                        val present =
                            child.days
                                .filter { it.present }
                                .map { it.date }
                                .toSet()
                        CookCandidate(child.memberId, names[child.memberId] ?: UNKNOWN_NAME, present, child.submissionDeadline)
                    }
            val parents = membersResult.data.filter { it.isParent }
            val presentByDate =
                weekResult.data.days.associate { day ->
                    day.date to
                        parents.size + presenceResult.data.children.count { child -> child.days.any { it.date == day.date && it.present } }
                }
            ApiResult.Success(DishEditorData(weekResult.data, parents, children, presentByDate))
        }
}

class SaveDishUseCase(
    private val repository: MealPlanRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(
        dishId: String?,
        input: DishInput,
        ingredientsRequired: Boolean = false,
    ): ApiResult<Dish> {
        validateDish(input.name, input.ingredientsText, ingredientsRequired)?.let { return it }
        val draft = DishDraft(input.date, input.cookId, input.name.trim(), ingredientsFromText(input.ingredientsText), input.recipe)
        val result = if (dishId == null) repository.createDish(draft) else repository.updateDish(dishId, draft)
        if (result is ApiResult.Success) reminders.requestNow()
        return result
    }
}

class DeleteDishUseCase(
    private val repository: MealPlanRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(dishId: String): ApiResult<Unit> =
        repository.deleteDish(dishId).also { if (it is ApiResult.Success) reminders.requestNow() }
}

/** Bündelt die Editor-Use-Cases (hält die ViewModel-Parameterliste kurz). */
class DishUseCases(
    val load: LoadDishEditorUseCase,
    val save: SaveDishUseCase,
    val delete: DeleteDishUseCase,
    val review: ReviewDishUseCase,
    val recipe: LoadRecipeUseCase,
)
