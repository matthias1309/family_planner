package de.familyplanner.android.feature.meals.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.domain.recipe.RecipeImportError
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditEvent
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditScreen
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RecipeImportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showEdit(
        state: RecipeEditUiState,
        onEvent: (RecipeEditEvent) -> Unit = {},
    ) = compose.setContent { FamilyPlannerTheme { RecipeEditScreen(state, onEvent, {}) } }

    @Test
    fun `REQ-MEAL-061 new recipe shows link field and disabled import button without link`() {
        showEdit(RecipeEditUiState(recipeId = null))

        compose.onNodeWithText("Link zum Rezept (optional, z. B. von rewe.de)").assertExists()
        compose.onNodeWithText("Übernehmen").assertIsNotEnabled()
    }

    @Test
    fun `REQ-MEAL-061 import button sends the event`() {
        val events = mutableListOf<RecipeEditEvent>()
        showEdit(RecipeEditUiState(recipeId = null, sourceUrl = "https://www.rewe.de/rezepte/x/"), events::add)

        compose.onNodeWithText("Übernehmen").assertIsEnabled().performClick()

        assertEquals(listOf<RecipeEditEvent>(RecipeEditEvent.Import), events)
    }

    @Test
    fun `REQ-MEAL-061 existing recipe has no import but keeps source field`() {
        showEdit(RecipeEditUiState(recipeId = "r-1", sourceUrl = "https://www.rewe.de/rezepte/x/"))

        compose.onNodeWithText("Übernehmen").assertDoesNotExist()
        compose.onNodeWithText("Quelle (Link, optional)").assertExists()
    }

    @Test
    fun `REQ-MEAL-061 import error is shown`() {
        showEdit(RecipeEditUiState(recipeId = null, importError = RecipeImportError.NO_RECIPE))

        compose.onNodeWithText("Auf dieser Seite wurde kein Rezept gefunden. Du kannst es unten von Hand anlegen.").assertExists()
    }

    @Test
    fun `REQ-MEAL-061 importing shows progress and disables saving`() {
        showEdit(RecipeEditUiState(recipeId = null, name = "Curry", ingredientsText = "Salz", isImporting = true))

        compose.onNodeWithText("Rezept wird gelesen …").assertExists()
        compose.onNodeWithText("Speichern").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Curry").assertIsNotEnabled()
        compose.onNodeWithText("Salz").assertIsNotEnabled()
    }
}
