package de.familyplanner.android.feature.gamification.ui.avatar

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarActions
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarDirectory
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarProfile
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarPurchase
import de.familyplanner.android.feature.gamification.domain.avatar.LoadAvatarEditorUseCase
import de.familyplanner.android.feature.gamification.domain.avatar.PurchaseOption
import de.familyplanner.android.feature.gamification.fakes.FakeAvatarRepository
import de.familyplanner.android.feature.gamification.fakes.FakeRewardRepository
import de.familyplanner.android.feature.gamification.fakes.InMemoryAvatarCache
import de.familyplanner.android.feature.gamification.fakes.childSession
import de.familyplanner.android.feature.gamification.fakes.freeLook
import de.familyplanner.android.feature.gamification.fakes.goal
import de.familyplanner.android.feature.gamification.fakes.parentSession
import de.familyplanner.android.feature.gamification.fakes.reward
import de.familyplanner.android.feature.gamification.fakes.sampleCatalog
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val avatars = FakeAvatarRepository()
    private val rewards = FakeRewardRepository().apply { goalResult = ApiResult.Success(goal(reward = null, balance = 40)) }
    private val directory = AvatarDirectory(avatars, InMemoryAvatarCache())

    private fun editor(session: de.familyplanner.android.core.data.session.Session = childSession) =
        AvatarEditorViewModel(InMemorySessionStore(session), LoadAvatarEditorUseCase(avatars, rewards), AvatarActions(avatars, directory))

    private fun loaded(): AvatarEditorViewModel = editor().also { it.onEvent(AvatarEditorEvent.Refresh) }

    private fun AvatarEditorViewModel.pick(itemId: String) {
        val rubric = state.value.rubrics.first { r -> r.items.any { it.id == itemId } }
        onEvent(AvatarEditorEvent.Pick(rubric, rubric.items.first { it.id == itemId }))
    }

    @Test
    fun `REQ-GAME-030 child without avatar gets default draft`() {
        val vm = loaded()

        assertEquals(freeLook, vm.state.value.draft)
        assertEquals(40, vm.state.value.balance)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `REQ-GAME-031 picking item previews it`() {
        val vm = loaded()

        vm.pick("top.bigHair")

        assertEquals(
            "top.bigHair",
            vm.state.value.draft
                ?.top,
        )
        assertEquals(PurchaseOption.Buyable(20), vm.state.value.purchaseOption)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `REQ-GAME-031 purchase lowers balance and owns item`() {
        val vm = loaded()
        vm.pick("top.bigHair")
        avatars.purchaseResult =
            ApiResult.Success(AvatarPurchase(sampleCatalog().slots.flatMap { it.items }.first { it.id == "top.bigHair" }, 20))

        vm.onEvent(AvatarEditorEvent.AskPurchase)
        vm.onEvent(AvatarEditorEvent.ConfirmPurchase)

        assertEquals(20, vm.state.value.balance)
        assertTrue("top.bigHair" in vm.state.value.owned)
        assertEquals(listOf("top.bigHair"), avatars.purchaseCalls)
    }

    @Test
    fun `REQ-GAME-031 too expensive item cannot be asked to purchase`() {
        rewards.goalResult = ApiResult.Success(goal(reward = null, balance = 5))
        val vm = loaded()

        vm.pick("top.bigHair")
        vm.onEvent(AvatarEditorEvent.AskPurchase)

        assertEquals(PurchaseOption.Missing(15), vm.state.value.purchaseOption)
        assertEquals(null, vm.state.value.confirmPurchase)
    }

    @Test
    fun `REQ-GAME-031 conflict reloads and keeps draft`() {
        val vm = loaded()
        vm.pick("top.bigHair")
        avatars.purchaseResult = ApiResult.Failure(AppError.Conflict("Nicht genug Guthaben: 5 von 20 Punkten"))
        rewards.goalResult = ApiResult.Success(goal(reward = null, balance = 5))

        vm.onEvent(AvatarEditorEvent.AskPurchase)
        vm.onEvent(AvatarEditorEvent.ConfirmPurchase)

        assertEquals(
            "top.bigHair",
            vm.state.value.draft
                ?.top,
        )
        assertEquals(5, vm.state.value.balance)
        assertEquals(AppError.Conflict("Nicht genug Guthaben: 5 von 20 Punkten"), vm.state.value.error)
    }

    @Test
    fun `REQ-GAME-030 save updates directory and marks saved`() {
        val vm = loaded()

        vm.onEvent(AvatarEditorEvent.Save)

        assertTrue(vm.state.value.saved)
        assertEquals(freeLook, directory.looks.value["c-1"])
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `REQ-GAME-041 confirm shows goal warning`() {
        rewards.goalResult = ApiResult.Success(goal(reward("r-1", price = 200), balance = 120))
        val vm = loaded()

        vm.pick("top.bigHair")
        vm.onEvent(AvatarEditorEvent.AskPurchase)

        assertTrue(vm.state.value.showGoalWarning)
    }

    @Test
    fun `REQ-GAME-030 parent gets forbidden and nothing is loaded`() {
        val vm = editor(parentSession)

        vm.onEvent(AvatarEditorEvent.Refresh)

        assertEquals(AppError.Forbidden, vm.state.value.error)
        assertEquals(null, vm.state.value.data)
    }

    @Test
    fun `REQ-GAME-030 saved profile is kept after save`() {
        avatars.profileResult = ApiResult.Success(AvatarProfile("c-1", freeLook, emptySet()))
        val vm = loaded()

        assertFalse(vm.state.value.canSave)
    }
}
