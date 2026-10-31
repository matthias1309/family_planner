package de.familyplanner.android.feature.gamification.ui

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.gamification.domain.ChildRewardActions
import de.familyplanner.android.feature.gamification.domain.CookingPointsUseCase
import de.familyplanner.android.feature.gamification.domain.CountPendingRedemptionsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadChildRewardsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadManagedRewardsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadPointsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadTodaySavingsUseCase
import de.familyplanner.android.feature.gamification.domain.ManageRewardActions
import de.familyplanner.android.feature.gamification.fakes.FakePointsRepository
import de.familyplanner.android.feature.gamification.fakes.FakeRewardRepository
import de.familyplanner.android.feature.gamification.fakes.FakeRewardSync
import de.familyplanner.android.feature.gamification.fakes.InMemoryPointsCache
import de.familyplanner.android.feature.gamification.fakes.childSession
import de.familyplanner.android.feature.gamification.fakes.goal
import de.familyplanner.android.feature.gamification.fakes.networkDown
import de.familyplanner.android.feature.gamification.fakes.parentSession
import de.familyplanner.android.feature.gamification.fakes.redemption
import de.familyplanner.android.feature.gamification.fakes.reward
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsEvent
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsViewModel
import de.familyplanner.android.feature.gamification.ui.manage.ManageRewardsEvent
import de.familyplanner.android.feature.gamification.ui.manage.ManageRewardsViewModel
import de.familyplanner.android.feature.gamification.ui.rewards.RewardsEvent
import de.familyplanner.android.feature.gamification.ui.rewards.RewardsViewModel
import de.familyplanner.android.feature.gamification.ui.today.TodaySavingsViewModel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeRewardRepository()
    private val points = FakePointsRepository()
    private val sync = FakeRewardSync()

    private fun rewards() =
        RewardsViewModel(InMemorySessionStore(childSession), LoadChildRewardsUseCase(repository), ChildRewardActions(repository, sync))

    private fun manage() = ManageRewardsViewModel(LoadManagedRewardsUseCase(repository, points), ManageRewardActions(repository))

    @Test
    fun `REQ-GAME-041 rewards view model loads rewards and goal`() {
        val vm = rewards()

        vm.onEvent(RewardsEvent.Refresh)

        assertEquals(
            2,
            vm.state.value.data
                ?.rewards
                ?.size,
        )
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun `REQ-GAME-041 setting and clearing the goal reloads`() {
        val vm = rewards()
        vm.onEvent(RewardsEvent.Refresh)

        vm.onEvent(RewardsEvent.SetGoal(reward("r-1")))
        vm.onEvent(RewardsEvent.ClearGoal)

        assertEquals(listOf("c-1" to "r-1"), repository.setGoalCalls)
        assertEquals(listOf("c-1"), repository.clearGoalCalls)
        assertEquals(3, repository.goalCalls.size)
    }

    @Test
    fun `REQ-GAME-042 redeem needs a confirmation and syncs afterwards`() {
        val vm = rewards()
        vm.onEvent(RewardsEvent.Refresh)

        vm.onEvent(RewardsEvent.AskRedeem(reward("r-2", 80)))
        assertEquals(
            "r-2",
            vm.state.value.confirmRedeem
                ?.id,
        )
        assertTrue(repository.redeemCalls.isEmpty())

        vm.onEvent(RewardsEvent.ConfirmRedeem)

        assertEquals(listOf("r-2"), repository.redeemCalls)
        assertNull(vm.state.value.confirmRedeem)
        assertEquals(1, sync.nowCalls)
    }

    @Test
    fun `REQ-GAME-042 dismissing the confirmation redeems nothing`() {
        val vm = rewards()

        vm.onEvent(RewardsEvent.AskRedeem(reward("r-2")))
        vm.onEvent(RewardsEvent.DismissRedeem)
        vm.onEvent(RewardsEvent.ConfirmRedeem)

        assertTrue(repository.redeemCalls.isEmpty())
    }

    @Test
    fun `REQ-GAME-042 conflict on redeem shows the message and reloads`() {
        val vm = rewards()
        vm.onEvent(RewardsEvent.Refresh)
        repository.redeemResult = ApiResult.Failure(AppError.Conflict("Guthaben reicht nicht"))
        val loadsBefore = repository.goalCalls.size

        vm.onEvent(RewardsEvent.AskRedeem(reward("r-2")))
        vm.onEvent(RewardsEvent.ConfirmRedeem)

        assertEquals(AppError.Conflict("Guthaben reicht nicht"), vm.state.value.error)
        assertNull(vm.state.value.confirmRedeem)
        assertEquals(loadsBefore + 1, repository.goalCalls.size)
        assertEquals(0, sync.nowCalls)
    }

    @Test
    fun `REQ-GAME-041 rewards load failure is shown`() {
        repository.rewardsResult = networkDown
        val vm = rewards()

        vm.onEvent(RewardsEvent.Refresh)

        assertEquals(AppError.Network, vm.state.value.error)
    }

    @Test
    fun `REQ-GAME-043 manage view model loads pending and rewards`() {
        repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1", memberId = "c-2")))
        val vm = manage()

        vm.onEvent(ManageRewardsEvent.Refresh)

        assertEquals(
            listOf("d-1"),
            vm.state.value.data
                ?.pending
                ?.map { it.id },
        )
        assertEquals(
            "Ben",
            vm.state.value.data
                ?.names
                ?.get("c-2"),
        )
    }

    @Test
    fun `REQ-GAME-040 creating a reward closes the dialog and reloads`() {
        val vm = manage()
        vm.onEvent(ManageRewardsEvent.StartCreate)
        vm.onEvent(ManageRewardsEvent.TitleChanged("Kino"))
        vm.onEvent(ManageRewardsEvent.PriceChanged("5a0"))

        vm.onEvent(ManageRewardsEvent.SaveReward)

        assertEquals(listOf<Triple<String, String?, Int>>(Triple("Kino", null, 50)), repository.createCalls)
        assertNull(vm.state.value.editing)
    }

    @Test
    fun `REQ-GAME-040 invalid draft cannot be saved`() {
        val vm = manage()
        vm.onEvent(ManageRewardsEvent.StartCreate)
        vm.onEvent(ManageRewardsEvent.TitleChanged("Kino"))
        vm.onEvent(ManageRewardsEvent.PriceChanged("0"))

        vm.onEvent(ManageRewardsEvent.SaveReward)

        assertTrue(repository.createCalls.isEmpty())
        assertNotNull(vm.state.value.editing)
    }

    @Test
    fun `REQ-GAME-040 server validation error stays in the dialog`() {
        repository.createResult = ApiResult.Failure(AppError.Validation("Titel vergeben"))
        val vm = manage()
        vm.onEvent(ManageRewardsEvent.StartCreate)
        vm.onEvent(ManageRewardsEvent.TitleChanged("Kino"))
        vm.onEvent(ManageRewardsEvent.PriceChanged("50"))

        vm.onEvent(ManageRewardsEvent.SaveReward)

        assertNotNull(vm.state.value.editing)
        assertIs<AppError.Validation>(vm.state.value.actionError)
    }

    @Test
    fun `REQ-GAME-040 editing sends an update`() {
        val vm = manage()
        vm.onEvent(ManageRewardsEvent.StartEdit(reward("r-1", 200)))
        vm.onEvent(ManageRewardsEvent.PriceChanged("250"))

        vm.onEvent(ManageRewardsEvent.SaveReward)

        assertEquals(listOf<List<Any?>>(listOf("r-1", "Freizeitpark", "🎢", 250)), repository.updateCalls)
    }

    @Test
    fun `REQ-GAME-040 archiving needs a confirmation`() {
        val vm = manage()

        vm.onEvent(ManageRewardsEvent.AskArchive(reward("r-1")))
        assertTrue(repository.archiveCalls.isEmpty())
        vm.onEvent(ManageRewardsEvent.ConfirmArchive)

        assertEquals(listOf("r-1"), repository.archiveCalls)
        assertNull(vm.state.value.confirmArchive)
    }

    @Test
    fun `REQ-GAME-043 fulfilling and rejecting a redemption`() {
        val vm = manage()
        val pending = redemption("d-1")

        vm.onEvent(ManageRewardsEvent.Fulfill(pending))
        vm.onEvent(ManageRewardsEvent.StartReject(pending))
        vm.onEvent(ManageRewardsEvent.RejectReasonChanged("Ferien"))
        vm.onEvent(ManageRewardsEvent.ConfirmReject)

        assertEquals(listOf("d-1"), repository.fulfillCalls)
        assertEquals(listOf("d-1" to "Ferien"), repository.rejectCalls)
        assertNull(vm.state.value.rejecting)
    }

    @Test
    fun `REQ-GAME-043 conflict closes the dialog, shows the error and reloads`() {
        repository.fulfillResult = ApiResult.Failure(AppError.Conflict("Schon entschieden"))
        val vm = manage()
        vm.onEvent(ManageRewardsEvent.Refresh)
        val loadsBefore = repository.redemptionsCalls.size

        vm.onEvent(ManageRewardsEvent.Fulfill(redemption("d-1")))

        assertEquals(AppError.Conflict("Schon entschieden"), vm.state.value.error)
        assertTrue(repository.redemptionsCalls.size > loadsBefore)
    }

    @Test
    fun `REQ-GAME-042 family view model counts pending redemptions`() {
        repository.redemptionsResult = ApiResult.Success(listOf(redemption("d-1"), redemption("d-2")))
        val vm =
            FamilyPointsViewModel(
                LoadPointsUseCase(points, repository, InMemoryPointsCache(), InMemorySessionStore(parentSession)),
                CookingPointsUseCase(points),
                CountPendingRedemptionsUseCase(repository),
            )

        vm.onEvent(FamilyPointsEvent.Refresh)

        assertEquals(2, vm.state.value.pendingRedemptions)
    }

    @Test
    fun `REQ-GAME-041 today view model shows the goal of a child`() {
        repository.goalResult = ApiResult.Success(goal())
        val vm = TodaySavingsViewModel(LoadTodaySavingsUseCase(repository, InMemoryPointsCache(), InMemorySessionStore(childSession)))

        assertEquals(goal(), vm.state.value.goal)
    }

    @Test
    fun `REQ-GAME-041 today view model shows nothing for parents`() {
        val vm = TodaySavingsViewModel(LoadTodaySavingsUseCase(repository, InMemoryPointsCache(), InMemorySessionStore(parentSession)))

        assertNull(vm.state.value.goal)
    }
}
