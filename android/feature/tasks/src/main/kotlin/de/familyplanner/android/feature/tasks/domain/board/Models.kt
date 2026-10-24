package de.familyplanner.android.feature.tasks.domain.board

import de.familyplanner.android.core.data.AppError
import java.time.Instant

enum class TaskStatus { TODO, REVIEW, DONE }

data class TaskCard(
    val id: String,
    val memberId: String,
    val title: String,
    val emoji: String?,
    val points: Int,
    val status: TaskStatus,
    val rejectionReason: String?,
    val assignedAt: Instant,
    val reportedAt: Instant?,
    val doneAt: Instant?,
)

data class TaskLane(
    val memberId: String,
    val name: String,
    val cards: List<TaskCard>,
)

/** Board vom Server oder aus dem lokalen Stand (ADR-0011). */
data class BoardSnapshot(
    val lanes: List<TaskLane>,
    val syncedAt: Instant,
)

/** Der lokale Stand gehört genau einem Mitglied (Gerät kann das Konto wechseln). */
data class CachedBoard(
    val memberId: String,
    val board: BoardSnapshot,
)

sealed interface BoardLoad {
    data class Fresh(
        val board: BoardSnapshot,
    ) : BoardLoad

    data class Cached(
        val board: BoardSnapshot,
        val error: AppError,
    ) : BoardLoad

    data class Failed(
        val error: AppError,
    ) : BoardLoad
}
