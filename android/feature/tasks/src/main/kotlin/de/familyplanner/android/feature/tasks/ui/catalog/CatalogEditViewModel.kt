package de.familyplanner.android.feature.tasks.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogInput
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [catalogTaskId] `null` = neue Aufgabe. Ein Koin-Parameter. */
data class CatalogEditArgs(
    val catalogTaskId: String? = null,
)

data class CatalogEditUiState(
    val catalogTaskId: String?,
    val title: String = "",
    val emoji: String = "",
    val points: Int = TaskLimits.POINTS_DEFAULT,
    val isLoading: Boolean = false,
    val loadError: AppError? = null,
    val isBusy: Boolean = false,
    val error: AppError? = null,
    val confirmingArchive: Boolean = false,
    val isDone: Boolean = false,
) {
    val isNew: Boolean get() = catalogTaskId == null
    val canSave: Boolean get() = !isLoading && loadError == null && !isBusy && title.isNotBlank()
}

sealed interface CatalogEditEvent {
    data class TitleChanged(
        val value: String,
    ) : CatalogEditEvent

    data class EmojiChanged(
        val value: String,
    ) : CatalogEditEvent

    data class PointsChanged(
        val value: Int,
    ) : CatalogEditEvent

    data object Save : CatalogEditEvent

    data object AskArchive : CatalogEditEvent

    data object ConfirmArchive : CatalogEditEvent

    data object DismissArchive : CatalogEditEvent

    data object Retry : CatalogEditEvent
}

class CatalogEditViewModel(
    args: CatalogEditArgs,
    private val catalog: CatalogUseCases,
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogEditUiState(args.catalogTaskId, isLoading = args.catalogTaskId != null))
    val state: StateFlow<CatalogEditUiState> = _state.asStateFlow()

    init {
        if (args.catalogTaskId != null) loadExisting(args.catalogTaskId)
    }

    fun onEvent(event: CatalogEditEvent) {
        when (event) {
            is CatalogEditEvent.TitleChanged -> _state.update { it.copy(title = event.value.take(TaskLimits.TITLE_MAX), error = null) }
            is CatalogEditEvent.EmojiChanged -> _state.update { it.copy(emoji = event.value.take(TaskLimits.EMOJI_MAX), error = null) }
            is CatalogEditEvent.PointsChanged ->
                _state.update { it.copy(points = event.value.coerceIn(TaskLimits.POINTS_MIN, TaskLimits.POINTS_MAX), error = null) }
            CatalogEditEvent.Save -> save()
            CatalogEditEvent.AskArchive -> _state.update { it.copy(confirmingArchive = true) }
            CatalogEditEvent.DismissArchive -> _state.update { it.copy(confirmingArchive = false) }
            CatalogEditEvent.ConfirmArchive -> archive()
            CatalogEditEvent.Retry -> _state.value.catalogTaskId?.let(::loadExisting)
        }
    }

    // Der Server liefert keine Einzelabfrage: die Aufgabe kommt aus der Katalogliste.
    private fun loadExisting(id: String) {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            _state.update {
                when (val result = catalog.load()) {
                    is ApiResult.Success -> {
                        val task = result.data.firstOrNull { entry -> entry.id == id }
                        if (task == null) {
                            it.copy(isLoading = false, loadError = AppError.NotFound)
                        } else {
                            it.copy(isLoading = false, title = task.title, emoji = task.emoji.orEmpty(), points = task.points)
                        }
                    }
                    is ApiResult.Failure -> it.copy(isLoading = false, loadError = result.error)
                }
            }
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val input = CatalogInput(current.title, current.emoji, current.points)
            when (val result = catalog.save(current.catalogTaskId, input)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
            }
        }
    }

    private fun archive() {
        val id = _state.value.catalogTaskId ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, confirmingArchive = false, error = null) }
        viewModelScope.launch {
            when (val result = catalog.archive(id)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
            }
        }
    }
}
