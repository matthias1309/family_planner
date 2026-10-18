package de.familyplanner.android.feature.meals.data

import de.familyplanner.android.feature.meals.data.recipe.decodeScriptList
import de.familyplanner.android.feature.meals.data.recipe.readJsonLdRecipe
import de.familyplanner.android.feature.meals.fakes.carbonaraSource
import de.familyplanner.android.feature.meals.fakes.reweBlocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JsonLdRecipeReaderTest {
    private fun recipe(extra: String) = """{"@type":"Recipe","name":"Suppe","recipeIngredient":["1 Zwiebel"]$extra}"""

    @Test
    fun `REQ-MEAL-061 rewe blocks yield the full recipe`() {
        assertEquals(carbonaraSource, readJsonLdRecipe(reweBlocks))
    }

    @Test
    fun `REQ-MEAL-061 recipe with ingredients wins over rating recipe in front`() {
        assertEquals(carbonaraSource, readJsonLdRecipe(reweBlocks.reversed()))
    }

    @Test
    fun `REQ-MEAL-061 graph direct and array type variants are found`() {
        assertNotNull(readJsonLdRecipe(listOf("""{"@graph":[{"@type":"Organization"},${recipe("")}]}""")))
        assertNotNull(readJsonLdRecipe(listOf(recipe(""))))
        assertNotNull(readJsonLdRecipe(listOf("""{"@type":["Recipe","NewsArticle"],"name":"A","recipeIngredient":"1 Ei"}""")))
        assertNotNull(readJsonLdRecipe(listOf("[${recipe("")}]")))
    }

    @Test
    fun `REQ-MEAL-061 yield variants`() {
        fun yield(value: String) = readJsonLdRecipe(listOf(recipe(""","recipeYield":$value""")))?.servings
        assertEquals(4, yield("4"))
        assertEquals(4, yield("""["4","4 Portionen"]"""))
        assertEquals(4, yield("\"4 Portionen\""))
        assertNull(yield("\"viele\""))
        assertNull(readJsonLdRecipe(listOf(recipe("")))?.servings)
    }

    @Test
    fun `REQ-MEAL-061 instruction variants`() {
        fun steps(value: String) = readJsonLdRecipe(listOf(recipe(""","recipeInstructions":$value""")))?.steps
        assertEquals(listOf("Eins", "Zwei drei"), steps("\"Eins\\n\\nZwei   drei\""))
        assertEquals(listOf("Eins", "Zwei"), steps("""["Eins","Zwei"]"""))
        assertEquals(
            listOf("Eins", "Zwei"),
            steps("""[{"@type":"HowToSection","name":"A","itemListElement":[{"@type":"HowToStep","text":"Eins"},{"name":"Zwei"}]}]"""),
        )
    }

    @Test
    fun `REQ-MEAL-061 invalid block is skipped`() {
        assertNotNull(readJsonLdRecipe(listOf("{kaputt", recipe(""))))
    }

    @Test
    fun `REQ-MEAL-061 no recipe gives null`() {
        assertNull(readJsonLdRecipe(listOf("""{"@type":"Organization"}""", """{"@type":"BreadcrumbList"}""")))
        assertNull(readJsonLdRecipe(listOf("""{"@type":"Recipe","name":"Leer","recipeIngredient":[]}""")))
        assertNull(readJsonLdRecipe(emptyList()))
    }

    @Test
    fun `REQ-MEAL-061 script list is decoded`() {
        assertEquals(listOf("a", "b"), decodeScriptList("[\"a\",\"b\"]"))
        listOf("null", "", null, "{}", "[kaputt", "[1,2]").forEach { assertEquals(emptyList(), decodeScriptList(it)) }
    }
}
