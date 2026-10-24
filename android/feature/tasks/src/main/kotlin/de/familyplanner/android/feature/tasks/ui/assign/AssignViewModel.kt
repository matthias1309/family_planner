package de.familyplanner.android.feature.tasks.ui.assign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.tasks.domain.board.AssignTasksUseCase
import de.familyplanner.android.feature.tasks.domain.board.BoardLoad
import de.familyplanner.android.feature.tasks.domain.board.LoadBoardUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.android.feature.tasks.domain.catalog.LoadCatalogUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [memberId]: Vorauswahl des Kindes (aktueller Reiter), `null` = noch keines gewählt. Ein Koin-Parameter. */
data class AssignArgs(
    val memberId: String? = null,
)

data class AssignChild(
    val id: String,
    val name: String,
)

data class AssignUiState(
    val preselected: String? = null,
    val children: List<AssignChild> = emptyList(),
    val catalog: List<CatalogTask> = emptyList(),
    val memberId: String? = null,
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val isBusy: Boolean = false,
    val submitError: AppError? = null,
    val isDone: Boolean = false,
) {
    val tooMany: Boolean get() = selected.size > TaskLimits.ASSIGN_MAX
    val canSubmit: Boolean get() = !isLoading && !isBusy && memberId != null && selected.isNotEmpty() && !tooMany
    val catalogEmpty: Boolean get() = !isLoading && error == null && catalog.isEmpty()
}

sealed interface AssignEvent {
    data object Retry : AssignEvent

    data class SelectChild(
        val memberId: String,
    ) : AssignEvent

    data class Toggle(
        val catalogTaskId: String,
    ) : AssignEvent

    data object Submit : AssignEvent
}

class AssignViewModel(
    private val args: AssignArgs,
    private val loadBoard: LoadBoardUseCase,
    private val loadCatalog: LoadCatalogUseCase,
    private val assign: AssignTasksUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AssignUiState(preselected = args.memberId))
    val state: StateFlow<AssignUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: AssignEvent) {
        when (event) {
            AssignEvent.Retry -> load()
            is AssignEvent.SelectChild -> _state.update { it.copy(memberId = event.memberId) }
            is AssignEvent.Toggle ->
                _state.update {
                    it.copy(
                        selected =
                            if (event.catalogTaskId in
                                it.selected
                            ) {
                                it.selected - event.catalogTaskId
                            } else {
                                it.selected + event.catalogTaskId
                            },
                    )
                }
            AssignEvent.Submit -> submit()
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val (board, catalog) =
                coroutineScope {
                    val boardResult = async { loadBoard() }
                    val catalogResult = async { loadCatalog() }
                    boardResult.await() to catalogResult.await()
                }
            val lanes =
                when (board) {
                    is BoardLoad.Fresh -> board.board.lanes
                    is BoardLoad.Cached -> board.board.lanes
                    is BoardLoad.Failed -> return@launch fail(board.error)
                }
            val tasks = (catalog as? ApiResult.Success)?.data ?: return@launch fail((catalog as ApiResult.Failure).error)
            val children = lanes.map { AssignChild(it.memberId, it.name) }
            val preset = args.memberId?.takeIf { id -> children.any { it.id == id } } ?: children.singleOrNull()?.id
            _state.update { current ->
                current.copy(
                    isLoading = false,
                    children = children,
                    catalog = tasks,
                    memberId = current.memberId ?: preset,
                    selected = current.selected.filter { id -> tasks.any { it.id == id } }.toSet(),
                )
            }
        }
    }

    private fun fail(error: AppError) = _state.update { it.copy(isLoading = false, error = error) }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isBusy = true, submitError = null) }
        viewModelScope.launch {
            when (val result = assign(current.memberId, current.selected.toList())) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, submitError = result.error) }
            }
        }
    }
}
