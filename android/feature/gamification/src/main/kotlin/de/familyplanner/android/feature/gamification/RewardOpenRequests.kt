package de.familyplanner.android.feature.gamification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wunsch „Belohnungen öffnen“ (Tippen auf eine Benachrichtigung) bis der angemeldete Hauptbereich ihn abholt. */
class RewardOpenRequests {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }

    companion object {
        const val EXTRA_OPEN_REWARDS = "de.familyplanner.android.OPEN_REWARDS"
    }
}
