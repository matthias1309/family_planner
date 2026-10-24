package de.familyplanner.android.feature.meals.domain.shopping

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.fakes.FakeShoppingRepository
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.shoppingEntry
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UseCasesTest {
    private val repository = FakeShoppingRepository()

    private fun validation(result: ApiResult<*>): String? = ((result as ApiResult.Failure).error as? AppError.Validation)?.message

    @Test
    fun `REQ-MEAL-041 split keeps server order and moves checked entries to done`() {
        val a = shoppingEntry("a")
        val b = shoppingEntry("b", checked = true)
        val c = shoppingEntry("c")

        val sections = splitByChecked(listOf(a, b, c))

        assertEquals(listOf(a, c), sections.open)
        assertEquals(listOf(b), sections.done)
        assertEquals(ShoppingSections(emptyList(), emptyList()), splitByChecked(emptyList()))
    }

    @Test
    fun `REQ-MEAL-040 entry display text matches ingredient format`() {
        assertEquals("625 g Vollkornspaghetti", shoppingEntry().displayText())
        assertEquals("Salz", shoppingEntry(name = "Salz", quantity = null, unit = null).displayText())
        assertEquals("12500 g Mehl", shoppingEntry(name = "Mehl", quantity = 12500.0).displayText())
    }

    @Test
    fun `REQ-MEAL-041 add item parses quantity and unit`() =
        runTest {
            AddShoppingItemUseCase(repository)(planWeek, " 2 kg Kartoffeln ", 0)

            assertEquals(listOf(planWeek to Ingredient("Kartoffeln", 2.0, "kg")), repository.added)
        }

    @Test
    fun `REQ-MEAL-041 add item keeps unparsable text as name`() =
        runTest {
            AddShoppingItemUseCase(repository)(planWeek, "Saft von 1 Limette", 0)

            assertEquals(Ingredient("Saft von 1 Limette"), repository.added.single().second)
        }

    @Test
    fun `REQ-MEAL-041 blank item is rejected without request`() =
        runTest {
            val result = AddShoppingItemUseCase(repository)(planWeek, "  ", 0)

            assertEquals("Bitte einen Eintrag eingeben.", validation(result))
            assertTrue(repository.added.isEmpty())
        }

    @Test
    fun `REQ-MEAL-041 too long item is rejected`() =
        runTest {
            val result = AddShoppingItemUseCase(repository)(planWeek, "x".repeat(81), 0)

            assertEquals("Ein Eintrag darf höchstens 80 Zeichen haben.", validation(result))
            assertTrue(repository.added.isEmpty())
        }

    @Test
    fun `REQ-MEAL-041 fifty free items are rejected without request`() =
        runTest {
            val result = AddShoppingItemUseCase(repository)(planWeek, "Brot", ShoppingLimits.FREE_ITEMS_MAX)

            assertEquals("Höchstens 50 freie Einträge.", validation(result))
            assertTrue(repository.added.isEmpty())
        }

    @Test
    fun `REQ-MEAL-041 deleting an already deleted item succeeds`() =
        runTest {
            repository.deleteResult = ApiResult.Failure(AppError.NotFound)

            assertEquals(ApiResult.Success(Unit), DeleteShoppingItemUseCase(repository)(planWeek, "i-1"))
        }

    @Test
    fun `REQ-MEAL-041 deleting without permission fails`() =
        runTest {
            repository.deleteResult = ApiResult.Failure(AppError.Forbidden)

            val result = DeleteShoppingItemUseCase(repository)(planWeek, "i-1")

            assertIs<ApiResult.Failure>(result)
            assertEquals(AppError.Forbidden, result.error)
        }
}
