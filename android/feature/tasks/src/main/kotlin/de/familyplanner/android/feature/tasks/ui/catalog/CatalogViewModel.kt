package de.familyplanner.android.feature.tasks.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.android.feature.tasks.domain.catalog.LoadCatalogUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogUiState(
    val tasks: List<CatalogTask> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && tasks.isEmpty()
}

class CatalogViewModel(
    private val loadCatalog: LoadCatalogUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            _state.update {
                when (val result = loadCatalog()) {
                    is ApiResult.Success -> it.copy(isLoading = false, tasks = result.data)
                    is ApiResult.Failure -> it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }
}
