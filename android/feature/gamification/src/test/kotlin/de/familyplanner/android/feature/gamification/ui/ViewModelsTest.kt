package de.familyplanner.android.feature.gamification.ui

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.gamification.domain.BookPointsUseCase
import de.familyplanner.android.feature.gamification.domain.CookingPointsUseCase
import de.familyplanner.android.feature.gamification.domain.CountPendingRedemptionsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadBadgesUseCase
import de.familyplanner.android.feature.gamification.domain.LoadChildAccountUseCase
import de.familyplanner.android.feature.gamification.domain.LoadPointsUseCase
import de.familyplanner.android.feature.gamification.fakes.FakePointsRepository
import de.familyplanner.android.feature.gamification.fakes.FakeRewardRepository
import de.familyplanner.android.feature.gamification.fakes.InMemoryPointsCache
import de.familyplanner.android.feature.gamification.fakes.badge
import de.familyplanner.android.feature.gamification.fakes.childSession
import de.familyplanner.android.feature.gamification.fakes.networkDown
import de.familyplanner.android.feature.gamification.fakes.overview
import de.familyplanner.android.feature.gamification.fakes.parentSession
import de.familyplanner.android.feature.gamification.fakes.serverDown
import de.familyplanner.android.feature.gamification.fakes.snapshot
import de.familyplanner.android.feature.gamification.ui.account.BookingDraft
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountArgs
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountEvent
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountViewModel
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesArgs
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesEvent
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesViewModel
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsEvent
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsViewModel
import de.familyplanner.android.feature.gamification.ui.me.MeEvent
import de.familyplanner.android.feature.gamification.ui.me.MeViewModel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakePointsRepository()
    private val cache = InMemoryPointsCache()

    private fun me(): MeViewModel {
        val sessions = InMemorySessionStore(childSession)
        return MeViewModel(LoadPointsUseCase(repository, FakeRewardRepository(), cache, sessions), sessions)
    }

    private fun family() =
        FamilyPointsViewModel(
            LoadPointsUseCase(repository, FakeRewardRepository(), cache, InMemorySessionStore(parentSession)),
            CookingPointsUseCase(repository),
            CountPendingRedemptionsUseCase(FakeRewardRepository()),
        )

    private fun account() =
        ChildAccountViewModel(
            ChildAccountArgs("c-1", "Lea"),
            LoadChildAccountUseCase(repository),
            LoadBadgesUseCase(repository),
            BookPointsUseCase(repository),
        )

    @Test
    fun `REQ-GAME-012 me view model shows totals, rank and history`() {
        val vm = me()
        assertNull(vm.state.value.snapshot)

        vm.onEvent(MeEvent.Refresh)

        val state = vm.state.value
        assertEquals(42, state.snapshot?.account?.collected)
        assertEquals(2, state.myRank?.rank)
        assertEquals(
            1,
            state.snapshot
                ?.account
                ?.entries
                ?.size,
        )
        assertFalse(state.isOffline)
    }

    @Test
    fun `REQ-GAME-012 me view model marks a cached snapshot as offline`() {
        cache.owner = "c-1"
        cache.stored = snapshot()
        repository.leaderboardResult = networkDown
        val vm = me()

        vm.onEvent(MeEvent.Refresh)

        assertTrue(vm.state.value.isOffline)
        assertNotNull(
            vm.state.value.snapshot
                ?.syncedAt,
        )
    }

    @Test
    fun `REQ-GAME-012 failed refresh keeps the previous snapshot`() {
        val vm = me()
        vm.onEvent(MeEvent.Refresh)
        repository.leaderboardResult = serverDown
        cache.stored = null

        vm.onEvent(MeEvent.Refresh)

        assertNotNull(vm.state.value.snapshot)
        assertEquals(AppError.Server, vm.state.value.error)
    }

    @Test
    fun `REQ-GAME-011 parent changes cooking points`() {
        val vm = family()
        vm.onEvent(FamilyPointsEvent.Refresh)
        assertEquals(3, vm.state.value.cookingPoints)

        vm.onEvent(FamilyPointsEvent.EditCookingPoints)
        vm.onEvent(FamilyPointsEvent.CookingPointsChanged(5))
        vm.onEvent(FamilyPointsEvent.SaveCookingPoints)

        assertEquals(listOf(5), repository.updateCalls)
        assertNull(vm.state.value.editingCookingPoints)
        assertEquals(5, vm.state.value.cookingPoints)
    }

    @Test
    fun `REQ-GAME-011 cooking points stay hidden when settings fail`() {
        repository.cookingResult = serverDown
        val vm = family()

        vm.onEvent(FamilyPointsEvent.Refresh)
        vm.onEvent(FamilyPointsEvent.EditCookingPoints)

        assertNull(vm.state.value.cookingPoints)
        assertNull(vm.state.value.editingCookingPoints)
        assertEquals(
            3,
            vm.state.value.snapshot
                ?.leaderboard
                ?.size,
        )
    }

    @Test
    fun `REQ-GAME-011 cooking points are limited and a failed save keeps the dialog`() {
        repository.updateResult = serverDown
        val vm = family()
        vm.onEvent(FamilyPointsEvent.Refresh)
        vm.onEvent(FamilyPointsEvent.EditCookingPoints)

        vm.onEvent(FamilyPointsEvent.CookingPointsChanged(99))
        assertEquals(20, vm.state.value.editingCookingPoints)
        vm.onEvent(FamilyPointsEvent.SaveCookingPoints)

        assertEquals(AppError.Server, vm.state.value.actionError)
        assertEquals(20, vm.state.value.editingCookingPoints)
        vm.onEvent(FamilyPointsEvent.DismissDialog)
        assertNull(vm.state.value.editingCookingPoints)
    }

    @Test
    fun `REQ-GAME-013 child account books a debit and reloads`() {
        val vm = account()
        vm.onEvent(ChildAccountEvent.Refresh)

        vm.onEvent(ChildAccountEvent.StartBooking)
        vm.onEvent(ChildAccountEvent.BookingCreditChanged(false))
        vm.onEvent(ChildAccountEvent.BookingAmountChanged("2"))
        vm.onEvent(ChildAccountEvent.BookingReasonChanged("Fehler"))
        vm.onEvent(ChildAccountEvent.SubmitBooking)

        assertEquals(listOf(Triple("c-1", -2, "Fehler")), repository.bookCalls)
        assertNull(vm.state.value.booking)
        assertEquals(2, repository.accountCalls.size)
    }

    @Test
    fun `REQ-GAME-013 booking draft accepts digits only and limits the reason`() {
        val vm = account()
        vm.onEvent(ChildAccountEvent.StartBooking)

        vm.onEvent(ChildAccountEvent.BookingAmountChanged("1a2"))
        vm.onEvent(ChildAccountEvent.BookingReasonChanged("x".repeat(250)))

        assertEquals(
            "12",
            vm.state.value.booking
                ?.amountText,
        )
        assertEquals(
            200,
            vm.state.value.booking
                ?.reason
                ?.length,
        )
        assertTrue(
            vm.state.value.booking
                ?.canSubmit == true,
        )
        assertFalse(BookingDraft(amountText = "0", reason = "x").canSubmit)
        assertFalse(BookingDraft(amountText = "", reason = "x").canSubmit)
    }

    @Test
    fun `REQ-GAME-013 server validation keeps the dialog open`() {
        repository.bookResult = ApiResult.Failure(AppError.Validation("Betrag falsch"))
        val vm = account()
        vm.onEvent(ChildAccountEvent.StartBooking)
        vm.onEvent(ChildAccountEvent.BookingAmountChanged("5"))
        vm.onEvent(ChildAccountEvent.BookingReasonChanged("x"))

        vm.onEvent(ChildAccountEvent.SubmitBooking)

        assertNotNull(vm.state.value.booking)
        assertIs<AppError.Validation>(vm.state.value.bookingError)
    }

    @Test
    fun `REQ-GAME-013 other booking errors close the dialog and show on the page`() {
        repository.bookResult = ApiResult.Failure(AppError.Forbidden)
        val vm = account()
        vm.onEvent(ChildAccountEvent.StartBooking)
        vm.onEvent(ChildAccountEvent.BookingAmountChanged("5"))
        vm.onEvent(ChildAccountEvent.BookingReasonChanged("x"))

        vm.onEvent(ChildAccountEvent.SubmitBooking)

        assertNull(vm.state.value.booking)
        assertEquals(AppError.Forbidden, vm.state.value.error)
        vm.onEvent(ChildAccountEvent.DismissBooking)
    }

    @Test
    fun `REQ-GAME-012 child account shows load errors`() {
        repository.accountResult = networkDown
        val vm = account()

        vm.onEvent(ChildAccountEvent.Refresh)

        assertEquals(AppError.Network, vm.state.value.error)
        assertNull(vm.state.value.account)
    }

    @Test
    fun `REQ-GAME-011 a failed settings load keeps the known cooking points`() {
        val vm = family()
        vm.onEvent(FamilyPointsEvent.Refresh)
        repository.cookingResult = serverDown

        vm.onEvent(FamilyPointsEvent.Refresh)

        assertEquals(3, vm.state.value.cookingPoints)
    }

    @Test
    fun `REQ-GAME-011 offline cooking points cannot be edited or saved`() {
        val vm = family()
        vm.onEvent(FamilyPointsEvent.Refresh)
        repository.leaderboardResult = networkDown
        cache.owner = "p-1"
        cache.stored = snapshot(account = null)
        vm.onEvent(FamilyPointsEvent.Refresh)
        assertTrue(vm.state.value.isOffline)

        vm.onEvent(FamilyPointsEvent.EditCookingPoints)
        vm.onEvent(FamilyPointsEvent.SaveCookingPoints)

        assertNull(vm.state.value.editingCookingPoints)
        assertEquals(emptyList(), repository.updateCalls)
    }

    @Test
    fun `REQ-GAME-013 too high amount is flagged and never sent`() {
        val vm = account()
        vm.onEvent(ChildAccountEvent.StartBooking)
        vm.onEvent(ChildAccountEvent.BookingAmountChanged("5000"))
        vm.onEvent(ChildAccountEvent.BookingReasonChanged("x"))

        vm.onEvent(ChildAccountEvent.SubmitBooking)

        assertTrue(
            vm.state.value.booking
                ?.amountTooHigh == true,
        )
        assertEquals(emptyList(), repository.bookCalls)
    }

    @Test
    fun `REQ-GAME-020 me view model shows new badges until dismissed`() {
        cache.owner = "c-1"
        cache.stored = snapshot(badges = overview(badge("first-task", earned = true), badge("tasks-10")))
        repository.badgesResult = ApiResult.Success(overview(badge("first-task", earned = true), badge("tasks-10", earned = true)))
        val vm = me()

        vm.onEvent(MeEvent.Refresh)
        assertEquals(
            listOf("tasks-10"),
            vm.state.value.newBadges
                .map { it.id },
        )

        cache.stored = snapshot(badges = overview(badge("first-task", earned = true), badge("tasks-10")))
        vm.onEvent(MeEvent.Refresh)
        assertEquals(
            listOf("tasks-10"),
            vm.state.value.newBadges
                .map { it.id },
        )

        vm.onEvent(MeEvent.DismissNewBadges)
        assertTrue(
            vm.state.value.newBadges
                .isEmpty(),
        )
    }

    @Test
    fun `REQ-GAME-021 member badges view model loads the member`() {
        val vm = MemberBadgesViewModel(MemberBadgesArgs("c-2", "Ben"), LoadBadgesUseCase(repository))

        vm.onEvent(MemberBadgesEvent.Refresh)

        assertEquals(listOf("c-2"), repository.badgesCalls)
        assertEquals(
            2,
            vm.state.value.overview
                ?.items
                ?.size,
        )
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `REQ-GAME-021 member badges view model shows load errors`() {
        repository.badgesResult = networkDown
        val vm = MemberBadgesViewModel(MemberBadgesArgs("c-2", "Ben"), LoadBadgesUseCase(repository))

        vm.onEvent(MemberBadgesEvent.Refresh)

        assertEquals(AppError.Network, vm.state.value.error)
        assertNull(vm.state.value.overview)
    }

    @Test
    fun `REQ-GAME-021 child account view model shows badges with progress`() {
        val vm = account()

        vm.onEvent(ChildAccountEvent.Refresh)

        assertEquals(
            7,
            vm.state.value.badges
                ?.items
                ?.last()
                ?.progress
                ?.current,
        )
    }

    @Test
    fun `REQ-GAME-021 child account keeps the account when badges fail`() {
        repository.badgesResult = serverDown
        val vm = account()

        vm.onEvent(ChildAccountEvent.Refresh)

        assertNotNull(vm.state.value.account)
        assertNull(vm.state.value.badges)
        assertEquals(AppError.Server, vm.state.value.error)
    }
}
