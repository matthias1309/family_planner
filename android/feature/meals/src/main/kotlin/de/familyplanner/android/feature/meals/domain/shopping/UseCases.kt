package de.familyplanner.android.feature.meals.domain.shopping

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.validationFailure
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.recipe.parseIngredientLine
import java.time.LocalDate

class LoadShoppingListUseCase(
    private val shopping: ShoppingRepository,
) {
    suspend operator fun invoke(weekStart: LocalDate): ApiResult<ShoppingList> = shopping.list(weekStart)
}

class AddShoppingItemUseCase(
    private val shopping: ShoppingRepository,
) {
    suspend operator fun invoke(
        weekStart: LocalDate,
        text: String,
        freeItemCount: Int,
    ): ApiResult<ShoppingList> {
        validateShoppingItem(text, freeItemCount)?.let { return it }
        return shopping.addItem(weekStart, parseIngredientLine(text.trim()))
    }
}

class SetShoppingCheckUseCase(
    private val shopping: ShoppingRepository,
) {
    suspend operator fun invoke(
        weekStart: LocalDate,
        key: String,
        checked: Boolean,
    ): ApiResult<ShoppingList> = shopping.setChecked(weekStart, key, checked)
}

class DeleteShoppingItemUseCase(
    private val shopping: ShoppingRepository,
) {
    /** Ein schon gelöschter Eintrag (404) gilt als gelöscht. */
    suspend operator fun invoke(
        weekStart: LocalDate,
        itemId: String,
    ): ApiResult<Unit> =
        when (val result = shopping.deleteItem(weekStart, itemId)) {
            is ApiResult.Failure -> if (result.error == AppError.NotFound) ApiResult.Success(Unit) else result
            is ApiResult.Success -> result
        }
}

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
