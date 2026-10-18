package de.familyplanner.android.feature.meals.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.meals.domain.recipe.RecipeLimits
import de.familyplanner.android.feature.meals.domain.recipe.RecipeSummary
import de.familyplanner.android.feature.meals.domain.recipe.RecipeUseCases
import de.familyplanner.android.feature.meals.domain.recipe.filterRecipes
import de.familyplanner.android.feature.meals.ui.recipe.RecipeListArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeListUiState(
    val recipes: List<RecipeSummary> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val isParent: Boolean = false,
    /** Auswahl für den Gericht-Editor: Tippen übernimmt, kein Anlegen. */
    val pick: Boolean = false,
) {
    val visible: List<RecipeSummary> get() = filterRecipes(recipes, query)
    val canCreate: Boolean get() = isParent && !pick
}

sealed interface RecipeListEvent {
    data object Refresh : RecipeListEvent

    data class QueryChanged(
        val value: String,
    ) : RecipeListEvent
}

class RecipeListViewModel(
    args: RecipeListArgs,
    sessions: SessionStore,
    private val recipes: RecipeUseCases,
) : ViewModel() {
    private val _state =
        MutableStateFlow(RecipeListUiState(isParent = sessions.current?.role == Role.PARENT, pick = args.pick))
    val state: StateFlow<RecipeListUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: RecipeListEvent) {
        when (event) {
            RecipeListEvent.Refresh -> load()
            is RecipeListEvent.QueryChanged -> _state.update { it.copy(query = event.value.take(RecipeLimits.QUERY_MAX)) }
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                when (val result = recipes.list()) {
                    is ApiResult.Success -> _state.update { it.copy(isLoading = false, recipes = result.data) }
                    is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
                }
            }
    }
}
