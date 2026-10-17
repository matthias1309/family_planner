package de.familyplanner.android.feature.meals.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceException
import de.familyplanner.android.feature.meals.domain.attendance.AttendancePattern
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceProfile
import de.familyplanner.android.feature.meals.domain.attendance.ChildProfile
import de.familyplanner.android.feature.meals.domain.attendance.ExceptionUseCases
import de.familyplanner.android.feature.meals.domain.attendance.LoadProfilesUseCase
import de.familyplanner.android.feature.meals.domain.attendance.Presence
import de.familyplanner.android.feature.meals.domain.attendance.SaveProfileUseCase
import de.familyplanner.android.feature.meals.domain.attendance.WeekType
import de.familyplanner.android.feature.meals.domain.attendance.mondayOf
import de.familyplanner.android.feature.meals.domain.attendance.referenceWeekFor
import de.familyplanner.android.feature.meals.domain.attendance.weekTypeOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate

/** Vorschlag für „Wechselmodell“, wenn das Kind bisher „immer da“ war (PRD). */
private val DefaultWeekA = setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
private val DefaultWeekB = DayOfWeek.entries.filter { it >= DayOfWeek.TUESDAY }.toSet()

data class ProfileEditUiState(
    val name: String = "",
    val weekOfToday: LocalDate,
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val error: AppError? = null,
    val cooks: Boolean = false,
    val alternating: Boolean = false,
    val weekA: Set<DayOfWeek> = DefaultWeekA,
    val weekB: Set<DayOfWeek> = DefaultWeekB,
    val thisWeekType: WeekType = WeekType.A,
    val isBusy: Boolean = false,
    val saved: Boolean = false,
    val saveError: AppError? = null,
    val exceptions: List<AttendanceException> = emptyList(),
    val showAddException: Boolean = false,
    val addExceptionError: AppError? = null,
    val pendingDelete: AttendanceException? = null,
)

sealed interface ProfileEditEvent {
    data object Refresh : ProfileEditEvent

    data class CooksChanged(
        val cooks: Boolean,
    ) : ProfileEditEvent

    data class AlternatingChanged(
        val alternating: Boolean,
    ) : ProfileEditEvent

    data class DayToggled(
        val week: WeekType,
        val day: DayOfWeek,
    ) : ProfileEditEvent

    data class ThisWeekChanged(
        val type: WeekType,
    ) : ProfileEditEvent

    data object Save : ProfileEditEvent

    data object ShowAddException : ProfileEditEvent

    data object DismissAddException : ProfileEditEvent

    data class AddException(
        val from: LocalDate,
        val to: LocalDate,
        val presence: Presence,
        val note: String,
    ) : ProfileEditEvent

    data class RequestDelete(
        val exception: AttendanceException,
    ) : ProfileEditEvent

    data object CancelDelete : ProfileEditEvent

    data object ConfirmDelete : ProfileEditEvent
}

class ProfileEditViewModel(
    private val memberId: String,
    private val loadProfiles: LoadProfilesUseCase,
    private val saveProfile: SaveProfileUseCase,
    private val exceptions: ExceptionUseCases,
    private val clock: Clock = Clock.system(BerlinZone),
) : ViewModel() {
    private val today: LocalDate get() = LocalDate.now(clock)
    private val _state = MutableStateFlow(ProfileEditUiState(weekOfToday = mondayOf(LocalDate.now(clock))))
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: ProfileEditEvent) {
        when (event) {
            ProfileEditEvent.Refresh -> refresh()
            is ProfileEditEvent.CooksChanged -> edit { it.copy(cooks = event.cooks) }
            is ProfileEditEvent.AlternatingChanged -> edit { it.copy(alternating = event.alternating) }
            is ProfileEditEvent.DayToggled -> edit { it.toggleDay(event.week, event.day) }
            is ProfileEditEvent.ThisWeekChanged -> edit { it.copy(thisWeekType = event.type) }
            ProfileEditEvent.Save -> save()
            ProfileEditEvent.ShowAddException -> _state.update { it.copy(showAddException = true, addExceptionError = null) }
            ProfileEditEvent.DismissAddException -> _state.update { it.copy(showAddException = false, addExceptionError = null) }
            is ProfileEditEvent.AddException -> addException(event)
            is ProfileEditEvent.RequestDelete -> _state.update { it.copy(pendingDelete = event.exception) }
            ProfileEditEvent.CancelDelete -> _state.update { it.copy(pendingDelete = null) }
            ProfileEditEvent.ConfirmDelete -> confirmDelete()
        }
    }

    /** Ist das Formular schon geladen, würde ein Neuladen ungespeicherte Eingaben überschreiben — dann nur die Ausnahmen. */
    private fun refresh() {
        if (!_state.value.isLoaded) {
            load()
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            reloadExceptions()
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun edit(change: (ProfileEditUiState) -> ProfileEditUiState) =
        _state.update { change(it).copy(saved = false, saveError = null) }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            coroutineScope {
                val profiles = async { loadProfiles() }
                val list = async { exceptions.load(memberId) }
                applyProfile(profiles.await())
                applyExceptions(list.await())
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun applyProfile(result: ApiResult<List<ChildProfile>>) {
        when (result) {
            is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
            is ApiResult.Success -> {
                val child = result.data.firstOrNull { it.memberId == memberId }
                if (child == null) {
                    _state.update { it.copy(error = AppError.NotFound) }
                } else {
                    _state.update { it.withProfile(child.name, child.profile, today) }
                }
            }
        }
    }

    private fun applyExceptions(result: ApiResult<List<AttendanceException>>) {
        when (result) {
            is ApiResult.Success -> _state.update { it.copy(exceptions = result.data) }
            is ApiResult.Failure -> _state.update { it.copy(error = it.error ?: result.error) }
        }
    }

    private fun save() {
        val current = _state.value
        if (current.isBusy || !current.isLoaded) return
        _state.update { it.copy(isBusy = true, saved = false, saveError = null) }
        viewModelScope.launch {
            when (val result = saveProfile(AttendanceProfile(memberId, current.cooks, current.toPattern(today)))) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, saved = true) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, saveError = result.error) }
            }
        }
    }

    private fun addException(event: ProfileEditEvent.AddException) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, addExceptionError = null) }
        viewModelScope.launch {
            when (val result = exceptions.add(memberId, event.from, event.to, event.presence, event.note)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isBusy = false, showAddException = false) }
                    reloadExceptions()
                }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, addExceptionError = result.error) }
            }
        }
    }

    private fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, pendingDelete = null, error = null) }
        viewModelScope.launch {
            val result = exceptions.delete(target.id)
            _state.update { it.copy(isBusy = false, error = (result as? ApiResult.Failure)?.error) }
            // Auch bei „nicht gefunden“ (bereits gelöscht) stimmt die Liste danach wieder.
            reloadExceptions()
        }
    }

    private suspend fun reloadExceptions() {
        when (val result = exceptions.load(memberId)) {
            is ApiResult.Success -> _state.update { it.copy(exceptions = result.data) }
            is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
        }
    }
}

private fun ProfileEditUiState.toggleDay(
    week: WeekType,
    day: DayOfWeek,
): ProfileEditUiState {
    fun Set<DayOfWeek>.toggled() = if (day in this) this - day else this + day
    return if (week == WeekType.A) copy(weekA = weekA.toggled()) else copy(weekB = weekB.toggled())
}

private fun ProfileEditUiState.withProfile(
    name: String,
    profile: AttendanceProfile,
    today: LocalDate,
): ProfileEditUiState {
    val base = copy(name = name, isLoaded = true, cooks = profile.cooks)
    return when (val pattern = profile.pattern) {
        AttendancePattern.Always -> base.copy(alternating = false)
        is AttendancePattern.Alternating ->
            base.copy(
                alternating = true,
                weekA = pattern.weekA,
                weekB = pattern.weekB,
                thisWeekType = weekTypeOf(today, pattern.referenceWeek),
            )
    }
}

private fun ProfileEditUiState.toPattern(today: LocalDate): AttendancePattern =
    if (alternating) {
        AttendancePattern.Alternating(weekA, weekB, referenceWeekFor(today, thisWeekType))
    } else {
        AttendancePattern.Always
    }
