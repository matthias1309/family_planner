package de.familyplanner.meals.recipe

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.fakes.PlanFixture
import de.familyplanner.meals.fakes.lines
import de.familyplanner.meals.plan.Ingredient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecipeServiceTest {
    private val service = PlanFixture().recipes
    private val parent = MemberId("parent-1")

    private fun input(
        name: String = "Carbonara",
        ingredients: List<Ingredient> = lines("Nudeln", "Speck"),
    ) = RecipeInput(name, 4, ingredients, listOf("Kochen"), null)

    @Test
    fun `REQ-MEAL-060 parent creates recipe with validated content`() =
        runTest {
            // Act
            val recipe = service.create(parent, input(name = " Carbonara "))

            // Assert
            assertEquals("Carbonara", recipe.name)
            assertEquals(parent, recipe.createdBy)
            assertEquals(recipe, service.get(recipe.id))
        }

    @Test
    fun `REQ-MEAL-060 recipe needs at least one ingredient`() =
        runTest {
            assertFailsWith<AppException.Validation> { service.create(parent, input(ingredients = emptyList())) }
        }

    @Test
    fun `REQ-MEAL-060 duplicate name returns conflict`() =
        runTest {
            service.create(parent, input("Carbonara"))
            assertFailsWith<AppException.Conflict> { service.create(parent, input("CARBONARA")) }
        }

    @Test
    fun `REQ-MEAL-060 update keeps creator and allows same name`() =
        runTest {
            // Arrange
            val recipe = service.create(parent, input())

            // Act
            val updated = service.update(recipe.id, input(ingredients = lines("Nudeln")))

            // Assert
            assertEquals(parent, updated.createdBy)
            assertEquals(lines("Nudeln"), service.get(recipe.id).ingredients)
        }

    @Test
    fun `REQ-MEAL-060 update to name of another recipe returns conflict`() =
        runTest {
            service.create(parent, input("Carbonara"))
            val other = service.create(parent, input("Quesadilla"))
            assertFailsWith<AppException.Conflict> { service.update(other.id, input("carbonara")) }
        }

    @Test
    fun `REQ-MEAL-060 unknown recipe returns not found`() =
        runTest {
            val unknown = RecipeId("nope")
            assertFailsWith<AppException.NotFound> { service.get(unknown) }
            assertFailsWith<AppException.NotFound> { service.update(unknown, input()) }
            assertFailsWith<AppException.NotFound> { service.delete(unknown) }
        }

    @Test
    fun `REQ-MEAL-060 deleted recipe is gone`() =
        runTest {
            val recipe = service.create(parent, input())
            service.delete(recipe.id)
            assertEquals(emptyList(), service.list(null))
        }
}
