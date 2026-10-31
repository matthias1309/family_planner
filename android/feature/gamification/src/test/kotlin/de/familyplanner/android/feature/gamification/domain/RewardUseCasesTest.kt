package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.gamification.fakes.FakePointsRepository
import de.familyplanner.android.feature.gamification.fakes.FakeRewardRepository
import de.familyplanner.android.feature.gamification.fakes.FakeRewardSync
import de.familyplanner.android.feature.gamification.fakes.InMemoryPointsCache
import de.familyplanner.android.feature.gamification.fakes.childSession
import de.familyplanner.android.feature.gamification.fakes.goal
import de.familyplanner.android.feature.gamification.fakes.networkDown
import de.familyplanner.android.feature.gamification.fakes.parentSession
import de.familyplanner.android.feature.gamification.fakes.redemption
import de.familyplanner.android.feature.gamification.fakes.snapshot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardUseCasesTest {
    private val repository = FakeRewardRepository()
    private val points = FakePointsRepository()
    private val sync = FakeRewardSync()

    @Test
    fun `REQ-GAME-041 child rewards load rewards, goal and redemptions`() =
        runTest {
            repository.goalResult = ApiResult.Success(goal())
            repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1")))

            val data = (LoadChildRewardsUseCase(repository)("c-1") as ApiResult.Success).data

            assertEquals(2, data.rewards.size)
            assertEquals(goal(), data.goal)
            assertEquals(listOf("d-1"), data.redemptions.map { it.id })
            assertEquals(listOf<Pair<RedemptionStatus?, Int>>(null to RewardLimits.CHILD_HISTORY_LIMIT), repository.redemptionsCalls)
        }

    @Test
    fun `REQ-GAME-041 child rewards fail on first error`() =
        runTest {
            repository.goalResult = networkDown

            assertEquals(networkDown, LoadChildRewardsUseCase(repository)("c-1"))
        }

    @Test
    fun `REQ-GAME-042 redeem requests a sync after success`() =
        runTest {
            ChildRewardActions(repository, sync).redeem("r-2")

            assertEquals(1, sync.nowCalls)
            assertEquals(listOf("r-2"), repository.redeemCalls)
        }

    @Test
    fun `REQ-GAME-042 failed redeem does not sync`() =
        runTest {
            repository.redeemResult = ApiResult.Failure(AppError.Conflict("Guthaben reicht nicht"))

            val result = ChildRewardActions(repository, sync).redeem("r-2")

            assertIs<ApiResult.Failure>(result)
            assertEquals(0, sync.nowCalls)
        }

    @Test
    fun `REQ-GAME-040 save creates without id and updates with id`() =
        runTest {
            val actions = ManageRewardActions(repository)

            actions.save(RewardDraft(title = "Kino", priceText = "50"))
            actions.save(RewardDraft(rewardId = "r-1", title = "Zoo", priceText = "60"))

            assertEquals(listOf<Triple<String, String?, Int>>(Triple("Kino", null, 50)), repository.createCalls)
            assertEquals(listOf<List<Any?>>(listOf("r-1", "Zoo", null, 60)), repository.updateCalls)
        }

    @Test
    fun `REQ-GAME-040 save trims and sends empty symbol as null`() =
        runTest {
            ManageRewardActions(repository).save(RewardDraft(title = " Kino ", emoji = " 🎬 ", priceText = "50"))
            ManageRewardActions(repository).save(RewardDraft(title = "Zoo", emoji = "  ", priceText = "60"))

            assertEquals(listOf(Triple("Kino", "🎬", 50), Triple("Zoo", null, 60)), repository.createCalls)
        }

    @Test
    fun `REQ-GAME-040 invalid draft is not sent`() =
        runTest {
            val result = ManageRewardActions(repository).save(RewardDraft(title = " ", priceText = "50"))

            assertIs<AppError.Validation>((result as ApiResult.Failure).error)
            assertTrue(repository.createCalls.isEmpty())
        }

    @Test
    fun `REQ-GAME-043 reject validates the reason`() =
        runTest {
            val actions = ManageRewardActions(repository)

            assertIs<ApiResult.Failure>(actions.reject("d-1", " "))
            assertTrue(repository.rejectCalls.isEmpty())

            actions.reject("d-1", " Ferien ")
            assertEquals(listOf("d-1" to "Ferien"), repository.rejectCalls)
        }

    @Test
    fun `REQ-GAME-043 managed rewards load pending with names`() =
        runTest {
            repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1", memberId = "c-2")))

            val data = (LoadManagedRewardsUseCase(repository, points)() as ApiResult.Success).data

            assertEquals("Ben", data.names["c-2"])
            assertEquals(
                listOf<Pair<RedemptionStatus?, Int>>(RedemptionStatus.PENDING to RewardLimits.PENDING_LIMIT),
                repository.redemptionsCalls,
            )
        }

    @Test
    fun `REQ-GAME-043 managed rewards without leaderboard use no names`() =
        runTest {
            points.leaderboardResult = networkDown

            val data = (LoadManagedRewardsUseCase(repository, points)() as ApiResult.Success).data

            assertTrue(data.names.isEmpty())
        }

    @Test
    fun `REQ-GAME-042 pending count`() =
        runTest {
            repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1"), redemption("d-2")))

            assertEquals(ApiResult.Success(2), CountPendingRedemptionsUseCase(repository)())
        }

    @Test
    fun `REQ-GAME-041 today savings uses server and falls back to cache`() =
        runTest {
            val cache =
                InMemoryPointsCache().apply {
                    owner = "c-1"
                    stored = snapshot(savingsGoal = goal(balance = 10))
                }
            val useCase = LoadTodaySavingsUseCase(repository, cache, InMemorySessionStore(childSession))

            repository.goalResult = ApiResult.Success(goal(balance = 120))
            assertEquals(120, useCase()?.balance)

            repository.goalResult = networkDown
            assertEquals(10, useCase()?.balance)
        }

    @Test
    fun `REQ-GAME-041 parent has no today savings`() =
        runTest {
            val useCase = LoadTodaySavingsUseCase(repository, InMemoryPointsCache(), InMemorySessionStore(parentSession))

            assertNull(useCase())
            assertTrue(repository.goalCalls.isEmpty())
        }
}
