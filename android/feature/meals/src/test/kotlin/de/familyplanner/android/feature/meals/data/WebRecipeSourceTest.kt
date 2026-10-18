package de.familyplanner.android.feature.meals.data

import de.familyplanner.android.feature.meals.data.recipe.WebRecipeSource
import de.familyplanner.android.feature.meals.domain.recipe.SourceResult
import de.familyplanner.android.feature.meals.fakes.CARBONARA_URL
import de.familyplanner.android.feature.meals.fakes.FakePageScriptLoader
import de.familyplanner.android.feature.meals.fakes.carbonaraSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebRecipeSourceTest {
    private val loader = FakePageScriptLoader()
    private val source = WebRecipeSource(loader)

    @Test
    fun `REQ-MEAL-061 rewe page is found and loader gets the url`() =
        runTest {
            assertEquals(SourceResult.Found(carbonaraSource), source.read(CARBONARA_URL))
            assertEquals(listOf(CARBONARA_URL), loader.requested)
        }

    @Test
    fun `REQ-MEAL-061 unreachable page is reported`() =
        runTest {
            loader.scripts = null
            assertEquals(SourceResult.Unreachable, source.read(CARBONARA_URL))
        }

    @Test
    fun `REQ-MEAL-061 page without json ld counts as no recipe`() =
        runTest {
            loader.scripts = emptyList()
            assertEquals(SourceResult.NotFound, source.read(CARBONARA_URL))
        }
}
