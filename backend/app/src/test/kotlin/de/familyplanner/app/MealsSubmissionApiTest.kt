package de.familyplanner.app

import de.familyplanner.api.model.CookState
import de.familyplanner.api.model.Dish
import de.familyplanner.api.model.DishResponse
import de.familyplanner.api.model.DishStatus
import de.familyplanner.api.model.WeekPlan
import de.familyplanner.api.model.WeekPlanResponse
import de.familyplanner.api.model.WeekStatus
import de.familyplanner.api.model.WeekStatusResponse
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
private const val ALWAYS_COOKS = """{"cooks":true,"pattern":{"type":"ALWAYS","weekA":[],"weekB":[]}}"""

class MealsSubmissionApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private fun dishJson(
        date: String,
        cookId: String,
        name: String = "Spaghetti",
        ingredients: List<String> = listOf("Nudeln"),
    ) = Json.encodeToString(
        buildJsonObject {
            put("date", date)
            put("cookId", cookId)
            put("name", name)
            put("ingredients", JsonArray(ingredients.map { JsonPrimitive(it) }))
        },
    )

    private suspend fun HttpClient.makeCook(family: Family) {
        val response = call(HttpMethod.Put, "/api/v1/meals/profiles/${family.childId}", family.parentToken, ALWAYS_COOKS)
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private suspend fun HttpClient.absent(
        family: Family,
        from: String,
        to: String,
    ) {
        val body = """{"from":"$from","to":"$to","presence":"ABSENT"}"""
        val response = call(HttpMethod.Post, "/api/v1/meals/profiles/${family.childId}/exceptions", family.parentToken, body)
        assertEquals(HttpStatusCode.Created, response.status)
    }

    private suspend fun HttpClient.submit(
        token: String,
        body: String,
        invalid: Boolean = false,
    ): HttpResponse = call(HttpMethod.Post, DISHES, token, body, invalidRequestExpected = invalid)

    private suspend fun HttpResponse.dish(): Dish = checkNotNull(json.decodeFromString<DishResponse>(bodyAsText()).data)

    private suspend fun HttpClient.planOf(token: String): WeekPlan =
        checkNotNull(
            json.decodeFromString<WeekPlanResponse>(call(HttpMethod.Get, "/api/v1/meals/weeks/$WEEK/plan", token).bodyAsText()).data,
        )

    private suspend fun HttpClient.statusOf(token: String): WeekStatus {
        val response = call(HttpMethod.Get, "/api/v1/meals/weeks/$WEEK/status", token)
        assertEquals(HttpStatusCode.OK, response.status)
        return checkNotNull(json.decodeFromString<WeekStatusResponse>(response.bodyAsText()).data)
    }

    private suspend fun HttpClient.cancel(
        token: String,
        memberId: String,
        reason: String,
        invalid: Boolean = false,
    ) = call(
        HttpMethod.Put,
        "/api/v1/meals/weeks/$WEEK/cancellations/$memberId",
        token,
        """{"reason":"$reason"}""",
        invalidRequestExpected = invalid,
    )

    private suspend fun HttpClient.withdraw(
        token: String,
        memberId: String,
    ) = call(HttpMethod.Delete, "/api/v1/meals/weeks/$WEEK/cancellations/$memberId", token)

    private suspend fun HttpClient.reject(
        token: String,
        id: String,
        reason: String,
        invalid: Boolean = false,
    ) = call(HttpMethod.Post, "$DISHES/$id/rejection", token, """{"reason":"$reason"}""", invalidRequestExpected = invalid)

    private suspend fun HttpClient.approve(
        token: String,
        id: String,
    ) = call(HttpMethod.Post, "$DISHES/$id/approval", token)

    private fun WeekPlan.dishOn(date: String) = days.single { it.date == date }.dish

    private suspend fun ApplicationTestBuilder.cookingFamily(): Family = setupFamilyWithChild().also { client.makeCook(it) }

    @Test
    fun `REQ-MEAL-020 cooking child submits dish and it waits for approval`() =
        testApplication {
            start()
            val family = cookingFamily()

            val created = client.submit(family.childToken, dishJson(TUESDAY, family.childId))

            assertEquals(HttpStatusCode.Created, created.status)
            val dish = created.dish()
            assertEquals(DishStatus.SUBMITTED, dish.status)
            assertEquals(false, dish.late)
            assertEquals(DishStatus.SUBMITTED, client.planOf(family.parentToken).dishOn(TUESDAY)?.status)
        }

    @Test
    fun `REQ-MEAL-020 child dish without ingredients or name is rejected`() =
        testApplication {
            start()
            val family = cookingFamily()

            val statuses =
                listOf(
                    client.submit(family.childToken, dishJson(TUESDAY, family.childId, ingredients = emptyList())).status,
                    client.submit(family.childToken, dishJson(TUESDAY, family.childId, ingredients = listOf(" "))).status,
                    client.submit(family.childToken, dishJson(TUESDAY, family.childId, name = "   ")).status,
                )

            assertEquals(List(3) { HttpStatusCode.BadRequest }, statuses)
        }

    @Test
    fun `REQ-MEAL-020 submission after deadline is accepted and marked late`() =
        testApplication {
            start()
            val family = cookingFamily()
            client.absent(family, "2026-10-14", "2026-10-18")

            val created = client.submit(family.childToken, dishJson(TUESDAY, family.childId))

            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(true, created.dish().late)
        }

    @Test
    fun `REQ-MEAL-020 child that does not cook or writes for a past week gets 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val notCooking = client.submit(family.childToken, dishJson(TUESDAY, family.childId))
            client.makeCook(family)
            val pastWeek = client.submit(family.childToken, dishJson("2026-10-13", family.childId))

            assertEquals(HttpStatusCode.BadRequest, notCooking.status)
            assertEquals(HttpStatusCode.BadRequest, pastWeek.status)
        }

    @Test
    fun `REQ-MEAL-020 second dish of the same child in a week returns 409`() =
        testApplication {
            start()
            val family = cookingFamily()
            client.submit(family.childToken, dishJson(TUESDAY, family.childId))

            val second = client.submit(family.childToken, dishJson(WEDNESDAY, family.childId, name = "Suppe"))

            assertEquals(HttpStatusCode.Conflict, second.status)
        }

    @Test
    fun `REQ-MEAL-021 child cannot cook on an absent day`() =
        testApplication {
            start()
            val family = cookingFamily()
            client.absent(family, TUESDAY, TUESDAY)

            val response = client.submit(family.childToken, dishJson(TUESDAY, family.childId))

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `REQ-MEAL-021 dish becomes invalid when its day turns absent`() =
        testApplication {
            start()
            val family = cookingFamily()
            client.submit(family.childToken, dishJson(TUESDAY, family.childId))

            client.absent(family, TUESDAY, TUESDAY)

            assertEquals(false, client.planOf(family.parentToken).dishOn(TUESDAY)?.dayValid)
            val status = client.statusOf(family.parentToken)
            assertEquals(false, status.readyToOrder)
            assertEquals(false, status.cooks.single().dayValid)
        }

    @Test
    fun `REQ-MEAL-022 child edits pending dish and it stays pending`() =
        testApplication {
            start()
            val family = cookingFamily()
            val id = client.submit(family.childToken, dishJson(TUESDAY, family.childId)).dish().id

            val edited = client.call(HttpMethod.Put, "$DISHES/$id", family.childToken, dishJson(TUESDAY, family.childId, name = "Lasagne"))

            assertEquals(HttpStatusCode.OK, edited.status)
            assertEquals(DishStatus.SUBMITTED, edited.dish().status)
        }

    @Test
    fun `REQ-MEAL-022 editing an approved dish requires approval again`() =
        testApplication {
            start()
            val family = cookingFamily()
            val id = client.submit(family.childToken, dishJson(TUESDAY, family.childId)).dish().id
            client.approve(family.parentToken, id)

            val edited = client.call(HttpMethod.Put, "$DISHES/$id", family.childToken, dishJson(TUESDAY, family.childId, name = "Lasagne"))

            assertEquals(DishStatus.SUBMITTED, edited.dish().status)
        }

    @Test
    fun `REQ-MEAL-022 child cannot edit another member's dish`() =
        testApplication {
            start()
            val family = cookingFamily()
            val parentId = client.memberIdOf(family.parentToken)
            val id = client.submit(family.parentToken, dishJson(TUESDAY, parentId)).dish().id

            val edited = client.call(HttpMethod.Put, "$DISHES/$id", family.childToken, dishJson(TUESDAY, family.childId))

            assertEquals(HttpStatusCode.Forbidden, edited.status)
        }

    @Test
    fun `REQ-MEAL-023 child cancels with reason and parents see it in week status`() =
        testApplication {
            start()
            val family = cookingFamily()

            val response = client.cancel(family.childToken, family.childId, "krank")

            assertEquals(HttpStatusCode.OK, response.status)
            val cook = client.statusOf(family.parentToken).cooks.single()
            assertEquals(CookState.CANCELLED, cook.state)
            assertEquals("krank", cook.cancellationReason)
        }

    @Test
    fun `REQ-MEAL-023 cancellation without reason is rejected`() =
        testApplication {
            start()
            val family = cookingFamily()

            val empty = client.cancel(family.childToken, family.childId, "", invalid = true)
            val blank = client.cancel(family.childToken, family.childId, "   ")

            assertEquals(HttpStatusCode.BadRequest, empty.status)
            assertEquals(HttpStatusCode.BadRequest, blank.status)
        }

    @Test
    fun `REQ-MEAL-023 cancelling removes the submitted dish and reopens the day`() =
        testApplication {
            start()
            val family = cookingFamily()
            client.submit(family.childToken, dishJson(TUESDAY, family.childId))

            client.cancel(family.childToken, family.childId, "krank")

            assertNull(client.planOf(family.parentToken).dishOn(TUESDAY))
        }

    @Test
    fun `REQ-MEAL-023 submitting after cancellation returns 409 until it is withdrawn`() =
        testApplication {
            start()
            val family = cookingFamily()
            client.cancel(family.childToken, family.childId, "krank")

            val blocked = client.submit(family.childToken, dishJson(TUESDAY, family.childId))
            val withdrawn = client.withdraw(family.childToken, family.childId)
            val again = client.submit(family.childToken, dishJson(TUESDAY, family.childId))

            assertEquals(HttpStatusCode.Conflict, blocked.status)
            assertEquals(HttpStatusCode.NoContent, withdrawn.status)
            assertEquals(HttpStatusCode.Created, again.status)
        }

    @Test
    fun `REQ-MEAL-023 child cannot cancel for someone else and withdrawing nothing returns 404`() =
        testApplication {
            start()
            val family = cookingFamily()
            val parentId = client.memberIdOf(family.parentToken)

            val forParent = client.cancel(family.childToken, parentId, "krank")
            val nothing = client.withdraw(family.childToken, family.childId)

            assertEquals(HttpStatusCode.Forbidden, forParent.status)
            assertEquals(HttpStatusCode.NotFound, nothing.status)
        }

    @Test
    fun `REQ-MEAL-030 parent approves submitted dish`() =
        testApplication {
            start()
            val family = cookingFamily()
            val id = client.submit(family.childToken, dishJson(TUESDAY, family.childId)).dish().id

            val approved = client.approve(family.parentToken, id)

            assertEquals(HttpStatusCode.NoContent, approved.status)
            assertEquals(DishStatus.APPROVED, client.planOf(family.childToken).dishOn(TUESDAY)?.status)
        }

    @Test
    fun `REQ-MEAL-030 parent rejects with reason and child sees it`() =
        testApplication {
            start()
            val family = cookingFamily()
            val id = client.submit(family.childToken, dishJson(TUESDAY, family.childId)).dish().id

            val rejected = client.reject(family.parentToken, id, "kein Gemüse")

            assertEquals(HttpStatusCode.NoContent, rejected.status)
            val dish = checkNotNull(client.planOf(family.childToken).dishOn(TUESDAY))
            assertEquals(DishStatus.REJECTED, dish.status)
            assertEquals("kein Gemüse", dish.rejectionReason)
        }

    @Test
    fun `REQ-MEAL-030 rejection without reason is rejected`() =
        testApplication {
            start()
            val family = cookingFamily()
            val id = client.submit(family.childToken, dishJson(TUESDAY, family.childId)).dish().id

            val empty = client.reject(family.parentToken, id, "", invalid = true)
            val blank = client.reject(family.parentToken, id, "  ")

            assertEquals(HttpStatusCode.BadRequest, empty.status)
            assertEquals(HttpStatusCode.BadRequest, blank.status)
        }

    @Test
    fun `REQ-MEAL-030 child revises rejected dish and it waits for approval again`() =
        testApplication {
            start()
            val family = cookingFamily()
            val id = client.submit(family.childToken, dishJson(TUESDAY, family.childId)).dish().id
            client.reject(family.parentToken, id, "kein Gemüse")

            val revised =
                client.call(
                    HttpMethod.Put,
                    "$DISHES/$id",
                    family.childToken,
                    dishJson(TUESDAY, family.childId, ingredients = listOf("Paprika")),
                )

            assertEquals(DishStatus.SUBMITTED, revised.dish().status)
            assertNull(revised.dish().rejectionReason)
        }

    @Test
    fun `REQ-MEAL-030 reviewing needs a pending dish, a known dish and a parent`() =
        testApplication {
            start()
            val family = cookingFamily()
            val parentDish = client.submit(family.parentToken, dishJson(TUESDAY, client.memberIdOf(family.parentToken))).dish().id
            val pending = client.submit(family.childToken, dishJson(WEDNESDAY, family.childId)).dish().id

            assertEquals(HttpStatusCode.Conflict, client.approve(family.parentToken, parentDish).status)
            assertEquals(HttpStatusCode.NotFound, client.approve(family.parentToken, UUID.randomUUID().toString()).status)
            assertEquals(HttpStatusCode.Forbidden, client.approve(family.childToken, pending).status)
            assertEquals(HttpStatusCode.Forbidden, client.reject(family.childToken, pending, "nein").status)
        }

    @Test
    fun `REQ-MEAL-031 parent enters dish for child and it is approved`() =
        testApplication {
            start()
            val family = cookingFamily()

            val created = client.submit(family.parentToken, dishJson(TUESDAY, family.childId))
            val noIngredients = client.submit(family.parentToken, dishJson(WEDNESDAY, family.childId, ingredients = emptyList()))

            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(DishStatus.APPROVED, created.dish().status)
            assertEquals(HttpStatusCode.BadRequest, noIngredients.status)
        }

    @Test
    fun `REQ-MEAL-031 parent cannot enter dish for a child that does not cook`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val response = client.submit(family.parentToken, dishJson(TUESDAY, family.childId))

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `REQ-MEAL-033 week status lists open days, pending submissions and approvals`() =
        testApplication {
            start()
            val family = cookingFamily()

            val before = client.statusOf(family.parentToken)
            client.submit(family.childToken, dishJson(TUESDAY, family.childId))
            val after = client.statusOf(family.parentToken)

            assertEquals(7, before.openDays.size)
            assertEquals(CookState.NOT_SUBMITTED, before.cooks.single().state)
            assertTrue(before.cooks.single().submissionDeadline != null)
            assertEquals(CookState.SUBMITTED, after.cooks.single().state)
            assertEquals(6, after.openDays.size)
            assertEquals(false, after.readyToOrder)
        }

    @Test
    fun `REQ-MEAL-033 week is ready to order when all days planned and approved`() =
        testApplication {
            start()
            val family = cookingFamily()
            val parentId = client.memberIdOf(family.parentToken)
            val id = client.submit(family.childToken, dishJson(TUESDAY, family.childId)).dish().id
            client.approve(family.parentToken, id)
            (19..25).map { "2026-10-$it" }.filter { it != TUESDAY }.forEach {
                client.submit(family.parentToken, dishJson(it, parentId, name = "Gericht $it"))
            }

            val status = client.statusOf(family.parentToken)

            assertTrue(status.readyToOrder)
            assertTrue(status.openDays.isEmpty())
        }

    @Test
    fun `REQ-MEAL-033 child can read week status`() =
        testApplication {
            start()
            val family = cookingFamily()

            val response = client.call(HttpMethod.Get, "/api/v1/meals/weeks/$WEEK/status", family.childToken)

            assertEquals(HttpStatusCode.OK, response.status)
        }
}
