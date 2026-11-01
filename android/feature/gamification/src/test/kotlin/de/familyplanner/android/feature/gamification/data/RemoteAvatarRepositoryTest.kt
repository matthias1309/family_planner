package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarSlot
import de.familyplanner.android.feature.gamification.fakes.freeLook
import de.familyplanner.api.client.GamificationApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteAvatarRepositoryTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteAvatarRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteAvatarRepository(retrofit.create(GamificationApi::class.java), json)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(
        code: Int,
        body: String = "",
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .addHeader("Content-Type", "application/json")
            .build(),
    )

    private fun ok(data: String) = """{"success":true,"data":$data}"""

    private val configJson =
        """{"skin":"skin.EDB98A","top":"top.shortFlat","hairColor":"hairColor.2C1B18",""" +
            """"hatColor":"hatColor.262E33","eyes":"eyes.default",""" +
            """"eyebrows":"eyebrows.default","mouth":"mouth.smile","clothes":"clothes.hoodie",""" +
            """"clothesColor":"clothesColor.65C9FF",""" +
            """"background":"background.FFFFFF"}"""

    private fun itemJson(
        slot: String = "top",
        category: String = "HEADWEAR",
    ) = """{"id":"top.winterHat02","slot":"$slot","category":"$category","title":"Bommelmütze","price":30}"""

    @Test
    fun `REQ-GAME-031 catalog is mapped`() =
        runTest {
            enqueue(200, ok("""{"slots":[{"slot":"top","optional":false,"items":[${itemJson()}]}]}"""))

            val catalog = (repository.catalog() as ApiResult.Success).data

            assertEquals(AvatarSlot.TOP, catalog.slots.single().slot)
            assertEquals(
                30,
                catalog.slots
                    .single()
                    .items
                    .single()
                    .price,
            )
        }

    @Test
    fun `REQ-GAME-014 avatars are mapped by member`() =
        runTest {
            enqueue(200, ok("""[{"memberId":"c-1","avatar":$configJson,"updatedAt":"2026-11-01T10:00:00Z"}]"""))

            assertEquals(mapOf("c-1" to freeLook), (repository.avatars() as ApiResult.Success).data)
        }

    @Test
    fun `REQ-GAME-030 profile without avatar has no look`() =
        runTest {
            enqueue(200, ok("""{"memberId":"c-1","ownedItemIds":["top.bigHair"]}"""))

            val profile = (repository.profile("c-1") as ApiResult.Success).data

            assertNull(profile.look)
            assertEquals(setOf("top.bigHair"), profile.owned)
        }

    @Test
    fun `REQ-GAME-030 save puts the configuration`() =
        runTest {
            enqueue(200, ok("""{"memberId":"c-1","ownedItemIds":[],"avatar":$configJson}"""))

            val result = repository.save("c-1", freeLook)

            assertEquals(freeLook, (result as ApiResult.Success).data.look)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertTrue(
                request.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"skin\":\"skin.EDB98A\""),
            )
        }

    @Test
    fun `REQ-GAME-031 purchase returns item and balance`() =
        runTest {
            enqueue(201, ok("""{"item":${itemJson()},"balance":12}"""))

            val purchase = (repository.purchase("top.winterHat02") as ApiResult.Success).data

            assertEquals(12, purchase.balance)
            assertEquals("Bommelmütze", purchase.item.title)
        }

    @Test
    fun `REQ-GAME-031 purchase conflict carries the server text`() =
        runTest {
            enqueue(409, """{"success":false,"error":{"code":"CONFLICT","message":"Nicht genug Guthaben: 5 von 30 Punkten"}}""")

            val failure = assertIs<ApiResult.Failure>(repository.purchase("top.winterHat02"))

            assertEquals(AppError.Conflict("Nicht genug Guthaben: 5 von 30 Punkten"), failure.error)
        }

    @Test
    fun `REQ-GAME-031 unknown category is a server error`() =
        runTest {
            val unknown = """{"slots":[{"slot":"top","optional":false,"items":[${itemJson(category = "SPACE")}]}]}"""
            enqueue(200, ok(unknown))

            assertEquals(AppError.Server, assertIs<ApiResult.Failure>(repository.catalog()).error)
        }
}
