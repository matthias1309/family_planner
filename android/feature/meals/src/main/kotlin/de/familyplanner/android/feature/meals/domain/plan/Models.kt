package de.familyplanner.android.feature.meals.domain.plan

import de.familyplanner.android.core.data.AppError
import java.time.Instant
import java.time.LocalDate

enum class DishStatus { SUBMITTED, APPROVED, REJECTED }

data class Dish(
    val id: String,
    val date: LocalDate,
    val cookId: String,
    val name: String,
    val ingredients: List<String>,
    val status: DishStatus = DishStatus.APPROVED,
    val rejectionReason: String? = null,
    /** Vom Kind nach seiner Frist gespeichert (REQ-MEAL-020). */
    val late: Boolean = false,
    /** `false`: Das kochende Kind ist an dem Tag nicht (mehr) da (REQ-MEAL-021). */
    val dayValid: Boolean = true,
)

data class PlanDay(
    val date: LocalDate,
    val dish: Dish?,
)

data class WeekPlan(
    val weekStart: LocalDate,
    val days: List<PlanDay>,
)

/** Kind, das in der Woche kochen darf, mit seinen Anwesenheitstagen (REQ-MEAL-021). */
data class CookCandidate(
    val memberId: String,
    val name: String,
    val presentDays: Set<LocalDate>,
    val submissionDeadline: Instant?,
)

data class DishEditorData(
    val week: WeekPlan,
    val parents: List<PlanMember>,
    val children: List<CookCandidate>,
)

data class PlanMember(
    val id: String,
    val name: String,
    val isParent: Boolean,
)

/** Eingabe für Anlegen/Ändern eines Gerichts (bereits bereinigt). */
data class DishDraft(
    val date: LocalDate,
    val cookId: String,
    val name: String,
    val ingredients: List<String>,
)

/** Tag zur Anzeige: Gericht mit aufgelöstem Namen von Koch/Köchin („?“, wenn unbekannt). */
data class PlannedDay(
    val date: LocalDate,
    val dish: Dish?,
    val cookName: String?,
)

data class WeekPlanSnapshot(
    val weekStart: LocalDate,
    val days: List<PlannedDay>,
    val syncedAt: Instant,
)

sealed interface WeekPlanLoad {
    data class Fresh(
        val week: WeekPlanSnapshot,
    ) : WeekPlanLoad

    /** Server nicht erreichbar; gezeigt wird der zuletzt gespeicherte Stand. */
    data class Cached(
        val week: WeekPlanSnapshot,
        val error: AppError,
    ) : WeekPlanLoad

    data class Failed(
        val error: AppError,
    ) : WeekPlanLoad
}
