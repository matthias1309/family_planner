package de.familyplanner.tasks.board

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.member.MemberId
import de.familyplanner.tasks.catalog.CatalogTask
import de.familyplanner.tasks.catalog.CatalogTaskId
import java.time.Instant

/** Blank reasons count as none; otherwise 1..[REASON_MAX] characters. */
internal fun validatedReason(reason: String?): String? =
    reason?.takeIf { it.isNotBlank() }?.let { validatedName(it, "Begründung", REASON_MAX) }

internal fun forbidden(transition: TaskTransition): AppException.Forbidden =
    when (transition) {
        TaskTransition.REPORT, TaskTransition.WITHDRAW -> AppException.Forbidden("Nur eigene Aufgaben")
        TaskTransition.ACCEPT, TaskTransition.REJECT -> AppException.Forbidden()
    }

/** Maps the repository outcome of a [transition] to the HTTP-facing exception. */
internal fun requireChanged(
    outcome: TransitionOutcome,
    transition: TaskTransition,
) {
    when (outcome) {
        TransitionOutcome.CHANGED -> Unit
        TransitionOutcome.NOT_FOUND -> throw AppException.NotFound(CARD_NOT_FOUND)
        TransitionOutcome.WRONG_STATUS -> throw AppException.Conflict(conflictText(transition))
    }
}

private fun conflictText(transition: TaskTransition) =
    when (transition) {
        TaskTransition.REPORT -> "Die Aufgabe ist nicht mehr offen"
        TaskTransition.ACCEPT -> "Die Aufgabe ist schon erledigt"
        TaskTransition.WITHDRAW, TaskTransition.REJECT -> "Die Aufgabe wartet nicht auf Abnahme"
    }

/** Maps a removal [outcome] to the HTTP-facing exception; [conflictText] explains a refused status. */
internal fun requireRemoved(
    outcome: TransitionOutcome,
    conflictText: String,
) {
    when (outcome) {
        TransitionOutcome.CHANGED -> Unit
        TransitionOutcome.NOT_FOUND -> throw AppException.NotFound(CARD_NOT_FOUND)
        TransitionOutcome.WRONG_STATUS -> throw AppException.Conflict(conflictText)
    }
}

/** Children may only put back cards they took from the pool themselves (REQ-TASK-028). */
internal fun requireOwnSelfAssigned(
    actor: Actor,
    card: TaskCard,
) {
    if (card.memberId != actor.id || !card.selfAssigned) {
        throw AppException.Forbidden("Nur selbst genommene Aufgaben können zurückgelegt werden")
    }
}

internal fun availableTask(
    available: Map<CatalogTaskId, CatalogTask>,
    id: CatalogTaskId,
): CatalogTask = available[id] ?: throw AppException.Validation("Aufgabe nicht verfügbar")

/** A fresh card (TODO, or REVIEW when [alreadyDone]) with title, emoji and points copied from [task]. */
internal fun newCard(
    id: TaskCardId,
    memberId: MemberId,
    task: CatalogTask,
    now: Instant,
    dueAt: Instant? = null,
    selfAssigned: Boolean = false,
    alreadyDone: Boolean = false,
) = TaskCard(
    id = id,
    memberId = memberId,
    catalogTaskId = task.id,
    title = task.title,
    emoji = task.emoji,
    points = task.points,
    status = if (alreadyDone) TaskStatus.REVIEW else TaskStatus.TODO,
    rejectionReason = null,
    assignedAt = now,
    reportedAt = if (alreadyDone) now else null,
    doneAt = null,
    dueAt = dueAt,
    selfAssigned = selfAssigned,
)

internal fun sortedForLane(cards: List<TaskCard>): List<TaskCard> =
    cards.filter { it.status == TaskStatus.TODO }.sortedBy { it.assignedAt } +
        cards.filter { it.status == TaskStatus.REVIEW }.sortedBy { it.reportedAt } +
        cards.filter { it.status == TaskStatus.DONE }.sortedByDescending { it.doneAt }
