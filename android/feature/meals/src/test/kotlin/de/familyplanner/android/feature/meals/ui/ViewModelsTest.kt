package de.familyplanner.android.feature.meals.ui

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.meals.domain.attendance.AddExceptionUseCase
import de.familyplanner.android.feature.meals.domain.attendance.AttendancePattern
import de.familyplanner.android.feature.meals.domain.attendance.DeleteExceptionUseCase
import de.familyplanner.android.feature.meals.domain.attendance.ExceptionUseCases
import de.familyplanner.android.feature.meals.domain.attendance.LoadDeadlineSettingsUseCase
import de.familyplanner.android.feature.meals.domain.attendance.LoadExceptionsUseCase
import de.familyplanner.android.feature.meals.domain.attendance.LoadProfilesUseCase
import de.familyplanner.android.feature.meals.domain.attendance.LoadWeekOverviewUseCase
import de.familyplanner.android.feature.meals.domain.attendance.Presence
import de.familyplanner.android.feature.meals.domain.attendance.SaveDeadlineSettingsUseCase
import de.familyplanner.android.feature.meals.domain.attendance.SaveProfileUseCase
import de.familyplanner.android.feature.meals.domain.attendance.WeekType
import de.familyplanner.android.feature.meals.fakes.FakeAttendanceRepository
import de.familyplanner.android.feature.meals.fakes.childSession
import de.familyplanner.android.feature.meals.fakes.childWeek
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.someException
import de.familyplanner.android.feature.meals.fakes.weekOf
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditEvent
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditViewModel
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesEvent
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesViewModel
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsEvent
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsViewModel
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewEvent
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAttendanceRepository()
    private val sessions = InMemorySessionStore(parentSession)

    // Samstag, 17.10.2026, 12:00 Berlin
    private val clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Berlin"))

    private fun weekViewModel() = WeekOverviewViewModel(sessions, LoadWeekOverviewUseCase(repository), clock)

    private fun profilesViewModel() = ProfilesViewModel(LoadProfilesUseCase(repository))

    private fun editViewModel(memberId: String = "c-1") =
        ProfileEditViewModel(
            memberId,
            LoadProfilesUseCase(repository),
            SaveProfileUseCase(repository),
            ExceptionUseCases(LoadExceptionsUseCase(repository), AddExceptionUseCase(repository), DeleteExceptionUseCase(repository)),
            clock,
        )

    private fun settingsViewModel() =
        DeadlineSettingsViewModel(LoadDeadlineSettingsUseCase(repository), SaveDeadlineSettingsUseCase(repository))

    // --- Wochenübersicht ---

    @Test
    fun `REQ-MEAL-012 starts at the plan week after today`() {
        val viewModel = weekViewModel()

        assertEquals(planWeek, viewModel.state.value.weekStart)
        assertEquals(listOf(planWeek), repository.requestedWeeks)
        assertEquals(
            "Ben",
            viewModel.state.value.overview
                ?.children
                ?.single()
                ?.name,
        )
    }

    @Test
    fun `REQ-MEAL-012 next and previous week reload`() {
        val viewModel = weekViewModel()

        viewModel.onEvent(WeekOverviewEvent.NextWeek)
        assertEquals(planWeek.plusWeeks(1), viewModel.state.value.weekStart)
        assertEquals(
            planWeek.plusWeeks(1),
            viewModel.state.value.overview
                ?.weekStart,
        )

        viewModel.onEvent(WeekOverviewEvent.PreviousWeek)
        viewModel.onEvent(WeekOverviewEvent.PreviousWeek)
        assertEquals(planWeek.minusWeeks(1), viewModel.state.value.weekStart)
        assertEquals(listOf(planWeek, planWeek.plusWeeks(1), planWeek, planWeek.minusWeeks(1)), repository.requestedWeeks)
    }

    @Test
    fun `REQ-MEAL-012 offline shows network error and keeps week`() {
        repository.weekResult = { ApiResult.Failure(AppError.Network) }

        val viewModel = weekViewModel()

        assertEquals(AppError.Network, viewModel.state.value.error)
        assertNull(viewModel.state.value.overview)
        assertEquals(planWeek, viewModel.state.value.weekStart)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `refresh after an error loads again`() {
        repository.weekResult = { ApiResult.Failure(AppError.Network) }
        val viewModel = weekViewModel()
        repository.weekResult = { ApiResult.Success(weekOf(childWeek(), start = it)) }

        viewModel.onEvent(WeekOverviewEvent.Refresh)

        assertNull(viewModel.state.value.error)
        assertNotNull(viewModel.state.value.overview)
    }

    @Test
    fun `late response of previous week is ignored`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.weekGates[planWeek] = gate
            val viewModel = weekViewModel()

            viewModel.onEvent(WeekOverviewEvent.NextWeek)
            gate.complete(Unit)

            assertEquals(planWeek.plusWeeks(1), viewModel.state.value.weekStart)
            assertEquals(
                planWeek.plusWeeks(1),
                viewModel.state.value.overview
                    ?.weekStart,
            )
        }

    @Test
    fun `week navigation stops at the supported years`() {
        val viewModel =
            WeekOverviewViewModel(
                sessions,
                LoadWeekOverviewUseCase(repository),
                Clock.fixed(Instant.parse("2100-12-20T10:00:00Z"), ZoneId.of("Europe/Berlin")),
            )

        while (viewModel.state.value.canGoNext) viewModel.onEvent(WeekOverviewEvent.NextWeek)
        val last = viewModel.state.value.weekStart
        viewModel.onEvent(WeekOverviewEvent.NextWeek)

        assertEquals(last, viewModel.state.value.weekStart)
        assertEquals(2100, last.year)
    }

    @Test
    fun `REQ-MEAL-010 only parents are marked as parents`() {
        assertTrue(weekViewModel().state.value.isParent)

        sessions.setCurrent(childSession)
        assertFalse(weekViewModel().state.value.isParent)
    }

    // --- Profile-Liste ---

    @Test
    fun `REQ-MEAL-010 profiles load with names`() {
        val viewModel = profilesViewModel()

        assertEquals(
            listOf("Ben", "Tim"),
            viewModel.state.value.profiles
                .map { it.name },
        )
        viewModel.onEvent(ProfilesEvent.Refresh)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `profiles show the error when loading fails`() {
        repository.profilesResult = ApiResult.Failure(AppError.Server)

        assertEquals(AppError.Server, profilesViewModel().state.value.error)
    }

    // --- Profil bearbeiten ---

    @Test
    fun `REQ-MEAL-010 loading alternating profile derives this week type`() {
        val state = editViewModel().state.value

        // Referenzwoche 12.10. = Montag der heutigen Woche → Woche A
        assertTrue(state.alternating)
        assertEquals(WeekType.A, state.thisWeekType)
        assertEquals(LocalDate.of(2026, 10, 12), state.weekOfToday)
        assertEquals("Ben", state.name)
        assertTrue(state.cooks)
    }

    @Test
    fun `REQ-MEAL-010 saving this week as B sends previous monday as reference`() {
        val viewModel = editViewModel()

        viewModel.onEvent(ProfileEditEvent.ThisWeekChanged(WeekType.B))
        viewModel.onEvent(ProfileEditEvent.Save)

        val saved = repository.savedProfiles.single().pattern as AttendancePattern.Alternating
        assertEquals(LocalDate.of(2026, 10, 5), saved.referenceWeek)
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun `REQ-MEAL-010 unchanged profile keeps parity when saved`() {
        val viewModel = editViewModel()

        viewModel.onEvent(ProfileEditEvent.Save)

        val saved = repository.savedProfiles.single().pattern as AttendancePattern.Alternating
        assertEquals(LocalDate.of(2026, 10, 12), saved.referenceWeek)
    }

    @Test
    fun `REQ-MEAL-010 switching to always sends always and keeps the days for switching back`() {
        val viewModel = editViewModel()

        viewModel.onEvent(ProfileEditEvent.AlternatingChanged(false))
        viewModel.onEvent(ProfileEditEvent.Save)

        assertEquals(AttendancePattern.Always, repository.savedProfiles.single().pattern)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY), viewModel.state.value.weekA)
    }

    @Test
    fun `REQ-MEAL-010 empty alternating days shows validation message`() {
        val viewModel = editViewModel()
        DayOfWeek.entries.forEach {
            if (it in viewModel.state.value.weekA) viewModel.onEvent(ProfileEditEvent.DayToggled(WeekType.A, it))
            if (it in viewModel.state.value.weekB) viewModel.onEvent(ProfileEditEvent.DayToggled(WeekType.B, it))
        }

        viewModel.onEvent(ProfileEditEvent.Save)

        assertTrue(viewModel.state.value.saveError is AppError.Validation)
        assertTrue(repository.savedProfiles.isEmpty())
    }

    @Test
    fun `REQ-MEAL-010 forbidden save shows parents only`() {
        repository.saveProfileResult = ApiResult.Failure(AppError.Forbidden)
        val viewModel = editViewModel()

        viewModel.onEvent(ProfileEditEvent.CooksChanged(false))
        viewModel.onEvent(ProfileEditEvent.Save)

        assertEquals(AppError.Forbidden, viewModel.state.value.saveError)
        assertFalse(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `REQ-MEAL-010 always profile gets the proposed alternating days`() {
        val viewModel = editViewModel("c-2")

        assertFalse(viewModel.state.value.alternating)
        viewModel.onEvent(ProfileEditEvent.AlternatingChanged(true))
        viewModel.onEvent(ProfileEditEvent.Save)

        val saved = repository.savedProfiles.single().pattern as AttendancePattern.Alternating
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY), saved.weekA)
        assertEquals(6, saved.weekB.size)
    }

    @Test
    fun `unknown member shows not found`() {
        assertEquals(AppError.NotFound, editViewModel("c-99").state.value.error)
    }

    @Test
    fun `editing clears the saved hint`() {
        val viewModel = editViewModel()
        viewModel.onEvent(ProfileEditEvent.Save)

        viewModel.onEvent(ProfileEditEvent.CooksChanged(false))

        assertFalse(viewModel.state.value.saved)
    }

    // --- Ausnahmen ---

    @Test
    fun `REQ-MEAL-011 adding exception reloads list`() {
        repository.exceptionsResult = ApiResult.Success(emptyList())
        val viewModel = editViewModel()
        viewModel.onEvent(ProfileEditEvent.ShowAddException)
        repository.exceptionsResult = ApiResult.Success(listOf(someException))

        viewModel.onEvent(ProfileEditEvent.AddException(someException.from, someException.to, Presence.PRESENT, "Ferien"))

        assertFalse(viewModel.state.value.showAddException)
        assertEquals(listOf(someException), viewModel.state.value.exceptions)
        assertEquals("Ferien", repository.addedExceptions.single()[4])
    }

    @Test
    fun `REQ-MEAL-011 overlapping exception keeps dialog open with server message`() {
        repository.addExceptionResult = ApiResult.Failure(AppError.Conflict("Zeitraum überschneidet sich mit einer bestehenden Ausnahme"))
        val viewModel = editViewModel()
        viewModel.onEvent(ProfileEditEvent.ShowAddException)

        viewModel.onEvent(ProfileEditEvent.AddException(someException.from, someException.to, Presence.ABSENT, ""))

        assertTrue(viewModel.state.value.showAddException)
        assertEquals(
            AppError.Conflict("Zeitraum überschneidet sich mit einer bestehenden Ausnahme"),
            viewModel.state.value.addExceptionError,
        )
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `REQ-MEAL-011 deleting requires confirmation`() {
        repository.exceptionsResult = ApiResult.Success(listOf(someException))
        val viewModel = editViewModel()

        viewModel.onEvent(ProfileEditEvent.RequestDelete(someException))
        assertEquals(someException, viewModel.state.value.pendingDelete)
        assertTrue(repository.deletedExceptions.isEmpty())

        viewModel.onEvent(ProfileEditEvent.CancelDelete)
        assertNull(viewModel.state.value.pendingDelete)
        assertTrue(repository.deletedExceptions.isEmpty())
    }

    @Test
    fun `REQ-MEAL-011 deleted exception disappears`() {
        repository.exceptionsResult = ApiResult.Success(listOf(someException))
        val viewModel = editViewModel()
        viewModel.onEvent(ProfileEditEvent.RequestDelete(someException))
        repository.exceptionsResult = ApiResult.Success(emptyList())

        viewModel.onEvent(ProfileEditEvent.ConfirmDelete)

        assertEquals(listOf("e-1"), repository.deletedExceptions)
        assertTrue(
            viewModel.state.value.exceptions
                .isEmpty(),
        )
        assertNull(viewModel.state.value.pendingDelete)
    }

    @Test
    fun `deleting an already deleted exception shows not found and reloads`() {
        repository.exceptionsResult = ApiResult.Success(listOf(someException))
        repository.deleteExceptionResult = ApiResult.Failure(AppError.NotFound)
        val viewModel = editViewModel()
        viewModel.onEvent(ProfileEditEvent.RequestDelete(someException))
        repository.exceptionsResult = ApiResult.Success(emptyList())

        viewModel.onEvent(ProfileEditEvent.ConfirmDelete)

        assertEquals(AppError.NotFound, viewModel.state.value.error)
        assertTrue(
            viewModel.state.value.exceptions
                .isEmpty(),
        )
    }

    @Test
    fun `dismissing the add dialog clears its error`() {
        repository.addExceptionResult = ApiResult.Failure(AppError.Network)
        val viewModel = editViewModel()
        viewModel.onEvent(ProfileEditEvent.ShowAddException)
        viewModel.onEvent(ProfileEditEvent.AddException(someException.from, someException.to, Presence.ABSENT, ""))

        viewModel.onEvent(ProfileEditEvent.DismissAddException)

        assertNull(viewModel.state.value.addExceptionError)
        assertFalse(viewModel.state.value.showAddException)
    }

    // --- Fristen ---

    @Test
    fun `REQ-MEAL-013 shows current and scheduled settings`() {
        val viewModel = settingsViewModel()

        assertEquals(
            DayOfWeek.SUNDAY,
            viewModel.state.value.overview
                ?.current
                ?.orderDay,
        )
        assertNull(
            viewModel.state.value.overview
                ?.scheduled,
        )
    }

    @Test
    fun `REQ-MEAL-013 form starts with scheduled values when present`() {
        val scheduled =
            repository.run {
                val current = (settingsResult as ApiResult.Success).data.current
                current.copy(orderDay = DayOfWeek.SATURDAY, orderTime = LocalTime.of(18, 0), effectiveFrom = LocalDate.of(2026, 10, 26))
            }
        repository.settingsResult =
            ApiResult.Success(
                de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettingsOverview(
                    (repository.settingsResult as ApiResult.Success).data.current,
                    scheduled,
                ),
            )

        val state = settingsViewModel().state.value

        assertEquals(DayOfWeek.SATURDAY, state.orderDay)
        assertEquals(LocalTime.of(18, 0), state.orderTime)
    }

    @Test
    fun `REQ-MEAL-013 saving sends day and HH mm times and shows scheduled week`() {
        val viewModel = settingsViewModel()

        viewModel.onEvent(DeadlineSettingsEvent.OrderDayChanged(DayOfWeek.SATURDAY))
        viewModel.onEvent(DeadlineSettingsEvent.OrderTimeChanged(LocalTime.of(18, 0)))
        viewModel.onEvent(DeadlineSettingsEvent.ChildTimeChanged(LocalTime.of(7, 5)))
        viewModel.onEvent(DeadlineSettingsEvent.Save)

        assertEquals(listOf<Any>(DayOfWeek.SATURDAY, LocalTime.of(18, 0), LocalTime.of(7, 5)), repository.savedSettings.single())
        assertEquals(
            LocalDate.of(2026, 10, 26),
            viewModel.state.value.overview
                ?.scheduled
                ?.effectiveFrom,
        )
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `REQ-MEAL-013 forbidden save shows parents only`() {
        repository.saveSettingsResult = ApiResult.Failure(AppError.Forbidden)
        val viewModel = settingsViewModel()

        viewModel.onEvent(DeadlineSettingsEvent.Save)

        assertEquals(AppError.Forbidden, viewModel.state.value.saveError)
    }

    @Test
    fun `settings load error is shown and refresh recovers`() {
        repository.settingsResult = ApiResult.Failure(AppError.Network)
        val viewModel = settingsViewModel()
        assertEquals(AppError.Network, viewModel.state.value.error)

        repository.settingsResult =
            ApiResult.Success(
                de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettingsOverview(
                    de.familyplanner.android.feature.meals.fakes.someSettings,
                    null,
                ),
            )
        viewModel.onEvent(DeadlineSettingsEvent.Refresh)

        assertNull(viewModel.state.value.error)
        assertNotNull(viewModel.state.value.overview)
    }

    @Test
    fun `save is ignored before settings are loaded`() {
        repository.settingsResult = ApiResult.Failure(AppError.Network)
        val viewModel = settingsViewModel()

        viewModel.onEvent(DeadlineSettingsEvent.Save)

        assertTrue(repository.savedSettings.isEmpty())
    }
}
