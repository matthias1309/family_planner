package de.familyplanner.android.feature.meals.ui.dish

import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishEditorData

private const val CHILD_NOT_COOKING = "Du kochst in dieser Woche nicht."

/** Vorbelegte Köchin/Koch eines Elternteils: bestehendes Gericht, Vorgabe, man selbst oder das erste Elternteil. */
internal fun parentCookId(
    data: DishEditorData,
    existing: Dish?,
    ownId: String?,
    requestedCookId: String?,
): String? {
    val parentIds = data.parents.map { it.id }
    val selectable = parentIds + data.children.map { it.memberId }
    if (existing != null) return existing.cookId.takeIf { it in selectable }
    return requestedCookId?.takeIf { it in selectable } ?: ownId?.takeIf { it in parentIds } ?: parentIds.firstOrNull()
}

/** Ein Kind darf nur für sich selbst kochen — und nur sein eigenes Gericht ändern. */
internal fun childCookId(
    data: DishEditorData,
    existing: Dish?,
    ownId: String?,
): String? = ownId?.takeIf { id -> data.children.any { it.memberId == id } && (existing == null || existing.cookId == id) }

internal fun notCooking(
    existing: Dish?,
    ownId: String?,
): AppError = if (existing != null && existing.cookId != ownId) AppError.Forbidden else AppError.Validation(CHILD_NOT_COOKING)
