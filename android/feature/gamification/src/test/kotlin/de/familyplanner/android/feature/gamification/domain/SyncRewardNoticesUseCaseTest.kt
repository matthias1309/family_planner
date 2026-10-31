package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.gamification.domain.notice.RedemptionSnapshot
import de.familyplanner.android.feature.gamification.domain.notice.RewardNotice
import de.familyplanner.android.feature.gamification.domain.notice.RewardSyncOutcome
import de.familyplanner.android.feature.gamification.domain.notice.SyncRewardNoticesUseCase
import de.familyplanner.android.feature.gamification.fakes.FakePointsRepository
import de.familyplanner.android.feature.gamification.fakes.FakeRewardNotifier
import de.familyplanner.android.feature.gamification.fakes.FakeRewardRepository
import de.familyplanner.android.feature.gamification.fakes.InMemoryRedemptionNoticeStore
import de.familyplanner.android.feature.gamification.fakes.childSession
import de.familyplanner.android.feature.gamification.fakes.networkDown
import de.familyplanner.android.feature.gamification.fakes.noon
import de.familyplanner.android.feature.gamification.fakes.parentSession
import de.familyplanner.android.feature.gamification.fakes.redemption
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncRewardNoticesUseCaseTest {
    private val repository = FakeRewardRepository()
    private val points = FakePointsRepository()
    private val store = InMemoryRedemptionNoticeStore()
    private val notifier = FakeRewardNotifier()

    private fun sync(session: de.familyplanner.android.core.data.session.Session? = parentSession) =
        SyncRewardNoticesUseCase(
            InMemorySessionStore(session),
            repository,
            points,
            store,
            notifier,
            Clock.fixed(noon.plusSeconds(60), ZoneOffset.UTC),
        )

    @Test
    fun `REQ-GAME-042 parent is notified and the state is stored`() =
        runTest {
            store.owner = "p-1"
            store.stored = RedemptionSnapshot(emptyMap(), noon)
            repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1", memberId = "c-2")))

            val outcome = sync()()

            assertEquals(RewardSyncOutcome.SYNCED, outcome)
            assertEquals(listOf<RewardNotice>(RewardNotice.Requested("d-1", "Ben", "Pizzaabend", "🍕")), notifier.notices)
            assertEquals(mapOf("d-1" to RedemptionStatus.PENDING), store.stored?.statuses)
            assertEquals(noon.plusSeconds(60), store.stored?.syncedAt)
        }

    @Test
    fun `REQ-GAME-042 first sync stores the baseline without notices`() =
        runTest {
            repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1")))

            sync()()

            assertEquals(emptyList(), notifier.notices)
            assertEquals(mapOf("d-1" to RedemptionStatus.PENDING), store.stored?.statuses)
        }

    @Test
    fun `REQ-GAME-042 state of another member counts as baseline`() =
        runTest {
            store.owner = "p-2"
            store.stored = RedemptionSnapshot(emptyMap(), noon)
            repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1")))

            sync()()

            assertEquals(emptyList(), notifier.notices)
        }

    @Test
    fun `REQ-GAME-042 unreachable server keeps the stored state`() =
        runTest {
            val old = RedemptionSnapshot(emptyMap(), noon)
            store.owner = "p-1"
            store.stored = old
            repository.redemptionsResult = networkDown

            assertEquals(RewardSyncOutcome.UNREACHABLE, sync()())
            assertEquals(old, store.stored)
        }

    @Test
    fun `REQ-GAME-042 unauthorized clears the stored state`() =
        runTest {
            repository.redemptionsResult = ApiResult.Failure(AppError.Unauthorized)

            assertEquals(RewardSyncOutcome.SIGNED_OUT, sync()())
            assertEquals(1, store.cleared)
            assertNull(store.stored)
        }

    @Test
    fun `REQ-GAME-042 parent without leaderboard is notified without a name`() =
        runTest {
            store.owner = "p-1"
            store.stored = RedemptionSnapshot(emptyMap(), noon)
            points.leaderboardResult = networkDown
            repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1", memberId = "c-2")))

            sync()()

            assertEquals(null, (notifier.notices.single() as RewardNotice.Requested).childName)
        }

    @Test
    fun `REQ-GAME-043 child is notified about a decision`() =
        runTest {
            store.owner = "c-1"
            store.stored = RedemptionSnapshot(mapOf("d-1" to RedemptionStatus.PENDING), noon)
            repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1", RedemptionStatus.FULFILLED)))

            sync(childSession)()

            assertEquals(listOf<RewardNotice>(RewardNotice.Fulfilled("d-1", "Pizzaabend", "🍕")), notifier.notices)
            assertEquals(listOf<Pair<RedemptionStatus?, Int>>(null to RewardLimits.SYNC_HISTORY_LIMIT), repository.redemptionsCalls)
        }
}
