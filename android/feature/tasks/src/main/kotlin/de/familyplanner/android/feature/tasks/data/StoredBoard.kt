package de.familyplanner.android.feature.tasks.data

import de.familyplanner.android.feature.tasks.domain.board.BoardSnapshot
import de.familyplanner.android.feature.tasks.domain.board.CachedBoard
import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskLane
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import kotlinx.serialization.Serializable
import java.time.Instant

internal const val CACHE_VERSION = 1

/** Dateiformat des lokalen Boards (ADR-0011), getrennt von der Domäne; Zeitpunkte als Epoch-Millis. */
@Serializable
data class StoredBoard(
    val version: Int = CACHE_VERSION,
    val memberId: String? = null,
    val syncedAtMillis: Long = 0,
    val lanes: List<StoredLane> = emptyList(),
)

@Serializable
data class StoredLane(
    val memberId: String,
    val name: String,
    val cards: List<StoredCard>,
)

@Serializable
data class StoredCard(
    val id: String,
    val memberId: String,
    val title: String,
    val emoji: String? = null,
    val points: Int,
    val status: String,
    val rejectionReason: String? = null,
    val assignedAtMillis: Long,
    val reportedAtMillis: Long? = null,
    val doneAtMillis: Long? = null,
)

internal fun CachedBoard.toStored() =
    StoredBoard(
        memberId = memberId,
        syncedAtMillis = board.syncedAt.toEpochMilli(),
        lanes = board.lanes.map { lane -> StoredLane(lane.memberId, lane.name, lane.cards.map { it.toStored() }) },
    )

private fun TaskCard.toStored() =
    StoredCard(
        id,
        memberId,
        title,
        emoji,
        points,
        status.name,
        rejectionReason,
        assignedAt.toEpochMilli(),
        reportedAt?.toEpochMilli(),
        doneAt?.toEpochMilli(),
    )

/** `null` ohne Besitzer oder mit unlesbarem Status — der Stand zählt dann als nicht gespeichert. */
internal fun StoredBoard.toDomain(): CachedBoard? {
    val owner = memberId ?: return null
    val mapped = lanes.mapAllOrNull { it.toDomain() } ?: return null
    return CachedBoard(owner, BoardSnapshot(mapped, Instant.ofEpochMilli(syncedAtMillis)))
}

private fun StoredLane.toDomain(): TaskLane? {
    val mapped = cards.mapAllOrNull { it.toDomain() } ?: return null
    return TaskLane(memberId, name, mapped)
}

private fun StoredCard.toDomain(): TaskCard? {
    val parsed = TaskStatus.entries.firstOrNull { it.name == status } ?: return null
    return TaskCard(
        id,
        memberId,
        title,
        emoji,
        points,
        parsed,
        rejectionReason,
        Instant.ofEpochMilli(assignedAtMillis),
        reportedAtMillis?.let(Instant::ofEpochMilli),
        doneAtMillis?.let(Instant::ofEpochMilli),
    )
}
