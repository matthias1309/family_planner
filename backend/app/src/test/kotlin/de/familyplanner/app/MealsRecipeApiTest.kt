package de.familyplanner.app

import de.familyplanner.api.model.Dish
import de.familyplanner.api.model.DishResponse
import de.familyplanner.api.model.DishStatus
import de.familyplanner.api.model.Recipe
import de.familyplanner.api.model.RecipeListResponse
import de.familyplanner.api.model.RecipeResponse
import de.familyplanner.api.model.RecipeSummary
import de.familyplanner.api.model.WeekPlanResponse
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import java.net.URLEncoder
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RECIPES = "/api/v1/meals/recipes"
private const val DISHES = "/api/v1/meals/dishes"
private const val WEEK = "2026-10-19"
private const val TUESDAY = "2026-10-20"
private const val ALWAYS_COOKS = """{"cooks":true,"pattern":{"type":"ALWAYS","weekA":[],"weekB":[]}}"""
private const val REWE_URL = "https://www.rewe.de/rezepte/bunte-gemuese-quesadilla/"

class MealsRecipeApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private fun ingredient(
        name: String,
        quantity: Double? = null,
        unit: String? = null,
    ) = buildJsonObject {
        put("name", name)
        quantity?.let { put("quantity", it) }
        unit?.let { put("unit", it) }
    }

    private fun recipeJson(
        name: String = "Carbonara",
        servings: Int = 4,
        ingredients: List<JsonElement> = listOf(ingredient("Spaghetti", 500.0, "g"), ingredient("Salz")),
        steps: List<String> = listOf("Wasser kochen", "Spaghetti garen"),
        sourceUrl: String? = null,
    ) = buildJsonObject {
        put("name", name)
        put("servings", servings)
        put("ingredients", JsonArray(ingredients))
        put("steps", buildJsonArray { steps.forEach { add(JsonPrimitive(it)) } })
        sourceUrl?.let { put("sourceUrl", it) }
    }.toString()

    private fun dishJson(
        cookId: String,
        structured: List<JsonElement>? = listOf(ingredient("Vollkornspaghetti", 625.0, "g"), ingredient("Salz")),
        lines: List<String> = emptyList(),
        recipeId: String? = null,
        servings: Int? = null,
        date: String = TUESDAY,
    ) = buildJsonObject {
        put("date", date)
        put("cookId", cookId)
        put("name", "Carbonara")
        put("ingredients", buildJsonArray { lines.forEach { add(JsonPrimitive(it)) } })
        structured?.let { put("structuredIngredients", JsonArray(it)) }
        recipeId?.let { put("recipeId", it) }
        servings?.let { put("servings", it) }
    }.toString()

    private suspend fun HttpClient.createRecipe(
        token: String,
        body: String = recipeJson(),
    ): HttpResponse = call(HttpMethod.Post, RECIPES, token, body)

    private suspend fun HttpResponse.recipe(): Recipe = checkNotNull(json.decodeFromString<RecipeResponse>(bodyAsText()).data)

    private suspend fun HttpClient.recipesFor(
        token: String,
        query: String? = null,
    ): List<RecipeSummary> {
        val suffix = query?.let { "?query=" + URLEncoder.encode(it, Charsets.UTF_8) }.orEmpty()
        val response = call(HttpMethod.Get, RECIPES + suffix, token)
        assertEquals(HttpStatusCode.OK, response.status)
        return checkNotNull(json.decodeFromString<RecipeListResponse>(response.bodyAsText()).data)
    }

    private suspend fun HttpClient.makeCook(family: Family) {
        val response = call(HttpMethod.Put, "/api/v1/meals/profiles/${family.childId}", family.parentToken, ALWAYS_COOKS)
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private suspend fun HttpClient.dishOn(
        token: String,
        date: String = TUESDAY,
    ): Dish? {
        val response = call(HttpMethod.Get, "/api/v1/meals/weeks/$WEEK/plan", token)
        val plan = checkNotNull(json.decodeFromString<WeekPlanResponse>(response.bodyAsText()).data)
        return plan.days.single { it.date == date }.dish
    }

    private suspend fun HttpResponse.dish(): Dish = checkNotNull(json.decodeFromString<DishResponse>(bodyAsText()).data)

    @Test
    fun `REQ-MEAL-060 parent creates recipe and everyone sees it`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val created = client.createRecipe(family.parentToken, recipeJson(sourceUrl = REWE_URL))
            val id = created.recipe().id
            val list = client.recipesFor(family.childToken)
            val detail = client.call(HttpMethod.Get, "$RECIPES/$id", family.childToken).recipe()

            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(listOf("Carbonara"), list.map { it.name })
            assertEquals(listOf("Spaghetti", "Salz"), detail.ingredients.map { it.name })
            assertEquals(500.0, detail.ingredients.first().quantity)
            assertEquals("g", detail.ingredients.first().unit)
            assertEquals(listOf("Wasser kochen", "Spaghetti garen"), detail.steps)
            assertEquals(REWE_URL, detail.sourceUrl)
        }

    @Test
    fun `REQ-MEAL-060 child cannot create, change or delete recipes`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val id = client.createRecipe(family.parentToken).recipe().id

            val create = client.call(HttpMethod.Post, RECIPES, family.childToken, recipeJson("Suppe"))
            val update = client.call(HttpMethod.Put, "$RECIPES/$id", family.childToken, recipeJson("Suppe"))
            val delete = client.call(HttpMethod.Delete, "$RECIPES/$id", family.childToken)

            assertEquals(listOf(HttpStatusCode.Forbidden), listOf(create.status, update.status, delete.status).distinct())
            assertEquals(listOf("Carbonara"), client.recipesFor(family.parentToken).map { it.name })
        }

    @Test
    fun `REQ-MEAL-060 recipe without name, ingredients or with invalid servings is rejected`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val token = family.parentToken

            val statuses =
                listOf(
                    client.createRecipe(token, recipeJson(name = "  ")).status,
                    client.createRecipe(token, recipeJson(ingredients = emptyList())).status,
                    client.createRecipe(token, recipeJson(servings = 0)).status,
                    client.createRecipe(token, recipeJson(servings = 21)).status,
                )

            assertEquals(listOf(HttpStatusCode.BadRequest), statuses.distinct())
        }

    @Test
    fun `REQ-MEAL-060 duplicate recipe name ignoring case returns 409`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.createRecipe(family.parentToken, recipeJson("Quesadilla"))

            val duplicate = client.createRecipe(family.parentToken, recipeJson("quesadilla"))

            assertEquals(HttpStatusCode.Conflict, duplicate.status)
        }

    @Test
    fun `REQ-MEAL-060 invalid source url is rejected`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val token = family.parentToken

            val ftp = client.createRecipe(token, recipeJson(sourceUrl = "ftp://x"))
            val text = client.createRecipe(token, recipeJson(sourceUrl = "kein link"))
            val rewe = client.createRecipe(token, recipeJson(sourceUrl = REWE_URL))

            assertEquals(HttpStatusCode.BadRequest, ftp.status)
            assertEquals(HttpStatusCode.BadRequest, text.status)
            assertEquals(HttpStatusCode.Created, rewe.status)
        }

    @Test
    fun `REQ-MEAL-060 search filters by name ignoring case`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            listOf("Quesadilla", "Carbonara", "Apfelkuchen").forEach { client.createRecipe(family.parentToken, recipeJson(it)) }

            val carbo = client.recipesFor(family.childToken, "CARBO")
            val all = client.recipesFor(family.childToken, "")

            assertEquals(listOf("Carbonara"), carbo.map { it.name })
            assertEquals(listOf("Apfelkuchen", "Carbonara", "Quesadilla"), all.map { it.name })
        }

    @Test
    fun `REQ-MEAL-060 too long search query is rejected`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val response =
                client.call(HttpMethod.Get, "$RECIPES?query=" + "x".repeat(61), family.parentToken, invalidRequestExpected = true)

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `REQ-MEAL-060 changing or deleting a recipe keeps dishes taken from it`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            val id = client.createRecipe(family.parentToken).recipe().id
            val created = client.call(HttpMethod.Post, DISHES, family.parentToken, dishJson(parentId, recipeId = id, servings = 5))
            val before = created.dish()

            val updated =
                client.call(
                    HttpMethod.Put,
                    "$RECIPES/$id",
                    family.parentToken,
                    recipeJson(ingredients = listOf(ingredient("Ei"))),
                )
            val afterUpdate = client.dishOn(family.parentToken)
            val deleted = client.call(HttpMethod.Delete, "$RECIPES/$id", family.parentToken)
            val afterDelete = checkNotNull(client.dishOn(family.parentToken))
            val gone = client.call(HttpMethod.Get, "$RECIPES/$id", family.parentToken)

            assertEquals(HttpStatusCode.OK, updated.status)
            assertEquals(before, afterUpdate)
            assertEquals(HttpStatusCode.NoContent, deleted.status)
            assertEquals(before.structuredIngredients, afterDelete.structuredIngredients)
            assertNull(afterDelete.recipeId)
            assertEquals(HttpStatusCode.NotFound, gone.status)
        }

    @Test
    fun `REQ-MEAL-060 unknown recipe returns 404 and invalid id 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val token = family.parentToken
            val unknown = "$RECIPES/${UUID.randomUUID()}"

            val statuses =
                listOf(
                    client.call(HttpMethod.Get, unknown, token).status,
                    client.call(HttpMethod.Put, unknown, token, recipeJson()).status,
                    client.call(HttpMethod.Delete, unknown, token).status,
                )
            val invalid = client.call(HttpMethod.Get, "$RECIPES/nope", token, invalidRequestExpected = true)

            assertEquals(listOf(HttpStatusCode.NotFound), statuses.distinct())
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
        }

    @Test
    fun `REQ-MEAL-062 child submits dish from recipe with structured ingredients`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.makeCook(family)
            val recipeId = client.createRecipe(family.parentToken).recipe().id

            val response =
                client.call(HttpMethod.Post, DISHES, family.childToken, dishJson(family.childId, recipeId = recipeId, servings = 5))

            assertEquals(HttpStatusCode.Created, response.status)
            val dish = response.dish()
            assertEquals(DishStatus.SUBMITTED, dish.status)
            assertEquals(listOf("625 g Vollkornspaghetti", "Salz"), dish.ingredients)
            assertEquals(listOf("Vollkornspaghetti", "Salz"), dish.structuredIngredients.map { it.name })
            assertEquals(625.0, dish.structuredIngredients.first().quantity)
            assertEquals(5, dish.servings)
            assertEquals(recipeId, dish.recipeId)
        }

    @Test
    fun `REQ-MEAL-062 dish with unknown recipe returns 404`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)

            val response =
                client.call(
                    HttpMethod.Post,
                    DISHES,
                    family.parentToken,
                    dishJson(parentId, recipeId = UUID.randomUUID().toString(), servings = 4),
                )

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `REQ-MEAL-062 dish with recipe but without servings returns 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            val recipeId = client.createRecipe(family.parentToken).recipe().id

            val missing = client.call(HttpMethod.Post, DISHES, family.parentToken, dishJson(parentId, recipeId = recipeId))
            val zero = client.call(HttpMethod.Post, DISHES, family.parentToken, dishJson(parentId, recipeId = recipeId, servings = 0))
            val orphan = client.call(HttpMethod.Post, DISHES, family.parentToken, dishJson(parentId, servings = 4))

            assertEquals(listOf(HttpStatusCode.BadRequest), listOf(missing.status, zero.status, orphan.status).distinct())
        }

    @Test
    fun `REQ-MEAL-062 lines and structured ingredients together are rejected`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)

            val response = client.call(HttpMethod.Post, DISHES, family.parentToken, dishJson(parentId, lines = listOf("Nudeln")))

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `REQ-MEAL-062 free-text dish still works for older apps`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            val body = dishJson(parentId, structured = null, lines = listOf("Nudeln"))

            val response = client.call(HttpMethod.Post, DISHES, family.parentToken, body)

            assertEquals(HttpStatusCode.Created, response.status)
            val dish = checkNotNull(client.dishOn(family.childToken))
            assertEquals(listOf("Nudeln"), dish.ingredients)
            assertEquals(listOf("Nudeln"), dish.structuredIngredients.map { it.name })
            assertNull(dish.structuredIngredients.single().quantity)
            assertNull(dish.recipeId)
            assertNull(dish.servings)
            assertTrue(dish.dayValid)
        }

    @Test
    fun `REQ-MEAL-062 older app sending display lines back keeps structure and recipe`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            val recipeId = client.createRecipe(family.parentToken).recipe().id
            val created =
                client
                    .call(
                        HttpMethod.Post,
                        DISHES,
                        family.parentToken,
                        dishJson(parentId, recipeId = recipeId, servings = 5),
                    ).dish()
            val lines = checkNotNull(client.dishOn(family.childToken)).ingredients

            val response =
                client.call(
                    HttpMethod.Put,
                    "$DISHES/${created.id}",
                    family.parentToken,
                    dishJson(parentId, structured = null, lines = lines),
                )

            assertEquals(HttpStatusCode.OK, response.status)
            val dish = response.dish()
            assertEquals(created.structuredIngredients, dish.structuredIngredients)
            assertEquals(recipeId, dish.recipeId)
            assertEquals(5, dish.servings)
        }
}
