package de.familyplanner.android.feature.meals.ui.dish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.attendance.mondayOf
import de.familyplanner.android.feature.meals.domain.plan.CookCandidate
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishEditorData
import de.familyplanner.android.feature.meals.domain.plan.DishInput
import de.familyplanner.android.feature.meals.domain.plan.DishUseCases
import de.familyplanner.android.feature.meals.domain.plan.EditorMode
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.plan.PlanMember
import de.familyplanner.android.feature.meals.domain.plan.allowedDates
import de.familyplanner.android.feature.meals.domain.plan.canConfirmCooking
import de.familyplanner.android.feature.meals.domain.plan.editorMode
import de.familyplanner.android.feature.meals.domain.plan.preselectDate
import de.familyplanner.android.feature.meals.domain.recipe.Recipe
import de.familyplanner.android.feature.meals.domain.recipe.RecipeRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

data class DishEditUiState(
    val dishId: String?,
    val weekStart: LocalDate,
    val date: LocalDate,
    val name: String = "",
    val ingredientsText: String = "",
    val cookId: String? = null,
    val parents: List<PlanMember> = emptyList(),
    val children: List<CookCandidate> = emptyList(),
    val existing: Dish? = null,
    val mode: EditorMode = EditorMode.PARENT_EDIT,
    /** Tage dieser Woche mit einem anderen Gericht (Server: 409). */
    val takenDates: Set<LocalDate> = emptySet(),
    /** Wählbare Tage: ohne belegte Tage, bei Kindern nur Anwesenheitstage. */
    val allowedDates: Set<LocalDate> = emptySet(),
    val isLoading: Boolean = true,
    val loadError: AppError? = null,
    val isBusy: Boolean = false,
    val error: AppError? = null,
    val showDeleteConfirm: Boolean = false,
    val showRejectDialog: Boolean = false,
    val rejectReason: String = "",
    /** Für den Fristhinweis im Kind-Modus. */
    val now: Instant = Instant.EPOCH,
    /** Gespeichert, freigegeben oder gelöscht → die Route geht zurück. */
    val isDone: Boolean = false,
    val recipeId: String? = null,
    val servings: Int? = null,
    /** Geladenes Rezept (Auswahl oder Rezeptbezug des Gerichts); nur damit lassen sich Portionen neu rechnen. */
    val recipe: Recipe? = null,
    val isRecipeLoading: Boolean = false,
    val presentByDate: Map<LocalDate, Int> = emptyMap(),
    /** Eltern dürfen das Kochen dieses Kindergerichts bestätigen (REQ-MEAL-034). */
    val canConfirmCooking: Boolean = false,
    val showCookedConfirm: Boolean = false,
) {
    val canUseRecipe: Boolean get() = !isLoading && loadError == null && !fieldsLocked && !isBusy && !isRecipeLoading
    val recipeRef: RecipeRef? get() = recipeId?.let { id -> servings?.let { RecipeRef(id, it) } }
    val isNew: Boolean get() = dishId == null
    val childCook: CookCandidate? get() = children.firstOrNull { it.memberId == cookId }
    val ingredientsRequired: Boolean get() = childCook != null
    val fieldsLocked: Boolean get() = mode == EditorMode.PARENT_MOVE
    val isLate: Boolean
        get() = mode == EditorMode.CHILD && childCook?.submissionDeadline?.let { now.isAfter(it) } == true
    val canSave: Boolean
        get() =
            !isLoading &&
                loadError == null &&
                cookId != null &&
                name.isNotBlank() &&
                date in allowedDates &&
                !isBusy &&
                (mode != EditorMode.PARENT_MOVE || date != existing?.date)
    val canDelete: Boolean get() = !isNew && mode != EditorMode.CHILD
}

sealed interface DishEditEvent {
    data class NameChanged(
        val value: String,
    ) : DishEditEvent

    data class IngredientsChanged(
        val value: String,
    ) : DishEditEvent

    data class DateSelected(
        val date: LocalDate,
    ) : DishEditEvent

    data class CookSelected(
        val memberId: String,
    ) : DishEditEvent

    data object Save : DishEditEvent

    data object RequestDelete : DishEditEvent

    data object CancelDelete : DishEditEvent

    data object ConfirmDelete : DishEditEvent

    data object Approve : DishEditEvent

    data object RequestReject : DishEditEvent

    data object RequestConfirmCooked : DishEditEvent

    data object CancelConfirmCooked : DishEditEvent

    data object ConfirmCooked : DishEditEvent

    data class RejectReasonChanged(
        val value: String,
    ) : DishEditEvent

    data object CancelReject : DishEditEvent

    data object ConfirmReject : DishEditEvent

    data object Retry : DishEditEvent

    data class RecipePicked(
        val recipeId: String,
    ) : DishEditEvent

    data class ServingsChanged(
        val value: Int,
    ) : DishEditEvent

    data object ClearRecipe : DishEditEvent
}

@Suppress("TooManyFunctions")
class DishEditViewModel(
    private val args: DishEditArgs,
    private val sessions: SessionStore,
    private val dishes: DishUseCases,
    private val clock: Clock = Clock.system(BerlinZone),
) : ViewModel() {
    private val dishId = args.dishId

    /** Auswahl, die ankam, bevor der Editor fertig geladen war; wird nach dem Laden übernommen. */
    private var pendingRecipeId: String? = null
    private val _state =
        MutableStateFlow(DishEditUiState(dishId = dishId, weekStart = mondayOf(args.date), date = args.date))
    val state: StateFlow<DishEditUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: DishEditEvent) {
        when (event) {
            is DishEditEvent.NameChanged ->
                edit { if (it.fieldsLocked) it else it.copy(name = event.value.take(PlanLimits.NAME_MAX)) }
            is DishEditEvent.IngredientsChanged -> edit { if (it.fieldsLocked) it else it.copy(ingredientsText = event.value) }
            is DishEditEvent.DateSelected -> edit { if (event.date in it.allowedDates) it.copy(date = event.date) else it }
            is DishEditEvent.CookSelected -> edit { it.withCook(event.memberId) }
            DishEditEvent.Save -> save()
            DishEditEvent.RequestDelete -> _state.update { it.copy(showDeleteConfirm = true) }
            DishEditEvent.CancelDelete -> _state.update { it.copy(showDeleteConfirm = false) }
            DishEditEvent.ConfirmDelete -> delete()
            DishEditEvent.Retry -> load()
            else -> onRecipe(event)
        }
    }

    private fun onRecipe(event: DishEditEvent) {
        when (event) {
            is DishEditEvent.RecipePicked -> pickRecipe(event.recipeId)
            is DishEditEvent.ServingsChanged -> edit { if (it.canUseRecipe) it.withServings(event.value) else it }
            DishEditEvent.ClearRecipe -> edit { if (it.canUseRecipe) it.withoutRecipe() else it }
            else -> onReview(event)
        }
    }

    private fun pickRecipe(recipeId: String) {
        if (_state.value.isLoading) {
            pendingRecipeId = recipeId
            return
        }
        if (!_state.value.canUseRecipe) return
        _state.update { it.copy(isRecipeLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = dishes.recipe(recipeId)) {
                is ApiResult.Success -> _state.update { it.withPickedRecipe(result.data) }
                is ApiResult.Failure -> _state.update { it.copy(isRecipeLoading = false, error = result.error) }
            }
        }
    }

    /** Rezept zum bestehenden Rezeptbezug im Hintergrund laden; schlägt es fehl, bleibt der Bezug, nur der Stepper fehlt. */
    private fun loadLinkedRecipe(recipeId: String) {
        viewModelScope.launch {
            val result = dishes.recipe(recipeId)
            if (result is ApiResult.Success) {
                _state.update { if (it.recipeId == recipeId && it.recipe == null) it.copy(recipe = result.data) else it }
            }
        }
    }

    private fun onReview(event: DishEditEvent) {
        when (event) {
            DishEditEvent.Approve -> approve()
            DishEditEvent.RequestConfirmCooked ->
                _state.update {
                    if (it.canConfirmCooking) {
                        it.copy(
                            showCookedConfirm = true,
                            error = null,
                        )
                    } else {
                        it
                    }
                }
            DishEditEvent.CancelConfirmCooked -> _state.update { it.copy(showCookedConfirm = false, error = null) }
            DishEditEvent.ConfirmCooked -> confirmCooked()
            DishEditEvent.RequestReject ->
                _state.update {
                    if (it.mode ==
                        EditorMode.PARENT_REVIEW
                    ) {
                        it.copy(showRejectDialog = true, rejectReason = "", error = null)
                    } else {
                        it
                    }
                }
            is DishEditEvent.RejectReasonChanged -> _state.update { it.copy(rejectReason = event.value.take(PlanLimits.REASON_MAX)) }
            DishEditEvent.CancelReject -> _state.update { it.copy(showRejectDialog = false, error = null) }
            DishEditEvent.ConfirmReject -> reject()
            else -> Unit
        }
    }

    private fun edit(change: (DishEditUiState) -> DishEditUiState) = _state.update { change(it).copy(error = null) }

    /** Wechsel der Köchin/des Kochs: Kinder erlauben nur ihre Anwesenheitstage; nicht mehr wählbarer Tag fällt weg. */
    private fun DishEditUiState.withCook(memberId: String): DishEditUiState {
        if (mode == EditorMode.PARENT_MOVE || mode == EditorMode.CHILD) return this
        val child = children.firstOrNull { it.memberId == memberId }
        val allowed = allowedDates(weekStart, takenDates, child)
        val selected = date.takeIf { it in allowed } ?: preselectDate(date, allowed) ?: date
        return copy(cookId = memberId, allowedDates = allowed, date = selected)
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = dishes.load(_state.value.weekStart)) {
                is ApiResult.Success -> {
                    _state.update { it.withLoaded(result.data) }
                    _state.value
                        .takeIf { it.loadError == null }
                        ?.recipeId
                        ?.let(::loadLinkedRecipe)
                    pendingRecipeId?.let { pickRecipe(it) }
                    pendingRecipeId = null
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    private fun DishEditUiState.withLoaded(data: DishEditorData): DishEditUiState {
        val session = sessions.current
        val isParent = session?.role == Role.PARENT
        val planned = data.week.days.mapNotNull { it.dish }
        val existing =
            dishId?.let { id ->
                planned.firstOrNull { it.id == id }
                    ?: return copy(isLoading = false, loadError = AppError.NotFound)
            }
        val base =
            copy(
                isLoading = false,
                parents = data.parents,
                children = data.children,
                existing = existing,
                takenDates = planned.filter { it.id != dishId }.map { it.date }.toSet(),
                now = clock.instant(),
                presentByDate = data.presentByDate,
                recipeId = existing?.recipeId,
                servings = existing?.servings,
            )
        val cookId =
            if (isParent) {
                parentCookId(data, existing, session?.memberId, args.cookId)
            } else {
                childCookId(data, existing, session?.memberId) ?: return base.copy(loadError = notCooking(existing, session?.memberId))
            }
        return base.withCookAndMode(cookId, isParent, existing)
    }

    private fun DishEditUiState.withCookAndMode(
        cookId: String?,
        isParent: Boolean,
        existing: Dish?,
    ): DishEditUiState {
        val child = children.firstOrNull { it.memberId == cookId }
        val allowed = allowedDates(weekStart, takenDates, child)
        return copy(
            cookId = cookId,
            mode = editorMode(isParent, existing, cookIsChild = child != null),
            canConfirmCooking = canConfirmCooking(isParent, existing, child != null, clock.instant().atZone(BerlinZone).toLocalDate()),
            allowedDates = allowed,
            date = existing?.date ?: preselectDate(args.date, allowed) ?: args.date,
            name = existing?.name.orEmpty(),
            ingredientsText = existing?.ingredients?.joinToString("\n").orEmpty(),
        )
    }

    private fun save() {
        val current = _state.value
        val cookId = current.cookId
        if (!current.canSave || cookId == null) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val input = DishInput(current.date, cookId, current.name, current.ingredientsText, current.recipeRef)
            val result = dishes.save(dishId, input, current.ingredientsRequired)
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
            }
        }
    }

    private fun approve() {
        val current = _state.value
        val id = dishId ?: return
        if (current.isBusy || current.mode != EditorMode.PARENT_REVIEW) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = dishes.review.approve(id)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
            }
        }
    }

    private fun confirmCooked() {
        val current = _state.value
        val id = dishId ?: return
        if (current.isBusy || !current.canConfirmCooking) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = dishes.review.confirmCooked(id)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, showCookedConfirm = false, isDone = true) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, showCookedConfirm = false, error = result.error) }
            }
        }
    }

    private fun reject() {
        val id = dishId ?: return
        val current = _state.value
        if (current.isBusy || current.mode != EditorMode.PARENT_REVIEW) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = dishes.review.reject(id, current.rejectReason)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, showRejectDialog = false, isDone = true) }
                // Eine fehlende/zu lange Begründung korrigiert man im Dialog; alles andere (z. B. 409) schließt ihn.
                is ApiResult.Failure ->
                    _state.update {
                        it.copy(isBusy = false, error = result.error, showRejectDialog = result.error is AppError.Validation)
                    }
            }
        }
    }

    private fun delete() {
        val id = dishId ?: return
        if (_state.value.isBusy || !_state.value.canDelete) return
        _state.update { it.copy(isBusy = true, showDeleteConfirm = false, error = null) }
        viewModelScope.launch {
            when (val result = dishes.delete(id)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
                // Schon gelöscht (anderes Gerät): Ziel erreicht, der Plan lädt neu.
                is ApiResult.Failure ->
                    if (result.error == AppError.NotFound) {
                        _state.update { it.copy(isBusy = false, isDone = true) }
                    } else {
                        _state.update { it.copy(isBusy = false, error = result.error) }
                    }
            }
        }
    }
}
