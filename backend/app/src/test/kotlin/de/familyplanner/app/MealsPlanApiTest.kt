package de.familyplanner.app

import de.familyplanner.api.model.DishResponse
import de.familyplanner.api.model.DishStatus
import de.familyplanner.api.model.WeekPlan
import de.familyplanner.api.model.WeekPlanResponse
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DISHES = "/api/v1/meals/dishes"
private const val WEEK = "2026-10-19"
private const val TUESDAY = "2026-10-20"
private const val WEDNESDAY = "2026-10-21"

class MealsPlanApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private fun dishJson(
        date: String,
        cookId: String,
        name: String = "Spaghetti",
        ingredients: List<String> = listOf("Nudeln", "Tomaten"),
    ) = Json.encodeToString(
        buildJsonObject {
            put("date", date)
            put("cookId", cookId)
            put("name", name)
            put("ingredients", JsonArray(ingredients.map { JsonPrimitive(it) }))
        },
    )

    private suspend fun HttpClient.planOf(
        token: String,
        weekStart: String = WEEK,
    ): WeekPlan {
        val response = call(HttpMethod.Get, "/api/v1/meals/weeks/$weekStart/plan", token)
        assertEquals(HttpStatusCode.OK, response.status)
        return checkNotNull(json.decodeFromString<WeekPlanResponse>(response.bodyAsText()).data)
    }

    private suspend fun HttpClient.createDish(
        token: String,
        body: String,
        invalid: Boolean = false,
    ): HttpResponse = call(HttpMethod.Post, DISHES, token, body, invalidRequestExpected = invalid)

    private suspend fun HttpResponse.dishId() = checkNotNull(json.decodeFromString<DishResponse>(bodyAsText()).data).id

    private fun WeekPlan.dishOn(date: String) = days.single { it.date == date }.dish

    @Test
    fun `REQ-MEAL-001 empty week shows seven open days`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val plan = client.planOf(family.childToken)

            assertEquals(WEEK, plan.weekStart)
            assertEquals((19..25).map { "2026-10-$it" }, plan.days.map { it.date })
            assertTrue(plan.days.all { it.dish == null })
        }

    @Test
    fun `REQ-MEAL-032 parent plans dish and every member sees it with cook`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)

            val created = client.createDish(family.parentToken, dishJson(TUESDAY, parentId))
            val plan = client.planOf(family.childToken)

            assertEquals(HttpStatusCode.Created, created.status)
            val dish = checkNotNull(plan.dishOn(TUESDAY))
            assertEquals("Spaghetti", dish.name)
            assertEquals(parentId, dish.cookId)
            assertEquals(listOf("Nudeln", "Tomaten"), dish.ingredients)
            assertEquals(DishStatus.APPROVED, dish.status)
            assertEquals(false, dish.late)
            assertEquals(true, dish.dayValid)
            assertEquals(6, plan.days.count { it.dish == null })
        }

    @Test
    fun `REQ-MEAL-032 ingredient lines are trimmed and blank lines removed`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)

            client.createDish(family.parentToken, dishJson(TUESDAY, parentId, ingredients = listOf("  Nudeln ", "", "Tomaten")))

            assertEquals(listOf("Nudeln", "Tomaten"), client.planOf(family.parentToken).dishOn(TUESDAY)?.ingredients)
        }

    @Test
    fun `REQ-MEAL-032 parent changes and moves dish`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            val id = client.createDish(family.parentToken, dishJson(TUESDAY, parentId)).dishId()

            val updated = client.call(HttpMethod.Put, "$DISHES/$id", family.parentToken, dishJson(WEDNESDAY, parentId, name = "Lasagne"))
            val plan = client.planOf(family.parentToken)

            assertEquals(HttpStatusCode.OK, updated.status)
            assertNull(plan.dishOn(TUESDAY))
            assertEquals("Lasagne", plan.dishOn(WEDNESDAY)?.name)
        }

    @Test
    fun `REQ-MEAL-032 deleting dish reopens the day`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val id = client.createDish(family.parentToken, dishJson(TUESDAY, client.memberIdOf(family.parentToken))).dishId()

            val deleted = client.call(HttpMethod.Delete, "$DISHES/$id", family.parentToken)

            assertEquals(HttpStatusCode.NoContent, deleted.status)
            assertNull(client.planOf(family.parentToken).dishOn(TUESDAY))
        }

    @Test
    fun `REQ-MEAL-032 second dish on same day returns 409`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            client.createDish(family.parentToken, dishJson(TUESDAY, parentId))

            val second = client.createDish(family.parentToken, dishJson(TUESDAY, parentId, name = "Suppe"))

            assertEquals(HttpStatusCode.Conflict, second.status)
        }

    @Test
    fun `REQ-MEAL-032 moving onto occupied day returns 409`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            val id = client.createDish(family.parentToken, dishJson(TUESDAY, parentId)).dishId()
            client.createDish(family.parentToken, dishJson(WEDNESDAY, parentId, name = "Suppe"))

            val moved = client.call(HttpMethod.Put, "$DISHES/$id", family.parentToken, dishJson(WEDNESDAY, parentId))

            assertEquals(HttpStatusCode.Conflict, moved.status)
            assertEquals("Spaghetti", client.planOf(family.parentToken).dishOn(TUESDAY)?.name)
        }

    @Test
    fun `REQ-MEAL-001 member can switch to the following week`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.createDish(family.parentToken, dishJson(TUESDAY, client.memberIdOf(family.parentToken)))

            val next = client.planOf(family.childToken, "2026-10-26")

            assertEquals("2026-10-26", next.weekStart)
            assertTrue(next.days.all { it.dish == null })
        }

    @Test
    fun `REQ-AUTH-002 child cannot write dishes of other cooks`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            val id = client.createDish(family.parentToken, dishJson(TUESDAY, parentId)).dishId()

            val statuses =
                listOf(
                    client.createDish(family.childToken, dishJson(WEDNESDAY, parentId)).status,
                    client.call(HttpMethod.Put, "$DISHES/$id", family.childToken, dishJson(WEDNESDAY, parentId)).status,
                    client.call(HttpMethod.Delete, "$DISHES/$id", family.childToken).status,
                )

            assertEquals(List(3) { HttpStatusCode.Forbidden }, statuses)
        }

    @Test
    fun `REQ-MEAL-032 invalid dishes are rejected with 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            val token = family.parentToken

            val statuses =
                listOf(
                    client.createDish(token, dishJson(TUESDAY, parentId, name = ""), invalid = true).status,
                    client.createDish(token, dishJson(TUESDAY, parentId, name = "   ")).status,
                    client.createDish(token, dishJson(TUESDAY, parentId, ingredients = listOf("x".repeat(81))), invalid = true).status,
                    client.createDish(token, dishJson(TUESDAY, parentId, ingredients = (1..31).map { "Zutat $it" })).status,
                    client.createDish(token, dishJson(TUESDAY, parentId, ingredients = List(101) { "" }), invalid = true).status,
                    client.createDish(token, dishJson(TUESDAY, family.childId)).status,
                    client.createDish(token, dishJson("2026-13-01", parentId), invalid = true).status,
                    client.createDish(token, dishJson(TUESDAY, "abc"), invalid = true).status,
                    client.call(HttpMethod.Get, "/api/v1/meals/weeks/$TUESDAY/plan", token).status,
                    client.call(HttpMethod.Delete, "$DISHES/abc", token, invalidRequestExpected = true).status,
                )

            assertEquals(List(statuses.size) { HttpStatusCode.BadRequest }, statuses)
        }

    @Test
    fun `REQ-MEAL-032 unknown cook or dish returns 404`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)
            val unknown = UUID.randomUUID().toString()

            val statuses =
                listOf(
                    client.createDish(family.parentToken, dishJson(TUESDAY, unknown)).status,
                    client.call(HttpMethod.Put, "$DISHES/$unknown", family.parentToken, dishJson(TUESDAY, parentId)).status,
                    client.call(HttpMethod.Delete, "$DISHES/$unknown", family.parentToken).status,
                )

            assertEquals(List(3) { HttpStatusCode.NotFound }, statuses)
        }
}
