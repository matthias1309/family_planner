package de.familyplanner.app

import de.familyplanner.api.model.DishResponse
import de.familyplanner.api.model.ShoppingEntry
import de.familyplanner.api.model.ShoppingListResponse
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DISHES = "/api/v1/meals/dishes"
private const val WEEK = "2026-10-19"
private const val LIST = "/api/v1/meals/weeks/$WEEK/shopping-list"
private const val ALWAYS_COOKS = """{"cooks":true,"pattern":{"type":"ALWAYS","weekA":[],"weekB":[]}}"""

class MealsShoppingApiTest {
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

    private fun dishJson(
        cookId: String,
        date: String,
        vararg ingredients: JsonElement,
    ) = buildJsonObject {
        put("date", date)
        put("cookId", cookId)
        put("name", "Gericht $date")
        put("ingredients", buildJsonArray { })
        put("structuredIngredients", JsonArray(ingredients.toList()))
    }.toString()

    private suspend fun HttpClient.planDish(
        token: String,
        cookId: String,
        date: String,
        vararg ingredients: JsonElement,
    ): HttpResponse = call(HttpMethod.Post, DISHES, token, dishJson(cookId, date, *ingredients))

    private suspend fun HttpResponse.entries(): List<ShoppingEntry> =
        checkNotNull(json.decodeFromString<ShoppingListResponse>(bodyAsText()).data).propertyEntries

    private suspend fun HttpClient.listFor(token: String): List<ShoppingEntry> {
        val response = call(HttpMethod.Get, LIST, token)
        assertEquals(HttpStatusCode.OK, response.status)
        return response.entries()
    }

    private suspend fun HttpClient.makeCook(family: Family) {
        val response = call(HttpMethod.Put, "/api/v1/meals/profiles/${family.childId}", family.parentToken, ALWAYS_COOKS)
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `REQ-MEAL-040 approved dishes are merged with their sources`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            client.planDish(family.parentToken, parentId, "2026-10-20", ingredient("Spaghetti", 500.0, "g"), ingredient("Salz"))
            client.planDish(family.parentToken, parentId, "2026-10-21", ingredient("Spaghetti", 250.0, "g"), ingredient("Salz"))

            val entries = client.listFor(family.parentToken)

            assertEquals(listOf("Salz", "Spaghetti"), entries.map { it.name })
            assertNull(entries[0].quantity)
            assertEquals(2, entries[0].sources.size)
            assertEquals(750.0, entries[1].quantity)
            assertEquals("g", entries[1].unit)
            assertEquals(listOf("2026-10-20", "2026-10-21"), entries[1].sources.map { it.date })
        }

    @Test
    fun `REQ-MEAL-040 submitted child dish appears only after approval`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.makeCook(family)
            val submitted = client.planDish(family.childToken, family.childId, "2026-10-22", ingredient("Reis", 200.0, "g"))
            val dishId = checkNotNull(json.decodeFromString<DishResponse>(submitted.bodyAsText()).data).id
            val before = client.listFor(family.parentToken)

            val approval = client.call(HttpMethod.Post, "$DISHES/$dishId/approval", family.parentToken)
            val after = client.listFor(family.parentToken)

            assertTrue(before.isEmpty())
            assertEquals(HttpStatusCode.NoContent, approval.status)
            assertEquals(listOf("Reis"), after.map { it.name })
        }

    @Test
    fun `REQ-MEAL-040 different units stay separate entries`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            client.planDish(family.parentToken, parentId, "2026-10-20", ingredient("Sahne", 200.0, "g"))
            client.planDish(family.parentToken, parentId, "2026-10-21", ingredient("Sahne", 1.0, "Becher"))

            val entries = client.listFor(family.parentToken)

            assertEquals(2, entries.size)
            assertEquals(setOf("g", "Becher"), entries.mapNotNull { it.unit }.toSet())
        }

    @Test
    fun `REQ-MEAL-041 parent adds, checks and deletes a free item`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val added = client.call(HttpMethod.Post, "$LIST/items", family.parentToken, ingredient("Batterien", 4.0).toString())
            val item = added.entries().single()
            val checked = client.call(HttpMethod.Put, "$LIST/checks", family.parentToken, """{"key":"${item.key}","checked":true}""")
            val deleted = client.call(HttpMethod.Delete, "$LIST/items/${item.itemId}", family.parentToken)

            assertEquals(HttpStatusCode.Created, added.status)
            assertEquals("Batterien", item.name)
            assertFalse(item.checked)
            assertTrue(checked.entries().single().checked)
            assertEquals(HttpStatusCode.NoContent, deleted.status)
            assertTrue(client.listFor(family.parentToken).isEmpty())
        }

    @Test
    fun `REQ-MEAL-041 checked dish entry reopens after more quantity is approved`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            client.planDish(family.parentToken, parentId, "2026-10-20", ingredient("Mehl", 500.0, "g"))
            val key = client.listFor(family.parentToken).single().key
            val checked = client.call(HttpMethod.Put, "$LIST/checks", family.parentToken, """{"key":"$key","checked":true}""")

            client.planDish(family.parentToken, parentId, "2026-10-21", ingredient("Mehl", 250.0, "g"))
            val entry = client.listFor(family.parentToken).single()

            assertTrue(checked.entries().single().checked)
            assertEquals(750.0, entry.quantity)
            assertFalse(entry.checked)
        }

    @Test
    fun `REQ-MEAL-041 child can read but not change the list`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val itemId = UUID.randomUUID()

            val read = client.call(HttpMethod.Get, LIST, family.childToken)
            val post = client.call(HttpMethod.Post, "$LIST/items", family.childToken, ingredient("Brot").toString())
            val put = client.call(HttpMethod.Put, "$LIST/checks", family.childToken, """{"key":"item:x","checked":true}""")
            val delete = client.call(HttpMethod.Delete, "$LIST/items/$itemId", family.childToken)

            assertEquals(HttpStatusCode.OK, read.status)
            assertEquals(HttpStatusCode.Forbidden, post.status)
            assertEquals(HttpStatusCode.Forbidden, put.status)
            assertEquals(HttpStatusCode.Forbidden, delete.status)
        }

    @Test
    fun `REQ-MEAL-041 unknown key and unknown item return 404`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val unknownKey = client.call(HttpMethod.Put, "$LIST/checks", family.parentToken, """{"key":"ing:nope||-","checked":true}""")
            val unknownItem = client.call(HttpMethod.Delete, "$LIST/items/${UUID.randomUUID()}", family.parentToken)

            assertEquals(HttpStatusCode.NotFound, unknownKey.status)
            assertEquals(HttpStatusCode.NotFound, unknownItem.status)
        }

    @Test
    fun `REQ-MEAL-040 week must start on a monday`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val response = client.call(HttpMethod.Get, "/api/v1/meals/weeks/2026-10-20/shopping-list", family.parentToken)

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `REQ-MEAL-040 summed quantity may exceed the single ingredient maximum`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            client.planDish(family.parentToken, parentId, "2026-10-20", ingredient("Mehl", 8000.0, "g"))
            client.planDish(family.parentToken, parentId, "2026-10-21", ingredient("Mehl", 7000.0, "g"))

            val entry = client.listFor(family.parentToken).single()

            assertEquals(15000.0, entry.quantity)
        }

    @Test
    fun `REQ-MEAL-041 more than 50 free items are rejected`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            repeat(50) { client.call(HttpMethod.Post, "$LIST/items", family.parentToken, ingredient("Artikel $it").toString()) }

            val response = client.call(HttpMethod.Post, "$LIST/items", family.parentToken, ingredient("Einer zu viel").toString())

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(50, client.listFor(family.parentToken).size)
        }
}
