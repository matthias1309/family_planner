package de.familyplanner.android.feature.tasks.domain.board

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.data.validationFailure
import de.familyplanner.android.core.ui.BerlinZone
import java.time.Clock

/** Verbindungsprobleme, bei denen der gespeicherte Stand gezeigt wird (ADR-0011). */
private fun AppError.allowsCachedBoard() = this == AppError.Network || this == AppError.Certificate || this == AppError.Server

class LoadBoardUseCase(
    private val repository: TaskRepository,
    private val cache: BoardCache,
    private val sessions: SessionStore,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke(): BoardLoad {
        val memberId = sessions.current?.memberId ?: return BoardLoad.Failed(AppError.Unauthorized)
        return when (val result = repository.board()) {
            is ApiResult.Success -> {
                val board = BoardSnapshot(result.data, clock.instant())
                cache.write(memberId, board)
                BoardLoad.Fresh(board)
            }
            is ApiResult.Failure -> {
                val cached = if (result.error.allowsCachedBoard()) cache.read(memberId) else null
                if (cached != null) BoardLoad.Cached(cached, result.error) else BoardLoad.Failed(result.error)
            }
        }
    }
}

/** Übergang einer Karte; der lokale Stand wird sofort mitgeführt, damit Badge und Hinweis-Basislinie stimmen. */
class MoveCardUseCase(
    private val repository: TaskRepository,
    private val cache: BoardCache,
    private val sessions: SessionStore,
) {
    suspend operator fun invoke(
        cardId: String,
        action: CardAction,
        reason: String? = null,
    ): ApiResult<TaskCard> {
        val trimmed = reason?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && trimmed.length > TaskLimits.REASON_MAX) {
            return validationFailure("Begründung darf höchstens ${TaskLimits.REASON_MAX} Zeichen lang sein")
        }
        val result =
            when (action) {
                CardAction.REPORT -> repository.report(cardId)
                CardAction.WITHDRAW -> repository.withdraw(cardId)
                CardAction.ACCEPT, CardAction.ACCEPT_DIRECTLY -> repository.accept(cardId)
                CardAction.REJECT -> repository.reject(cardId, trimmed)
                CardAction.REMOVE -> throw IllegalArgumentException("REMOVE läuft über RemoveCardUseCase")
            }
        if (result is ApiResult.Success) updateCache(sessions, cache) { it.replacing(result.data) }
        return result
    }
}

class RemoveCardUseCase(
    private val repository: TaskRepository,
    private val cache: BoardCache,
    private val sessions: SessionStore,
) {
    suspend operator fun invoke(cardId: String): ApiResult<Unit> =
        repository.remove(cardId).also { result ->
            if (result is ApiResult.Success) updateCache(sessions, cache) { it.removing(cardId) }
        }
}

private suspend fun updateCache(
    sessions: SessionStore,
    cache: BoardCache,
    change: (List<TaskLane>) -> List<TaskLane>,
) {
    val memberId = sessions.current?.memberId ?: return
    cache.update(memberId) { it.copy(lanes = change(it.lanes)) }
}

class AssignTasksUseCase(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(
        memberId: String?,
        catalogTaskIds: List<String>,
    ): ApiResult<List<TaskCard>> =
        when {
            memberId == null -> validationFailure("Bitte ein Kind wählen")
            catalogTaskIds.isEmpty() -> validationFailure("Bitte mindestens eine Aufgabe wählen")
            catalogTaskIds.size > TaskLimits.ASSIGN_MAX -> validationFailure("Höchstens ${TaskLimits.ASSIGN_MAX} Aufgaben auf einmal")
            else -> repository.assign(memberId, catalogTaskIds)
        }
}

class LoadHistoryUseCase(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(memberId: String): ApiResult<List<TaskCard>> = repository.history(memberId, TaskLimits.HISTORY_LIMIT)
}
