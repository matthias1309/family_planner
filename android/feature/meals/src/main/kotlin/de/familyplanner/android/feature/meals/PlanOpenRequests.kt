package de.familyplanner.android.feature.meals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wunsch „Essensplan öffnen“ (Tippen auf eine Benachrichtigung) bis der angemeldete Hauptbereich ihn abholt. */
class PlanOpenRequests {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }

    companion object {
        const val EXTRA_OPEN_PLAN = "de.familyplanner.android.OPEN_PLAN"
    }
}
