package de.familyplanner.tasks.fakes

import de.familyplanner.core.events.EventNotifier
import de.familyplanner.core.events.TaskAccepted
import de.familyplanner.core.member.MemberId
import de.familyplanner.tasks.board.TaskCard
import de.familyplanner.tasks.board.TaskCardId
import de.familyplanner.tasks.board.TaskCardRepository
import de.familyplanner.tasks.board.TaskStatus
import de.familyplanner.tasks.board.TaskTransition
import de.familyplanner.tasks.board.TransitionOutcome
import de.familyplanner.tasks.board.from
import de.familyplanner.tasks.board.isRemovable
import de.familyplanner.tasks.board.to
import de.familyplanner.tasks.catalog.CatalogRepository
import de.familyplanner.tasks.catalog.CatalogTask
import de.familyplanner.tasks.catalog.CatalogTaskId
import java.time.Instant

class InMemoryCatalogRepository : CatalogRepository {
    val tasks = mutableMapOf<CatalogTaskId, CatalogTask>()

    override suspend fun findAll(includeArchived: Boolean): List<CatalogTask> =
        tasks.values.filter { includeArchived || !it.archived }.sortedBy { it.title.lowercase() }

    override suspend fun findById(id: CatalogTaskId): CatalogTask? = tasks[id]

    override suspend fun findActiveByIds(ids: Collection<CatalogTaskId>): List<CatalogTask> =
        ids.mapNotNull { tasks[it] }.filter { !it.archived }

    override suspend fun create(
        task: CatalogTask,
        at: Instant,
    ) {
        tasks[task.id] = task
    }

    override suspend fun update(
        task: CatalogTask,
        at: Instant,
    ): Boolean {
        val existing = tasks[task.id] ?: return false
        if (existing.archived) return false
        tasks[task.id] = task
        return true
    }

    override suspend fun archive(
        id: CatalogTaskId,
        at: Instant,
    ): Boolean {
        val existing = tasks[id] ?: return false
        tasks[id] = existing.copy(archived = true)
        return true
    }
}

/** Records accepted cards in [recordedEvents] instead of an event log. */
class InMemoryTaskCardRepository : TaskCardRepository {
    val cards = mutableMapOf<TaskCardId, TaskCard>()
    val recordedEvents = mutableListOf<TaskAccepted>()

    override suspend fun createAll(cards: List<TaskCard>) {
        cards.forEach { this.cards[it.id] = it }
    }

    override suspend fun findById(id: TaskCardId): TaskCard? = cards[id]

    override suspend fun findBoard(doneSince: Instant): List<TaskCard> =
        cards.values.filter { it.status != TaskStatus.DONE || (it.doneAt ?: Instant.MIN) >= doneSince }

    override suspend fun findDone(
        memberId: MemberId,
        limit: Int,
    ): List<TaskCard> =
        cards.values
            .filter { it.memberId == memberId && it.status == TaskStatus.DONE }
            .sortedByDescending { it.doneAt }
            .take(limit)

    override suspend fun transition(
        id: TaskCardId,
        transition: TaskTransition,
        by: MemberId,
        reason: String?,
        at: Instant,
    ): TransitionOutcome {
        val card = cards[id] ?: return TransitionOutcome.NOT_FOUND
        if (card.status !in transition.from()) return TransitionOutcome.WRONG_STATUS
        val moved = card.copy(status = transition.to())
        cards[id] =
            when (transition) {
                TaskTransition.REPORT -> moved.copy(reportedAt = at, rejectionReason = null)
                TaskTransition.WITHDRAW -> moved.copy(reportedAt = null)
                TaskTransition.REJECT -> moved.copy(reportedAt = null, rejectionReason = reason)
                TaskTransition.ACCEPT -> moved.copy(doneAt = at)
            }
        if (transition == TaskTransition.ACCEPT) {
            recordedEvents += TaskAccepted(id.value, card.memberId.value, card.title, card.points, by.value, at.toEpochMilli())
        }
        return TransitionOutcome.CHANGED
    }

    override suspend fun remove(id: TaskCardId): TransitionOutcome {
        val card = cards[id] ?: return TransitionOutcome.NOT_FOUND
        if (!card.status.isRemovable()) return TransitionOutcome.WRONG_STATUS
        cards.remove(id)
        return TransitionOutcome.CHANGED
    }
}

class CountingEventNotifier : EventNotifier {
    var calls = 0
        private set

    override suspend fun dispatchPending() {
        calls++
    }
}
