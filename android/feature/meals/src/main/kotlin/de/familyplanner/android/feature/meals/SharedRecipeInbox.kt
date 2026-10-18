package de.familyplanner.android.feature.meals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val SHARED_TEXT_MAX = 2_000

/** Geteilter Text (Teilen-Menü) bis der angemeldete Hauptbereich ihn abholt; abgemeldet bleibt er liegen. */
class SharedRecipeInbox {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(text: String?) {
        if (!text.isNullOrBlank()) _pending.value = text.take(SHARED_TEXT_MAX)
    }

    fun consume() {
        _pending.value = null
    }
}
