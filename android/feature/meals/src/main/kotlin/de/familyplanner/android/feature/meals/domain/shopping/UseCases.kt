package de.familyplanner.android.feature.meals.domain.shopping

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.validationFailure
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.recipe.parseIngredientLine
import java.time.LocalDate

/** Lädt die Liste einer Woche und merkt sie sich für das nächste Öffnen (ADR-0018). */
class LoadShoppingListUseCase(
    private val shopping: ShoppingRepository,
    private val cache: ShoppingListCache,
) {
    /** Zuletzt geladener Stand der Woche, sonst `null`. */
    fun cached(weekStart: LocalDate): ShoppingList? = cache.read(weekStart)

    suspend operator fun invoke(weekStart: LocalDate): ApiResult<ShoppingList> {
        // Vor der Anfrage: Mitglied und letzte eigene Änderung festhalten (Mitgliederwechsel, Abhaken während des Ladens).
        val ticket = cache.ticket()
        return shopping.list(weekStart).also { if (it is ApiResult.Success) cache.write(ticket, it.data) }
    }
}

class AddShoppingItemUseCase(
    private val shopping: ShoppingRepository,
    private val cache: ShoppingListCache,
) {
    suspend operator fun invoke(
        weekStart: LocalDate,
        text: String,
        freeItemCount: Int,
    ): ApiResult<ShoppingList> {
        validateShoppingItem(text, freeItemCount)?.let { return it }
        return shopping.addItem(weekStart, parseIngredientLine(text.trim())).also { cache.keep(weekStart, it) }
    }
}

class SetShoppingCheckUseCase(
    private val shopping: ShoppingRepository,
    private val cache: ShoppingListCache,
) {
    suspend operator fun invoke(
        weekStart: LocalDate,
        key: String,
        checked: Boolean,
    ): ApiResult<ShoppingList> = shopping.setChecked(weekStart, key, checked).also { cache.keep(weekStart, it) }
}

class DeleteShoppingItemUseCase(
    private val shopping: ShoppingRepository,
    private val cache: ShoppingListCache,
) {
    /** Ein schon gelöschter Eintrag (404) gilt als gelöscht. */
    suspend operator fun invoke(
        weekStart: LocalDate,
        itemId: String,
    ): ApiResult<Unit> =
        when (val result = shopping.deleteItem(weekStart, itemId)) {
            is ApiResult.Failure -> if (result.error == AppError.NotFound) ApiResult.Success(Unit) else result
            is ApiResult.Success -> result
        }.also { if (it is ApiResult.Success) cache.update(weekStart) { list -> list.withoutItem(itemId) } }
}

/** Schreibvorgänge liefern die ganze Liste; sie ist der neueste Stand. */
private fun ShoppingListCache.keep(
    weekStart: LocalDate,
    result: ApiResult<ShoppingList>,
) {
    if (result is ApiResult.Success) update(weekStart) { result.data }
}

private fun ShoppingList.withoutItem(itemId: String) = copy(entries = entries.filterNot { it.itemId == itemId })

class ShoppingUseCases(
    val load: LoadShoppingListUseCase,
    val add: AddShoppingItemUseCase,
    val check: SetShoppingCheckUseCase,
    val delete: DeleteShoppingItemUseCase,
)

internal fun validateShoppingItem(
    text: String,
    freeItemCount: Int,
): ApiResult.Failure? =
    when {
        text.isBlank() -> validationFailure("Bitte einen Eintrag eingeben.")
        text.trim().length > PlanLimits.INGREDIENT_MAX ->
            validationFailure("Ein Eintrag darf höchstens ${PlanLimits.INGREDIENT_MAX} Zeichen haben.")
        freeItemCount >= ShoppingLimits.FREE_ITEMS_MAX ->
            validationFailure("Höchstens ${ShoppingLimits.FREE_ITEMS_MAX} freie Einträge.")
        else -> null
    }
