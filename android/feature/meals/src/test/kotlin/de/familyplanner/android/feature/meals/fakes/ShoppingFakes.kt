package de.familyplanner.android.feature.meals.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingEntry
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingList
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingRepository
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingSource
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate

val carbonaraShoppingSource = ShoppingSource("d-1", "Carbonara", LocalDate.of(2026, 10, 20))
val curryThursdaySource = ShoppingSource("d-2", "Curry", LocalDate.of(2026, 10, 22))

fun shoppingEntry(
    key: String = "k-1",
    name: String = "Vollkornspaghetti",
    quantity: Double? = 625.0,
    unit: String? = "g",
    checked: Boolean = false,
    itemId: String? = null,
    sources: List<ShoppingSource> = listOf(carbonaraShoppingSource),
) = ShoppingEntry(key, name, quantity, unit, checked, itemId, sources)

val freeEntry = shoppingEntry("item:i-1", "Kaffee", 1.0, "Packung", itemId = "i-1", sources = emptyList())

fun shoppingListOf(
    vararg entries: ShoppingEntry,
    weekStart: LocalDate = planWeek,
) = ShoppingList(weekStart, entries.toList())

class FakeShoppingRepository : ShoppingRepository {
    var listResult: (LocalDate) -> ApiResult<ShoppingList> = { ApiResult.Success(shoppingListOf(shoppingEntry(), weekStart = it)) }
    var addResult: ApiResult<ShoppingList> = ApiResult.Success(shoppingListOf(shoppingEntry()))
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var checkResult: ApiResult<ShoppingList> = ApiResult.Success(shoppingListOf(shoppingEntry(checked = true)))

    val requestedWeeks = mutableListOf<LocalDate>()
    val added = mutableListOf<Pair<LocalDate, Ingredient>>()
    val deleted = mutableListOf<Pair<LocalDate, String>>()
    val checks = mutableListOf<Triple<LocalDate, String, Boolean>>()

    /** Hält die Listenantwort einer Woche zurück (Wochenwechsel-Rennen). */
    val listGates = mutableMapOf<LocalDate, CompletableDeferred<Unit>>()

    /** Hält Abhaken zurück (Doppelklick-Tests). */
    var checkGate: CompletableDeferred<Unit>? = null

    override suspend fun list(weekStart: LocalDate): ApiResult<ShoppingList> {
        requestedWeeks += weekStart
        listGates[weekStart]?.await()
        return listResult(weekStart)
    }

    override suspend fun addItem(
        weekStart: LocalDate,
        item: Ingredient,
    ): ApiResult<ShoppingList> {
        added += weekStart to item
        return addResult
    }

    override suspend fun deleteItem(
        weekStart: LocalDate,
        itemId: String,
    ): ApiResult<Unit> {
        deleted += weekStart to itemId
        return deleteResult
    }

    override suspend fun setChecked(
        weekStart: LocalDate,
        key: String,
        checked: Boolean,
    ): ApiResult<ShoppingList> {
        checks += Triple(weekStart, key, checked)
        checkGate?.await()
        return checkResult
    }
}
