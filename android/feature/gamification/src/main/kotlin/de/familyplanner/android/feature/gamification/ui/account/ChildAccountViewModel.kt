package de.familyplanner.android.feature.gamification.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.gamification.domain.BookPointsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadChildAccountUseCase
import de.familyplanner.android.feature.gamification.domain.PointsAccount
import de.familyplanner.android.feature.gamification.domain.PointsLimits
import de.familyplanner.android.feature.gamification.domain.validateManualEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Konto des Kindes [memberId] mit Anzeigename [name]. Ein Koin-Parameter. */
data class ChildAccountArgs(
    val memberId: String,
    val name: String,
)

data class BookingDraft(
    val credit: Boolean = true,
    val amountText: String = "",
    val reason: String = "",
) {
    /** Betrag mit Vorzeichen; `null`, solange keine Zahl eingegeben ist. */
    val amount: Int? get() = amountText.toIntOrNull()?.let { if (credit) it else -it }
    val amountTooHigh: Boolean get() = (amountText.toIntOrNull() ?: 0) > PointsLimits.AMOUNT_MAX
    val canSubmit: Boolean get() = amount?.let { validateManualEntry(it, reason) == null } ?: false
}

data class ChildAccountUiState(
    val args: ChildAccountArgs,
    val account: PointsAccount? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val booking: BookingDraft? = null,
    val isBusy: Boolean = false,
    val bookingError: AppError? = null,
)

sealed interface ChildAccountEvent {
    data object Refresh : ChildAccountEvent

    data object StartBooking : ChildAccountEvent

    data class BookingCreditChanged(
        val credit: Boolean,
    ) : ChildAccountEvent

    data class BookingAmountChanged(
        val text: String,
    ) : ChildAccountEvent

    data class BookingReasonChanged(
        val text: String,
    ) : ChildAccountEvent

    data object SubmitBooking : ChildAccountEvent

    data object DismissBooking : ChildAccountEvent
}

class ChildAccountViewModel(
    private val args: ChildAccountArgs,
    private val loadAccount: LoadChildAccountUseCase,
    private val bookPoints: BookPointsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ChildAccountUiState(args))
    val state: StateFlow<ChildAccountUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun onEvent(event: ChildAccountEvent) {
        when (event) {
            ChildAccountEvent.Refresh -> refresh()
            ChildAccountEvent.StartBooking -> _state.update { it.copy(booking = BookingDraft(), bookingError = null) }
            is ChildAccountEvent.BookingCreditChanged -> editDraft { it.copy(credit = event.credit) }
            is ChildAccountEvent.BookingAmountChanged ->
                editDraft { it.copy(amountText = event.text.filter(Char::isDigit).take(PointsLimits.AMOUNT_DIGITS_MAX)) }
            is ChildAccountEvent.BookingReasonChanged -> editDraft { it.copy(reason = event.text.take(PointsLimits.REASON_MAX)) }
            ChildAccountEvent.SubmitBooking -> submit()
            ChildAccountEvent.DismissBooking -> _state.update { it.copy(booking = null, bookingError = null) }
        }
    }

    private fun editDraft(change: (BookingDraft) -> BookingDraft) =
        _state.update { s ->
            s.booking?.let { s.copy(booking = change(it)) }
                ?: s
        }

    private fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val result = loadAccount(args.memberId)
                _state.update {
                    when (result) {
                        is ApiResult.Success -> it.copy(isLoading = false, account = result.data)
                        is ApiResult.Failure -> it.copy(isLoading = false, error = result.error)
                    }
                }
            }
    }

    private fun submit() {
        val current = _state.value
        val draft = current.booking ?: return
        val amount = draft.amount ?: return
        if (current.isBusy || !draft.canSubmit) return
        _state.update { it.copy(isBusy = true, bookingError = null) }
        viewModelScope.launch {
            when (val result = bookPoints(args.memberId, amount, draft.reason)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isBusy = false, booking = null) }
                    refresh()
                }
                is ApiResult.Failure ->
                    _state.update {
                        if (result.error is AppError.Validation) {
                            it.copy(isBusy = false, bookingError = result.error)
                        } else {
                            it.copy(isBusy = false, booking = null, error = result.error)
                        }
                    }
            }
        }
    }
}
