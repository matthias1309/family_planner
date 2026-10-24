package de.familyplanner.android.feature.tasks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wunsch „Aufgaben öffnen“ (Tippen auf eine Benachrichtigung) bis der angemeldete Hauptbereich ihn abholt. */
class TaskOpenRequests {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }

    companion object {
        const val EXTRA_OPEN_TASKS = "de.familyplanner.android.OPEN_TASKS"
    }
}
