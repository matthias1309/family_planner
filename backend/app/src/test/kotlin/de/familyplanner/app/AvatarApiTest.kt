package de.familyplanner.app

import de.familyplanner.api.model.AvatarCatalogResponse
import de.familyplanner.api.model.AvatarProfileResponse
import de.familyplanner.api.model.AvatarPurchaseResponse
import de.familyplanner.api.model.LeaderboardResponse
import de.familyplanner.api.model.LedgerEntryKind
import de.familyplanner.api.model.MemberAvatarListResponse
import de.familyplanner.api.model.PointsAccountResponse
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val GAME = "/api/v1/gamification"

private val FREE_AVATAR =
    """
    {"skin":"skin.edb98a","top":"top.shortFlat","hairColor":"hairColor.724133","hatColor":"hatColor.262e33",
     "eyes":"eyes.default","eyebrows":"eyebrows.default","mouth":"mouth.smile","clothes":"clothes.hoodie",
     "clothesColor":"clothesColor.5199e4","background":"background.b6e3f4"}
    """.trimIndent()

class AvatarApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

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

    private suspend fun HttpClient.account(family: Family) =
        checkNotNull(
            json
                .decodeFromString<PointsAccountResponse>(
                    call(HttpMethod.Get, "$GAME/members/${family.childId}/account", family.childToken).bodyAsText(),
                ).data,
        )

    private suspend fun HttpClient.leaderboard(token: String) =
        checkNotNull(json.decodeFromString<LeaderboardResponse>(call(HttpMethod.Get, "$GAME/leaderboard", token).bodyAsText()).data)

    @Test
    fun `REQ-GAME-030 catalog lists 13 slots in editor order for children and parents`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            listOf(family.childToken, family.parentToken).forEach { token ->
                val response = client.call(HttpMethod.Get, "$GAME/avatar/catalog", token)

                assertEquals(HttpStatusCode.OK, response.status)
                val slots = checkNotNull(json.decodeFromString<AvatarCatalogResponse>(response.bodyAsText()).data).slots
                assertEquals(13, slots.size)
                assertEquals("skin", slots.first().slot.value)
                assertEquals(listOf("clothesGraphic", "accessories", "facialHair"), slots.filter { it.optional }.map { it.slot.value })
                assertEquals(34, slots.first { it.slot.value == "top" }.items.size)
            }
        }

    @Test
    fun `REQ-GAME-030 child saves avatar and parent sees it in the list, sibling profile is 403`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val (_, siblingToken, _) = client.addPairedChild(family.parentToken, "Max")

            val saved = client.call(HttpMethod.Put, "$GAME/members/${family.childId}/avatar", family.childToken, FREE_AVATAR)

            assertEquals(HttpStatusCode.OK, saved.status)
            val all = client.call(HttpMethod.Get, "$GAME/avatars", family.parentToken)
            val avatars = checkNotNull(json.decodeFromString<MemberAvatarListResponse>(all.bodyAsText()).data)
            assertEquals(listOf(family.childId), avatars.map { it.memberId })
            assertEquals("top.shortFlat", avatars.single().avatar.top)
            assertEquals(
                HttpStatusCode.Forbidden,
                client.call(HttpMethod.Get, "$GAME/members/${family.childId}/avatar", siblingToken).status,
            )
            assertEquals(
                HttpStatusCode.OK,
                client.call(HttpMethod.Get, "$GAME/members/${family.childId}/avatar", family.parentToken).status,
            )
        }

    @Test
    fun `REQ-GAME-030 profile without saved avatar has null avatar`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val response = client.call(HttpMethod.Get, "$GAME/members/${family.childId}/avatar", family.childToken)

            val profile = checkNotNull(json.decodeFromString<AvatarProfileResponse>(response.bodyAsText()).data)
            assertNull(profile.avatar)
            assertTrue(profile.ownedItemIds.isEmpty())
        }

    @Test
    fun `REQ-GAME-030 parent cannot save and invalid avatar is rejected`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val path = "$GAME/members/${family.childId}/avatar"

            assertEquals(HttpStatusCode.Forbidden, client.call(HttpMethod.Put, path, family.parentToken, FREE_AVATAR).status)
            val unknown = client.call(HttpMethod.Put, path, family.childToken, FREE_AVATAR.replace("eyes.default", "eyes.nope"))
            assertEquals(HttpStatusCode.BadRequest, unknown.status)
            val unowned = client.call(HttpMethod.Put, path, family.childToken, FREE_AVATAR.replace("top.shortFlat", "top.hat"))
            assertEquals(HttpStatusCode.Conflict, unowned.status)
        }

    @Test
    fun `REQ-GAME-031 purchase lowers balance, keeps collected and rank, and shows in the history`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.credit(family, 40)
            val before = client.leaderboard(family.childToken)

            val response = client.call(HttpMethod.Post, "$GAME/avatar/items/top.hat/purchase", family.childToken)

            assertEquals(HttpStatusCode.Created, response.status)
            val purchase = checkNotNull(json.decodeFromString<AvatarPurchaseResponse>(response.bodyAsText()).data)
            assertEquals("top.hat", purchase.item.id)
            assertEquals(10, purchase.balance)
            val account = client.account(family)
            assertEquals(10, account.balance)
            assertEquals(40, account.collected)
            val shop = account.items.first { it.kind == LedgerEntryKind.SHOP }
            assertEquals("Shop: Hut", shop.title)
            assertEquals(-30, shop.amount)
            assertEquals(before, client.leaderboard(family.childToken))
            val profile = client.call(HttpMethod.Get, "$GAME/members/${family.childId}/avatar", family.childToken)
            assertEquals(
                listOf("top.hat"),
                checkNotNull(json.decodeFromString<AvatarProfileResponse>(profile.bodyAsText()).data).ownedItemIds,
            )
        }

    @Test
    fun `REQ-GAME-031 bought item can be worn`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.credit(family, 40)
            client.call(HttpMethod.Post, "$GAME/avatar/items/top.hat/purchase", family.childToken)

            val saved =
                client.call(
                    HttpMethod.Put,
                    "$GAME/members/${family.childId}/avatar",
                    family.childToken,
                    FREE_AVATAR.replace("top.shortFlat", "top.hat"),
                )

            assertEquals(HttpStatusCode.OK, saved.status)
            assertNotNull(checkNotNull(json.decodeFromString<AvatarProfileResponse>(saved.bodyAsText()).data).avatar)
        }

    @Test
    fun `REQ-GAME-031 purchase conflicts and errors map to the right status`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.credit(family, 40)

            suspend fun buy(
                id: String,
                token: String,
            ) = client.call(HttpMethod.Post, "$GAME/avatar/items/$id/purchase", token, invalidRequestExpected = true)

            assertEquals(HttpStatusCode.Created, buy("top.hat", family.childToken).status)
            assertEquals(HttpStatusCode.Conflict, buy("top.hat", family.childToken).status)
            assertEquals(HttpStatusCode.Conflict, buy("top.winterHat1", family.childToken).status)
            assertEquals(HttpStatusCode.Conflict, buy("top.shortFlat", family.childToken).status)
            assertEquals(HttpStatusCode.NotFound, buy("top.unknown", family.childToken).status)
            assertEquals(HttpStatusCode.BadRequest, buy("top", family.childToken).status)
            assertEquals(HttpStatusCode.Forbidden, buy("top.hat", family.parentToken).status)
        }

    @Test
    fun `REQ-GAME-031 purchase requires authentication`() =
        testApplication {
            start()
            setupFamilyWithChild()

            val response = client.call(HttpMethod.Post, "$GAME/avatar/items/top.hat/purchase", null)

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
