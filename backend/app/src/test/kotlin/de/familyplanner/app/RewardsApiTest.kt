package de.familyplanner.app

import de.familyplanner.api.model.PointsAccountResponse
import de.familyplanner.api.model.RedemptionListResponse
import de.familyplanner.api.model.RedemptionResponse
import de.familyplanner.api.model.RedemptionStatus
import de.familyplanner.api.model.RewardListResponse
import de.familyplanner.api.model.RewardResponse
import de.familyplanner.api.model.SavingsGoalResponse
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val GAME = "/api/v1/gamification"

class RewardsApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private suspend fun HttpClient.createReward(
        family: Family,
        price: Int = 200,
    ) = checkNotNull(
        json
            .decodeFromString<RewardResponse>(
                call(
                    HttpMethod.Post,
                    "$GAME/rewards",
                    family.parentToken,
                    """{"title":"Freizeitpark","emoji":"🎢","price":$price}""",
                ).bodyAsText(),
            ).data,
    )

    private suspend fun HttpClient.credit(
        family: Family,
        amount: Int,
    ) = assertEquals(
        HttpStatusCode.Created,
        call(
            HttpMethod.Post,
            "$GAME/members/${family.childId}/entries",
            family.parentToken,
            """{"amount":$amount,"reason":"Start"}""",
        ).status,
    )

    private suspend fun HttpClient.redeem(
        family: Family,
        rewardId: String,
    ) = call(HttpMethod.Post, "$GAME/rewards/$rewardId/redemptions", family.childToken)

    private suspend fun HttpClient.balanceOf(family: Family) =
        checkNotNull(
            json
                .decodeFromString<PointsAccountResponse>(
                    call(HttpMethod.Get, "$GAME/members/${family.childId}/account", family.childToken).bodyAsText(),
                ).data,
        ).balance

    @Test
    fun `REQ-GAME-040 parent creates reward, child lists it, child gets 403 on create`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val reward = client.createReward(family)

            val listed = client.call(HttpMethod.Get, "$GAME/rewards", family.childToken)
            assertEquals(listOf(reward), checkNotNull(json.decodeFromString<RewardListResponse>(listed.bodyAsText()).data))
            val forbidden = client.call(HttpMethod.Post, "$GAME/rewards", family.childToken, """{"title":"x","price":5}""")
            assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        }

    @Test
    fun `REQ-GAME-040 archive hides reward, update archived 409`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val reward = client.createReward(family)

            assertEquals(HttpStatusCode.NoContent, client.call(HttpMethod.Delete, "$GAME/rewards/${reward.id}", family.parentToken).status)

            val hidden = client.call(HttpMethod.Get, "$GAME/rewards", family.childToken)
            assertTrue(checkNotNull(json.decodeFromString<RewardListResponse>(hidden.bodyAsText()).data).isEmpty())
            val all = client.call(HttpMethod.Get, "$GAME/rewards?includeArchived=true", family.childToken)
            assertEquals(listOf(true), checkNotNull(json.decodeFromString<RewardListResponse>(all.bodyAsText()).data).map { it.archived })
            val update = client.call(HttpMethod.Put, "$GAME/rewards/${reward.id}", family.parentToken, """{"title":"Neu","price":10}""")
            assertEquals(HttpStatusCode.Conflict, update.status)
        }

    @Test
    fun `REQ-GAME-041 child sets goal and reads progress, parent reads, sibling 403`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val (siblingId, siblingToken, _) = client.addPairedChild(family.parentToken, "Max")
            val reward = client.createReward(family)
            client.credit(family, 120)
            val goalPath = "$GAME/members/${family.childId}/savings-goal"

            val set = client.call(HttpMethod.Put, goalPath, family.childToken, """{"rewardId":"${reward.id}"}""")

            assertEquals(HttpStatusCode.OK, set.status)
            val goal = checkNotNull(json.decodeFromString<SavingsGoalResponse>(set.bodyAsText()).data)
            assertEquals(120, goal.progress?.current)
            assertEquals(200, goal.progress?.target)
            assertEquals(false, goal.redeemable)
            val byParent = client.call(HttpMethod.Get, goalPath, family.parentToken)
            assertEquals(reward, json.decodeFromString<SavingsGoalResponse>(byParent.bodyAsText()).data?.reward)
            assertEquals(HttpStatusCode.Forbidden, client.call(HttpMethod.Get, goalPath, siblingToken).status)
            assertEquals(
                HttpStatusCode.Forbidden,
                client.call(HttpMethod.Put, goalPath, family.parentToken, """{"rewardId":"${reward.id}"}""").status,
            )
            assertEquals(siblingId, client.memberIdOf(siblingToken))
            assertEquals(HttpStatusCode.NoContent, client.call(HttpMethod.Delete, goalPath, family.childToken).status)
            val cleared = client.call(HttpMethod.Get, goalPath, family.childToken)
            assertNull(json.decodeFromString<SavingsGoalResponse>(cleared.bodyAsText()).data?.reward)
        }

    @Test
    fun `REQ-GAME-042 child redeems, balance drops, parent sees pending redemption`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val reward = client.createReward(family)
            client.credit(family, 250)

            val response = client.redeem(family, reward.id)

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(50, client.balanceOf(family))
            val pending = client.call(HttpMethod.Get, "$GAME/redemptions?status=PENDING", family.parentToken)
            val items = checkNotNull(json.decodeFromString<RedemptionListResponse>(pending.bodyAsText()).data)
            assertEquals(listOf(RedemptionStatus.PENDING), items.map { it.status })
            assertEquals(listOf(200), items.map { it.price })
        }

    @Test
    fun `REQ-GAME-042 insufficient balance 409`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val reward = client.createReward(family)
            client.credit(family, 199)

            assertEquals(HttpStatusCode.Conflict, client.redeem(family, reward.id).status)

            assertEquals(199, client.balanceOf(family))
        }

    @Test
    fun `REQ-GAME-042 invalid status filter 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val response = client.call(HttpMethod.Get, "$GAME/redemptions?status=NOPE", family.parentToken, invalidRequestExpected = true)

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `REQ-GAME-043 parent rejects with reason, child sees reason and refund`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val reward = client.createReward(family)
            client.credit(family, 200)
            val redemption = checkNotNull(json.decodeFromString<RedemptionResponse>(client.redeem(family, reward.id).bodyAsText()).data)

            val rejected =
                client.call(
                    HttpMethod.Post,
                    "$GAME/redemptions/${redemption.id}/rejection",
                    family.parentToken,
                    """{"reason":"Erst im Sommer"}""",
                )

            assertEquals(HttpStatusCode.OK, rejected.status)
            assertEquals(200, client.balanceOf(family))
            val mine = client.call(HttpMethod.Get, "$GAME/redemptions", family.childToken)
            val item = checkNotNull(json.decodeFromString<RedemptionListResponse>(mine.bodyAsText()).data).single()
            assertEquals(RedemptionStatus.REJECTED, item.status)
            assertEquals("Erst im Sommer", item.rejectionReason)
        }

    @Test
    fun `REQ-GAME-043 reject without body 400, second decision 409`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val reward = client.createReward(family)
            client.credit(family, 200)
            val redemption = checkNotNull(json.decodeFromString<RedemptionResponse>(client.redeem(family, reward.id).bodyAsText()).data)
            val path = "$GAME/redemptions/${redemption.id}"

            val noBody = client.call(HttpMethod.Post, "$path/rejection", family.parentToken, invalidRequestExpected = true)
            assertEquals(HttpStatusCode.BadRequest, noBody.status)
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Post, "$path/fulfillment", family.parentToken).status)
            assertEquals(HttpStatusCode.Conflict, client.call(HttpMethod.Post, "$path/fulfillment", family.parentToken).status)
            assertEquals(HttpStatusCode.Forbidden, client.call(HttpMethod.Post, "$path/fulfillment", family.childToken).status)
        }
}
