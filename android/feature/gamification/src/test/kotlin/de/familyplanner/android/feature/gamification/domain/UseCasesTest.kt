package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.gamification.fakes.FakePointsRepository
import de.familyplanner.android.feature.gamification.fakes.InMemoryPointsCache
import de.familyplanner.android.feature.gamification.fakes.badge
import de.familyplanner.android.feature.gamification.fakes.childSession
import de.familyplanner.android.feature.gamification.fakes.networkDown
import de.familyplanner.android.feature.gamification.fakes.noon
import de.familyplanner.android.feature.gamification.fakes.overview
import de.familyplanner.android.feature.gamification.fakes.parentSession
import de.familyplanner.android.feature.gamification.fakes.serverDown
import de.familyplanner.android.feature.gamification.fakes.snapshot
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UseCasesTest {
    private val repository = FakePointsRepository()
    private val cache = InMemoryPointsCache()
    private val clock = Clock.fixed(noon, ZoneOffset.UTC)

    private fun load(session: de.familyplanner.android.core.data.session.Session? = childSession) =
        LoadPointsUseCase(repository, cache, InMemorySessionStore(session), clock)

    @Test
    fun `REQ-GAME-012 child loads own account and leaderboard and stores them`() =
        runTest {
            val result = load()()

            val fresh = assertIs<PointsLoad.Fresh>(result)
            assertEquals(listOf("c-1" to PointsLimits.HISTORY_LIMIT), repository.accountCalls)
            assertEquals(fresh.snapshot, cache.stored)
            assertEquals(noon, fresh.snapshot.syncedAt)
        }

    @Test
    fun `REQ-GAME-014 parent loads only the leaderboard`() =
        runTest {
            val fresh = assertIs<PointsLoad.Fresh>(load(parentSession)())

            assertNull(fresh.snapshot.account)
            assertEquals(emptyList(), repository.accountCalls)
        }

    @Test
    fun `REQ-GAME-012 missing session fails as unauthorized`() =
        runTest {
            assertEquals(AppError.Unauthorized, assertIs<PointsLoad.Failed>(load(null)()).error)
        }

    @Test
    fun `REQ-GAME-012 offline shows the cached snapshot`() =
        runTest {
            cache.write("c-1", snapshot())
            repository.accountResult = networkDown

            val cached = assertIs<PointsLoad.Cached>(load()())

            assertEquals(AppError.Network, cached.error)
        }

    @Test
    fun `REQ-GAME-012 cached snapshot of another member is not used`() =
        runTest {
            cache.write("p-1", snapshot())
            repository.accountResult = networkDown

            assertIs<PointsLoad.Failed>(load()())
        }

    @Test
    fun `REQ-GAME-012 a failed leaderboard does not store half a snapshot`() =
        runTest {
            repository.leaderboardResult = serverDown

            assertIs<PointsLoad.Failed>(load()())
            assertEquals(0, cache.writes)
        }

    @Test
    fun `REQ-GAME-012 unauthorized clears the local snapshot`() =
        runTest {
            cache.write("c-1", snapshot())
            repository.leaderboardResult = ApiResult.Failure(AppError.Unauthorized)

            assertIs<PointsLoad.Failed>(load()())
            assertEquals(1, cache.cleared)
            assertNull(cache.stored)
        }

    @Test
    fun `REQ-GAME-013 booking trims the reason and sends the signed amount`() =
        runTest {
            BookPointsUseCase(repository)("c-1", -2, " Fehler ")

            assertEquals(listOf(Triple("c-1", -2, "Fehler")), repository.bookCalls)
        }

    @Test
    fun `REQ-GAME-013 invalid booking is not sent`() =
        runTest {
            val result = BookPointsUseCase(repository)("c-1", 0, "x")

            assertIs<AppError.Validation>(assertIs<ApiResult.Failure>(result).error)
            assertEquals(emptyList(), repository.bookCalls)
        }

    @Test
    fun `REQ-GAME-011 saving cooking points validates before sending`() =
        runTest {
            val useCase = CookingPointsUseCase(repository)

            assertIs<ApiResult.Failure>(useCase.save(21))
            assertEquals(emptyList(), repository.updateCalls)
            assertEquals(ApiResult.Success(5), useCase.save(5))
            assertEquals(ApiResult.Success(3), useCase.load())
        }

    @Test
    fun `REQ-GAME-013 child account loads with the history limit`() =
        runTest {
            LoadChildAccountUseCase(repository)("c-9")

            assertEquals(listOf("c-9" to PointsLimits.HISTORY_LIMIT), repository.accountCalls)
        }

    @Test
    fun `REQ-GAME-021 child loads own badges and stores them`() =
        runTest {
            val fresh = assertIs<PointsLoad.Fresh>(load()())

            assertEquals(listOf("c-1"), repository.badgesCalls)
            assertEquals(
                2,
                fresh.snapshot.badges
                    ?.items
                    ?.size,
            )
            assertEquals(fresh.snapshot, cache.stored)
        }

    @Test
    fun `REQ-GAME-020 fresh load reports badges earned since the stored snapshot`() =
        runTest {
            cache.write("c-1", snapshot(badges = overview(badge("first-task", earned = true), badge("tasks-10"))))
            repository.badgesResult = ApiResult.Success(overview(badge("first-task", earned = true), badge("tasks-10", earned = true)))

            val fresh = assertIs<PointsLoad.Fresh>(load()())

            assertEquals(listOf("tasks-10"), fresh.newBadges.map { it.id })
        }

    @Test
    fun `REQ-GAME-020 first load without stored badges reports nothing`() =
        runTest {
            cache.write("c-1", snapshot(badges = null))
            repository.badgesResult = ApiResult.Success(overview(badge("first-task", earned = true)))

            assertEquals(emptyList(), assertIs<PointsLoad.Fresh>(load()()).newBadges)
        }

    @Test
    fun `REQ-GAME-021 parent does not load badges`() =
        runTest {
            val fresh = assertIs<PointsLoad.Fresh>(load(parentSession)())

            assertEquals(emptyList(), repository.badgesCalls)
            assertNull(fresh.snapshot.badges)
        }

    @Test
    fun `REQ-GAME-021 failed badges do not store half a snapshot`() =
        runTest {
            repository.badgesResult = serverDown

            assertIs<PointsLoad.Failed>(load()())
            assertEquals(0, cache.writes)
        }

    @Test
    fun `REQ-GAME-021 badges of another member load without cache`() =
        runTest {
            val result = LoadBadgesUseCase(repository)("c-2")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(listOf("c-2"), repository.badgesCalls)
            assertEquals(0, cache.writes)
        }
}
