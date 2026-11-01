package de.familyplanner.android.feature.gamification.domain.avatar

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.gamification.fakes.FakeAvatarRepository
import de.familyplanner.android.feature.gamification.fakes.InMemoryAvatarCache
import de.familyplanner.android.feature.gamification.fakes.freeLook
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class AvatarDirectoryTest {
    private val repository = FakeAvatarRepository()
    private val cache = InMemoryAvatarCache()
    private val directory = AvatarDirectory(repository, cache)
    private val remote = mapOf("c-1" to freeLook)

    @Test
    fun `REQ-GAME-014 refresh publishes looks and writes the cache`() =
        runTest {
            repository.avatarsResult = ApiResult.Success(remote)

            directory.refresh()

            assertEquals(remote, directory.looks.value)
            assertEquals(remote, cache.stored)
        }

    @Test
    fun `REQ-GAME-014 network failure falls back to the cache`() =
        runTest {
            cache.stored = remote
            repository.avatarsResult = ApiResult.Failure(AppError.Network)

            directory.refresh()

            assertEquals(remote, directory.looks.value)
        }

    @Test
    fun `REQ-GAME-014 unauthorized clears the cache and the looks`() =
        runTest {
            repository.avatarsResult = ApiResult.Success(remote)
            directory.refresh()
            repository.avatarsResult = ApiResult.Failure(AppError.Unauthorized)

            directory.refresh()

            assertEquals(1, cache.cleared)
            assertEquals(emptyMap(), directory.looks.value)
        }

    @Test
    fun `REQ-GAME-014 ensureLoaded loads once after success and retries after failure`() =
        runTest {
            repository.avatarsResult = ApiResult.Failure(AppError.Network)
            directory.ensureLoaded()
            repository.avatarsResult = ApiResult.Success(remote)
            directory.ensureLoaded()
            directory.ensureLoaded()

            assertEquals(2, repository.avatarsCalls)
        }

    @Test
    fun `REQ-GAME-030 update merges the saved look and persists it`() =
        runTest {
            directory.update("c-1", freeLook)

            assertEquals(remote, directory.looks.value)
            assertEquals(remote, cache.stored)
        }
}
