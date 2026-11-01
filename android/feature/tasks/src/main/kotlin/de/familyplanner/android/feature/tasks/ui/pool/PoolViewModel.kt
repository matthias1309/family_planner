package de.familyplanner.android.feature.tasks.ui.pool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.android.feature.tasks.domain.pool.LoadPoolUseCase
import de.familyplanner.android.feature.tasks.domain.pool.TakePoolTaskUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Freigegebene Aufgaben, die ein Kind selbst nehmen kann (REQ-TASK-028). */
data class PoolUiState(
    val tasks: List<CatalogTask> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val busyId: String? = null,
    val actionError: AppError? = null,
    val isDone: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && tasks.isEmpty()
}

sealed interface PoolEvent {
    data object Retry : PoolEvent

    data class Take(
        val catalogTaskId: String,
        val alreadyDone: Boolean,
    ) : PoolEvent
}

class PoolViewModel(
    private val loadPool: LoadPoolUseCase,
    private val takePoolTask: TakePoolTaskUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(PoolUiState())
    val state: StateFlow<PoolUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: PoolEvent) {
        when (event) {
            PoolEvent.Retry -> load()
            is PoolEvent.Take -> take(event.catalogTaskId, event.alreadyDone)
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            _state.update {
                when (val result = loadPool()) {
                    is ApiResult.Success -> it.copy(isLoading = false, tasks = result.data)
                    is ApiResult.Failure -> it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    private fun take(
        catalogTaskId: String,
        alreadyDone: Boolean,
    ) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = catalogTaskId, actionError = null) }
        viewModelScope.launch {
            when (val result = takePoolTask(catalogTaskId, alreadyDone)) {
                is ApiResult.Success -> _state.update { it.copy(busyId = null, isDone = true) }
                is ApiResult.Failure -> {
                    _state.update { it.copy(busyId = null, actionError = result.error) }
                    // Freigabe zurückgenommen oder archiviert: Liste neu holen, der Eintrag verschwindet.
                    if (result.error == AppError.Forbidden || result.error is AppError.Conflict || result.error == AppError.NotFound) load()
                }
            }
        }
    }
}
