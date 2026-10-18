package de.familyplanner.android.feature.meals.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.recipe.ImportRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.Recipe
import de.familyplanner.android.feature.meals.domain.recipe.RecipeImportError
import de.familyplanner.android.feature.meals.domain.recipe.RecipeImportResult
import de.familyplanner.android.feature.meals.domain.recipe.RecipeInput
import de.familyplanner.android.feature.meals.domain.recipe.RecipeLimits
import de.familyplanner.android.feature.meals.domain.recipe.RecipeUseCases
import de.familyplanner.android.feature.meals.domain.recipe.extractRecipeUrl
import de.familyplanner.android.feature.meals.domain.recipe.ingredientsToText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeEditUiState(
    val recipeId: String?,
    val name: String = "",
    val servings: Int = RecipeLimits.SERVINGS_DEFAULT,
    val ingredientsText: String = "",
    val stepsText: String = "",
    val sourceUrl: String = "",
    val isLoading: Boolean = false,
    val loadError: AppError? = null,
    val isBusy: Boolean = false,
    val isImporting: Boolean = false,
    val importError: RecipeImportError? = null,
    val error: AppError? = null,
    val isDone: Boolean = false,
) {
    val isNew: Boolean get() = recipeId == null
    val canSave: Boolean get() =
        !isLoading &&
            loadError == null &&
            !isBusy &&
            !isImporting &&
            name.isNotBlank() &&
            ingredientsText.isNotBlank()
    val canImport: Boolean
        get() = isNew && !isLoading && loadError == null && !isBusy && !isImporting && sourceUrl.isNotBlank()
}

sealed interface RecipeEditEvent {
    data class NameChanged(
        val value: String,
    ) : RecipeEditEvent

    data class ServingsChanged(
        val value: Int,
    ) : RecipeEditEvent

    data class IngredientsChanged(
        val value: String,
    ) : RecipeEditEvent

    data class StepsChanged(
        val value: String,
    ) : RecipeEditEvent

    data class SourceUrlChanged(
        val value: String,
    ) : RecipeEditEvent

    data object Save : RecipeEditEvent

    data object Import : RecipeEditEvent

    data object Retry : RecipeEditEvent
}

class RecipeEditViewModel(
    private val args: RecipeEditArgs,
    sessions: SessionStore,
    private val recipes: RecipeUseCases,
    private val importRecipe: ImportRecipeUseCase,
) : ViewModel() {
    private val isParent = sessions.current?.role == Role.PARENT
    private val _state = MutableStateFlow(RecipeEditUiState(recipeId = args.recipeId))
    val state: StateFlow<RecipeEditUiState> = _state.asStateFlow()

    init {
        load()
        if (isParent && args.recipeId == null && args.importText != null) startImport(args.importText)
    }

    fun onEvent(event: RecipeEditEvent) {
        when (event) {
            is RecipeEditEvent.NameChanged -> edit { it.copy(name = event.value.take(PlanLimits.NAME_MAX)) }
            is RecipeEditEvent.ServingsChanged ->
                edit { it.copy(servings = event.value.coerceIn(RecipeLimits.SERVINGS_MIN, RecipeLimits.SERVINGS_MAX)) }
            is RecipeEditEvent.IngredientsChanged -> edit { it.copy(ingredientsText = event.value) }
            is RecipeEditEvent.StepsChanged -> edit { it.copy(stepsText = event.value) }
            is RecipeEditEvent.SourceUrlChanged -> edit { it.copy(sourceUrl = event.value, importError = null) }
            RecipeEditEvent.Save -> save()
            RecipeEditEvent.Import -> if (_state.value.canImport) startImport(_state.value.sourceUrl)
            RecipeEditEvent.Retry -> load()
        }
    }

    private fun edit(change: (RecipeEditUiState) -> RecipeEditUiState) = _state.update { change(it).copy(error = null) }

    private fun load() {
        // Anlegen, Ändern und Löschen sind Elternsache (Server: 403); Kinder kommen gar nicht erst zum Formular.
        if (!isParent) {
            _state.update { it.copy(isLoading = false, loadError = AppError.Forbidden) }
            return
        }
        val recipeId = args.recipeId ?: return
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = recipes.load(recipeId)) {
                is ApiResult.Success -> _state.update { it.withRecipe(result.data) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    private fun RecipeEditUiState.withRecipe(recipe: Recipe) =
        copy(
            isLoading = false,
            name = recipe.name,
            servings = recipe.servings,
            ingredientsText = ingredientsToText(recipe.ingredients),
            stepsText = recipe.steps.joinToString("\n"),
            sourceUrl = recipe.sourceUrl.orEmpty(),
        )

    private fun startImport(text: String) {
        _state.update {
            it.copy(isImporting = true, importError = null, error = null, sourceUrl = extractRecipeUrl(text) ?: it.sourceUrl)
        }
        viewModelScope.launch {
            when (val result = importRecipe(text)) {
                is RecipeImportResult.Success -> _state.update { it.withImport(result.input) }
                is RecipeImportResult.Failure -> _state.update { it.copy(isImporting = false, importError = result.error) }
            }
        }
    }

    private fun RecipeEditUiState.withImport(input: RecipeInput) =
        copy(
            isImporting = false,
            name = input.name,
            servings = input.servings,
            ingredientsText = input.ingredientsText,
            stepsText = input.stepsText,
            sourceUrl = input.sourceUrl,
        )

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val input = RecipeInput(current.name, current.servings, current.ingredientsText, current.stepsText, current.sourceUrl)
            when (val result = recipes.save(args.recipeId, input)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
            }
        }
    }
}
