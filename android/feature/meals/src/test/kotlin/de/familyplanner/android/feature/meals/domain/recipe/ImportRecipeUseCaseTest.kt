package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.feature.meals.fakes.CARBONARA_URL
import de.familyplanner.android.feature.meals.fakes.FakeRecipeSource
import de.familyplanner.android.feature.meals.fakes.carbonaraSource
import de.familyplanner.android.feature.meals.fakes.quesadillaSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImportRecipeUseCaseTest {
    private val source = FakeRecipeSource()
    private val import = ImportRecipeUseCase(source)

    private suspend fun input(text: String = "Lauch-Carbonara $CARBONARA_URL"): RecipeInput =
        assertIs<RecipeImportResult.Success>(import(text)).input

    @Test
    fun `REQ-MEAL-061 carbonara fills all fields`() =
        runTest {
            val input = input()
            assertEquals("Spaghetti mit Lauch-Carbonara mit & ohne Speck", input.name)
            assertEquals(4, input.servings)
            assertEquals(CARBONARA_URL, input.sourceUrl)
            assertEquals("Nudeln kochen.\nLauch in Ringe schneiden.\nAlles mischen.", input.stepsText)
            assertEquals(
                """
                500 g Vollkornspaghetti
                Salz
                2 Stange(n) Lauch
                2 Zehe(n) Knoblauch
                70 g Optional: Bio-Bacon
                2 EL Rapsöl
                200 g Sahne
                2 Eier
                Pfeffer
                Muskatnuss
                50 g Parmesan
                """.trimIndent(),
                input.ingredientsText,
            )
            assertEquals(listOf(CARBONARA_URL), source.requested)
        }

    @Test
    fun `REQ-MEAL-061 store brands are removed from quesadilla`() =
        runTest {
            source.result = SourceResult.Found(quesadillaSource)
            val lines = input(CARBONARA_URL).ingredientsText.lines()
            listOf("2 EL Rapsöl", "1 Dose(n) Kidney-Bohnen", "Saft von 1 Limette", "4 Tortilla-Wraps", "200 g Gouda gerieben", "1 Zucchini")
                .forEach { assertContains(lines, it) }
        }

    @Test
    fun `REQ-MEAL-061 text without link is reported and source untouched`() =
        runTest {
            assertEquals(RecipeImportResult.Failure(RecipeImportError.NO_LINK), import("kein Link"))
            assertEquals(emptyList(), source.requested)
        }

    @Test
    fun `REQ-MEAL-061 source failures map to errors`() =
        runTest {
            source.result = SourceResult.Unreachable
            assertEquals(RecipeImportResult.Failure(RecipeImportError.UNREACHABLE), import(CARBONARA_URL))
            source.result = SourceResult.NotFound
            assertEquals(RecipeImportResult.Failure(RecipeImportError.NO_RECIPE), import(CARBONARA_URL))
        }

    @Test
    fun `REQ-MEAL-061 servings and name are bounded`() =
        runTest {
            suspend fun servings(value: Int?) {
                source.result = SourceResult.Found(carbonaraSource.copy(servings = value))
            }
            servings(null)
            assertEquals(4, input(CARBONARA_URL).servings)
            servings(50)
            assertEquals(20, input(CARBONARA_URL).servings)
            servings(0)
            assertEquals(1, input(CARBONARA_URL).servings)
            source.result = SourceResult.Found(carbonaraSource.copy(name = "x".repeat(70)))
            assertEquals(60, input(CARBONARA_URL).name.length)
        }

    @Test
    fun `REQ-MEAL-061 imported text round trips into ingredients`() =
        runTest {
            val ingredients = ingredientsFromText(input().ingredientsText)
            assertContains(ingredients, Ingredient("Vollkornspaghetti", 500.0, "g"))
            assertContains(ingredients, Ingredient("Salz"))
        }
}
