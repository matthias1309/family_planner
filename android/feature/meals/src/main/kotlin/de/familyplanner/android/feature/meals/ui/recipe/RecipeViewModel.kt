package de.familyplanner.android.feature.meals.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.meals.domain.attendance.mondayOf
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.MealPlanRepository
import de.familyplanner.android.feature.meals.domain.recipe.Recipe
import de.familyplanner.android.feature.meals.domain.recipe.RecipeUseCases
import de.familyplanner.android.feature.meals.domain.recipe.displayText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeUiState(
    val recipe: Recipe? = null,
    /** Zutaten des Gerichts (Anzeigetexte) und seine Portionen, wenn aus dem Plan geöffnet. */
    val dishIngredients: List<String>? = null,
    val dishServings: Int? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val isParent: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val isBusy: Boolean = false,
    val actionError: AppError? = null,
    /** Gelöscht → zurück zur Liste. */
    val isDeleted: Boolean = false,
) {
    val ingredientLines: List<String> get() = dishIngredients ?: recipe?.ingredients?.map { it.displayText() }.orEmpty()
    val servings: Int? get() = dishServings ?: recipe?.servings
    val canManage: Boolean get() = isParent && dishIngredients == null
}

sealed interface RecipeEvent {
    data object Refresh : RecipeEvent

    data object RequestDelete : RecipeEvent

    data object CancelDelete : RecipeEvent

    data object ConfirmDelete : RecipeEvent
}

private data class LoadedRecipe(
    val recipe: ApiResult<Recipe>,
    val dish: Dish?,
)

class RecipeViewModel(
    private val args: RecipeViewArgs,
    sessions: SessionStore,
    private val recipes: RecipeUseCases,
    private val plan: MealPlanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RecipeUiState(isParent = sessions.current?.role == Role.PARENT))
    val state: StateFlow<RecipeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: RecipeEvent) {
        when (event) {
            RecipeEvent.Refresh -> load()
            RecipeEvent.RequestDelete -> _state.update { if (it.canManage) it.copy(showDeleteConfirm = true, actionError = null) else it }
            RecipeEvent.CancelDelete -> _state.update { it.copy(showDeleteConfirm = false) }
            RecipeEvent.ConfirmDelete -> delete()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val loaded = loadRecipeAndDish()
            when (val result = loaded.recipe) {
                is ApiResult.Success ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            recipe = result.data,
                            dishIngredients = loaded.dish?.ingredients,
                            dishServings = loaded.dish?.servings,
                        )
                    }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    /** Das Gericht ist nur Beiwerk: Ist die Woche nicht ladbar oder das Gericht weg, zeigt die Ansicht die Rezeptmengen. */
    private suspend fun loadRecipeAndDish(): LoadedRecipe =
        coroutineScope {
            val recipe = async { recipes.load(args.recipeId) }
            val dish = async { loadDish() }
            LoadedRecipe(recipe.await(), dish.await())
        }

    private suspend fun loadDish(): Dish? {
        val date = args.dishDate ?: return null
        val dishId = args.dishId ?: return null
        val week = plan.week(mondayOf(date)) as? ApiResult.Success ?: return null
        return week.data.days
            .mapNotNull { it.dish }
            .firstOrNull { it.id == dishId && it.recipeId == args.recipeId && it.servings != null }
    }

    private fun delete() {
        val current = _state.value
        if (current.isBusy || !current.canManage) return
        _state.update { it.copy(isBusy = true, showDeleteConfirm = false, actionError = null) }
        viewModelScope.launch {
            when (val result = recipes.delete(args.recipeId)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDeleted = true) }
                // Schon gelöscht (anderes Gerät): Ziel erreicht.
                is ApiResult.Failure ->
                    if (result.error == AppError.NotFound) {
                        _state.update { it.copy(isBusy = false, isDeleted = true) }
                    } else {
                        _state.update { it.copy(isBusy = false, actionError = result.error) }
                    }
            }
        }
    }
}
