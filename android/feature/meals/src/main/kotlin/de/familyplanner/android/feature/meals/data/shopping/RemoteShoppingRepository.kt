package de.familyplanner.android.feature.meals.data.shopping

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.apiCallNoContent
import de.familyplanner.android.feature.meals.data.recipe.toApi
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingList
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingRepository
import de.familyplanner.api.client.MealsApi
import de.familyplanner.api.model.CheckShoppingEntryRequest
import kotlinx.serialization.json.Json
import java.time.LocalDate

class RemoteShoppingRepository(
    private val meals: MealsApi,
    private val json: Json,
) : ShoppingRepository {
    override suspend fun list(weekStart: LocalDate): ApiResult<ShoppingList> =
        apiCall(json, { meals.getShoppingList(weekStart.toString()) }) { it.data?.toDomain() }

    /** Antwortet mit 201 und Body. */
    override suspend fun addItem(
        weekStart: LocalDate,
        item: Ingredient,
    ): ApiResult<ShoppingList> = apiCall(json, { meals.addShoppingItem(weekStart.toString(), item.toApi()) }) { it.data?.toDomain() }

    override suspend fun deleteItem(
        weekStart: LocalDate,
        itemId: String,
    ): ApiResult<Unit> = apiCallNoContent(json) { meals.deleteShoppingItem(weekStart.toString(), itemId) }

    override suspend fun setChecked(
        weekStart: LocalDate,
        key: String,
        checked: Boolean,
    ): ApiResult<ShoppingList> =
        apiCall(json, { meals.setShoppingEntryChecked(weekStart.toString(), CheckShoppingEntryRequest(key, checked)) }) {
            it.data?.toDomain()
        }
}
