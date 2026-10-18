package de.familyplanner.meals.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.meals.fakes.lines
import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.Ingredient
import de.familyplanner.meals.plan.RecipeRef
import de.familyplanner.meals.recipe.Recipe
import de.familyplanner.meals.recipe.RecipeId
import de.familyplanner.meals.recipe.RecipeSaveOutcome
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedRecipeRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-14T10:00:00Z")
    private lateinit var db: Database
    private lateinit var repository: ExposedRecipeRepository
    private var parentId = MemberId("unset")

    @BeforeTest
    fun setUp() =
        runTest {
            db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
            parentId = ExposedMemberRepository(db).create(aMember(role = Role.PARENT)).id
            repository = ExposedRecipeRepository(db)
        }

    private fun recipe(
        id: String,
        name: String = "Carbonara",
        ingredients: List<Ingredient> =
            listOf(Ingredient("Spaghetti", 500.0, "g"), Ingredient("Salz"), Ingredient("Lauch", 2.5, "Stange(n)")),
    ) = Recipe(RecipeId(id), name, 4, ingredients, listOf("Kochen", "Mischen"), "https://www.rewe.de/rezepte/x/", parentId)

    @Test
    fun `REQ-MEAL-060 recipe roundtrip keeps ingredient and step order`() =
        runTest {
            // Arrange
            val recipe = recipe("r1")

            // Act
            val outcome = repository.createIfNameFree(recipe, now)

            // Assert
            assertEquals(RecipeSaveOutcome.SAVED, outcome)
            assertEquals(recipe, repository.findById(RecipeId("r1")))
            assertTrue(repository.exists(RecipeId("r1")))
            assertEquals(listOf("Carbonara"), repository.findAllSummaries().map { it.name })
        }

    @Test
    fun `REQ-MEAL-060 name taken ignoring case`() =
        runTest {
            // Arrange
            repository.createIfNameFree(recipe("r1", "Äpfel"), now)
            repository.createIfNameFree(recipe("r2", "Suppe"), now)

            // Act
            val duplicate = repository.createIfNameFree(recipe("r3", "äPFEL"), now)
            val rename = repository.updateIfNameFree(recipe("r2", "ÄPFEL"), now)
            val own = repository.updateIfNameFree(recipe("r1", "Äpfel", ingredients = lines("Apfel")), now)

            // Assert
            assertEquals(RecipeSaveOutcome.NAME_TAKEN, duplicate)
            assertEquals(RecipeSaveOutcome.NAME_TAKEN, rename)
            assertEquals(RecipeSaveOutcome.SAVED, own)
            assertEquals(lines("Apfel"), repository.findById(RecipeId("r1"))?.ingredients)
        }

    @Test
    fun `REQ-MEAL-060 update of unknown recipe is not found`() =
        runTest {
            assertEquals(RecipeSaveOutcome.NOT_FOUND, repository.updateIfNameFree(recipe("nope"), now))
            assertFalse(repository.delete(RecipeId("nope")))
        }

    @Test
    fun `REQ-MEAL-060 deleting recipe clears reference on dishes`() =
        runTest {
            // Arrange
            repository.createIfNameFree(recipe("r1"), now)
            val dishRepository = ExposedMealPlanRepository(db)
            val ingredients = lines("Nudeln", "Salz")
            val dish =
                Dish(DishId("d1"), LocalDate.parse("2026-10-20"), parentId, "Carbonara", ingredients, recipe = RecipeRef(RecipeId("r1"), 5))
            dishRepository.createIfDayFree(dish, now)

            // Act
            val deleted = repository.delete(RecipeId("r1"))

            // Assert
            val kept = dishRepository.findById(DishId("d1"))
            assertTrue(deleted)
            assertNull(repository.findById(RecipeId("r1")))
            assertNull(kept?.recipe)
            assertEquals(ingredients, kept?.ingredients)
        }
}
