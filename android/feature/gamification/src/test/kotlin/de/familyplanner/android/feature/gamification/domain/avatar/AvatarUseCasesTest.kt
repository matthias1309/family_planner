package de.familyplanner.android.feature.gamification.domain.avatar

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.gamification.fakes.FakeAvatarRepository
import de.familyplanner.android.feature.gamification.fakes.FakeRewardRepository
import de.familyplanner.android.feature.gamification.fakes.InMemoryAvatarCache
import de.familyplanner.android.feature.gamification.fakes.freeLook
import de.familyplanner.android.feature.gamification.fakes.sampleCatalog
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AvatarUseCasesTest {
    private val avatars = FakeAvatarRepository()
    private val rewards = FakeRewardRepository()
    private val load = LoadAvatarEditorUseCase(avatars, rewards)

    @Test
    fun `REQ-GAME-030 editor loads catalog profile and goal and starts with the default look`() =
        runTest {
            val data = (load("c-1") as ApiResult.Success).data

            assertEquals(freeLook, data.startLook)
            assertEquals(sampleCatalog(), data.catalog)
        }

    @Test
    fun `REQ-GAME-030 editor starts with the saved look`() =
        runTest {
            val saved = freeLook.copy(skin = "skin.AE5D29")
            avatars.profileResult = ApiResult.Success(AvatarProfile("c-1", saved, emptySet()))

            assertEquals(saved, (load("c-1") as ApiResult.Success).data.startLook)
        }

    @Test
    fun `REQ-GAME-030 first failure wins`() =
        runTest {
            avatars.catalogResult = ApiResult.Failure(AppError.Network)
            rewards.goalResult = ApiResult.Failure(AppError.Server)

            assertEquals(AppError.Network, (load("c-1") as ApiResult.Failure).error)
        }

    @Test
    fun `REQ-GAME-030 catalog without a free required item is a server error`() =
        runTest {
            avatars.catalogResult =
                ApiResult.Success(
                    AvatarCatalog(
                        sampleCatalog().slots.map {
                            if (it.slot ==
                                AvatarSlot.EYES
                            ) {
                                it.copy(items = emptyList())
                            } else {
                                it
                            }
                        },
                    ),
                )

            assertEquals(AppError.Server, assertIs<ApiResult.Failure>(load("c-1")).error)
        }

    @Test
    fun `REQ-GAME-030 save updates the directory`() =
        runTest {
            val directory = AvatarDirectory(avatars, InMemoryAvatarCache())

            AvatarActions(avatars, directory).save("c-1", freeLook)

            assertEquals(mapOf("c-1" to freeLook), directory.looks.value)
        }

    @Test
    fun `REQ-GAME-030 failed save leaves the directory untouched`() =
        runTest {
            avatars.saveResult = ApiResult.Failure(AppError.Conflict("x"))
            val directory = AvatarDirectory(avatars, InMemoryAvatarCache())

            AvatarActions(avatars, directory).save("c-1", freeLook)

            assertEquals(emptyMap(), directory.looks.value)
        }
}
