package de.familyplanner.android.feature.meals.domain.shopping

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import java.time.LocalDate

interface ShoppingRepository {
    suspend fun list(weekStart: LocalDate): ApiResult<ShoppingList>

    suspend fun addItem(
        weekStart: LocalDate,
        item: Ingredient,
    ): ApiResult<ShoppingList>

    suspend fun deleteItem(
        weekStart: LocalDate,
        itemId: String,
    ): ApiResult<Unit>

    suspend fun setChecked(
        weekStart: LocalDate,
        key: String,
        checked: Boolean,
    ): ApiResult<ShoppingList>
}
