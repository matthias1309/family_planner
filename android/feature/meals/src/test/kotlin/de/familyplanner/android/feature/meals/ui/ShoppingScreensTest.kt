package de.familyplanner.android.feature.meals.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingEntry
import de.familyplanner.android.feature.meals.fakes.curryThursdaySource
import de.familyplanner.android.feature.meals.fakes.freeEntry
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.shoppingEntry
import de.familyplanner.android.feature.meals.fakes.shoppingListOf
import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListEvent
import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListScreen
import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ShoppingScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val spaghetti = shoppingEntry()
    private val lauch = shoppingEntry("k-2", "Lauch", 2.0, "Stange(n)", sources = listOf(spaghetti.sources.single(), curryThursdaySource))
    private val rice = shoppingEntry("k-3", "Reis", 500.0, "g", checked = true, sources = listOf(curryThursdaySource))

    private fun state(
        vararg entries: ShoppingEntry,
        isParent: Boolean = true,
        actionError: AppError? = null,
        newItem: String = "",
    ) = ShoppingListUiState(
        weekStart = planWeek,
        list = shoppingListOf(*entries),
        isParent = isParent,
        actionError = actionError,
        newItem = newItem,
    )

    private fun show(
        state: ShoppingListUiState,
        onEvent: (ShoppingListEvent) -> Unit = {},
    ) = compose.setContent { FamilyPlannerTheme { ShoppingListScreen(state, onEvent, {}) } }

    @Test
    fun `REQ-MEAL-040 parent sees entries with quantity and origin`() {
        show(state(spaghetti, lauch))

        compose.onNodeWithText("625 g Vollkornspaghetti").assertIsDisplayed()
        compose.onNodeWithText("2 Stange(n) Lauch").assertIsDisplayed()
        compose.onNodeWithText("Carbonara (Di), Curry (Do)").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-041 checked entries are listed under Erledigt`() {
        show(state(rice, spaghetti))

        compose.onNodeWithText("Erledigt (1)").assertIsDisplayed()
        compose.onNodeWithText("500 g Reis").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-041 checkbox click sends set checked`() {
        val events = mutableListOf<ShoppingListEvent>()
        show(state(spaghetti), events::add)

        compose.onNodeWithContentDescription("625 g Vollkornspaghetti").performClick()

        assertEquals(listOf<ShoppingListEvent>(ShoppingListEvent.SetChecked("k-1", true)), events)
    }

    @Test
    fun `REQ-MEAL-041 free item shows own entry and delete button`() {
        val events = mutableListOf<ShoppingListEvent>()
        show(state(spaghetti, freeEntry), events::add)

        compose.onNodeWithText("Eigener Eintrag").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Kaffee löschen").assertCountEquals(1)
        compose.onNodeWithContentDescription("Kaffee löschen").performClick()

        assertEquals(listOf<ShoppingListEvent>(ShoppingListEvent.DeleteItem("i-1")), events)
    }

    @Test
    fun `REQ-MEAL-041 add button sends add event`() {
        val events = mutableListOf<ShoppingListEvent>()
        show(state(spaghetti, newItem = "Brot"), events::add)

        compose.onNodeWithText("Hinzufügen").performClick()
        assertEquals(listOf<ShoppingListEvent>(ShoppingListEvent.AddItem), events)
    }

    @Test
    fun `REQ-MEAL-041 typing in add field sends new item changed`() {
        val events = mutableListOf<ShoppingListEvent>()
        show(state(spaghetti), events::add)

        compose.onNodeWithText("Eintrag hinzufügen").performTextInput("Brot")

        assertEquals(ShoppingListEvent.NewItemChanged("Brot"), events.last())
    }

    @Test
    fun `REQ-MEAL-041 add button is disabled for empty input`() {
        show(state(spaghetti))

        compose.onNodeWithText("Hinzufügen").assertIsNotEnabled()
    }

    @Test
    fun `REQ-MEAL-041 child sees read only list with check mark`() {
        show(state(spaghetti, rice, freeEntry, isParent = false))

        compose.onNodeWithText("✓ 500 g Reis").assertIsDisplayed()
        compose.onAllNodesWithText("Hinzufügen").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Kaffee löschen").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("625 g Vollkornspaghetti").assertCountEquals(0)
    }

    @Test
    fun `REQ-MEAL-040 empty list shows hint`() {
        show(state())

        compose.onNodeWithText("Noch nichts auf der Liste. Zutaten erscheinen, sobald Gerichte freigegeben sind.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-041 action error is shown`() {
        show(state(spaghetti, actionError = AppError.Validation("Höchstens 50 freie Einträge")))

        compose.onNodeWithText("Höchstens 50 freie Einträge").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-040 week header shows range and navigates`() {
        val events = mutableListOf<ShoppingListEvent>()
        show(state(spaghetti), events::add)

        compose.onNodeWithContentDescription("Nächste Woche").performClick()

        assertEquals(listOf<ShoppingListEvent>(ShoppingListEvent.NextWeek), events)
    }
}
