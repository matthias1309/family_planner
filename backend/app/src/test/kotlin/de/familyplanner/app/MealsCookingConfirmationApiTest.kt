package de.familyplanner.app

import de.familyplanner.api.model.WeekPlanResponse
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val CONFIRMATION = "cooking-confirmation"
private const val PAST_DAY = "2026-10-13"
private const val FUTURE_DAY = "2026-10-20"

class MealsCookingConfirmationApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private suspend fun HttpClient.confirm(
        token: String,
        dishId: String,
    ) = call(HttpMethod.Post, "/api/v1/meals/dishes/$dishId/$CONFIRMATION", token)

    private suspend fun HttpClient.cookedAtOf(
        token: String,
        date: String,
        week: String,
    ): String? {
        val plan =
            checkNotNull(
                json.decodeFromString<WeekPlanResponse>(call(HttpMethod.Get, "/api/v1/meals/weeks/$week/plan", token).bodyAsText()).data,
            )
        return plan.days
            .first { it.date == date }
            .dish
            ?.cookedAt
    }

    @Test
    fun `REQ-MEAL-034 parent confirms cooked dish`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.makeChildCook(family)
            val dishId = client.enterApprovedChildDish(family, PAST_DAY)
            assertNull(client.cookedAtOf(family.parentToken, PAST_DAY, "2026-10-12"))

            val response = client.confirm(family.parentToken, dishId)

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertNotNull(client.cookedAtOf(family.childToken, PAST_DAY, "2026-10-12"))
        }

    @Test
    fun `REQ-MEAL-034 child gets 403, future dish 409, second confirmation 409, unknown 404, bad id 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.makeChildCook(family)
            val past = client.enterApprovedChildDish(family, PAST_DAY)
            val future = client.enterApprovedChildDish(family, FUTURE_DAY, "Suppe")

            assertEquals(HttpStatusCode.Forbidden, client.confirm(family.childToken, past).status)
            assertEquals(HttpStatusCode.Conflict, client.confirm(family.parentToken, future).status)
            assertEquals(HttpStatusCode.NoContent, client.confirm(family.parentToken, past).status)
            assertEquals(HttpStatusCode.Conflict, client.confirm(family.parentToken, past).status)
            assertEquals(HttpStatusCode.NotFound, client.confirm(family.parentToken, UUID.randomUUID().toString()).status)
            val bad =
                client.call(
                    HttpMethod.Post,
                    "/api/v1/meals/dishes/nope/$CONFIRMATION",
                    family.parentToken,
                    invalidRequestExpected = true,
                )
            assertEquals(HttpStatusCode.BadRequest, bad.status)
        }
}
