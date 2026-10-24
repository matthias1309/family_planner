package de.familyplanner.tasks.persistence

import de.familyplanner.core.events.TaskAccepted
import de.familyplanner.core.events.recordEvent
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.tasks.board.TaskCard
import de.familyplanner.tasks.board.TaskCardId
import de.familyplanner.tasks.board.TaskCardRepository
import de.familyplanner.tasks.board.TaskStatus
import de.familyplanner.tasks.board.TaskTransition
import de.familyplanner.tasks.board.TransitionOutcome
import de.familyplanner.tasks.board.from
import de.familyplanner.tasks.board.to
import de.familyplanner.tasks.catalog.CatalogTaskId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

class ExposedTaskCardRepository(
    private val db: Database,
) : TaskCardRepository {
    override suspend fun createAll(cards: List<TaskCard>) {
        dbQuery(db) {
            TaskCardsTable.batchInsert(cards) { card ->
                this[TaskCardsTable.id] = card.id.value
                this[TaskCardsTable.memberId] = card.memberId.value
                this[TaskCardsTable.catalogTaskId] = card.catalogTaskId?.value
                this[TaskCardsTable.title] = card.title
                this[TaskCardsTable.emoji] = card.emoji
                this[TaskCardsTable.points] = card.points
                this[TaskCardsTable.status] = card.status.name
                this[TaskCardsTable.rejectionReason] = card.rejectionReason
                this[TaskCardsTable.assignedAt] = card.assignedAt.toEpochMilli()
                this[TaskCardsTable.reportedAt] = card.reportedAt?.toEpochMilli()
                this[TaskCardsTable.doneAt] = card.doneAt?.toEpochMilli()
            }
        }
    }

    override suspend fun findById(id: TaskCardId): TaskCard? = dbQuery(db) { row(id)?.toCard() }

    override suspend fun findBoard(doneSince: Instant): List<TaskCard> =
        dbQuery(db) {
            TaskCardsTable
                .selectAll()
                .where {
                    (TaskCardsTable.status neq TaskStatus.DONE.name) or (TaskCardsTable.doneAt greaterEq doneSince.toEpochMilli())
                }.map { it.toCard() }
        }

    override suspend fun findDone(
        memberId: MemberId,
        limit: Int,
    ): List<TaskCard> =
        dbQuery(db) {
            TaskCardsTable
                .selectAll()
                .where { (TaskCardsTable.memberId eq memberId.value) and (TaskCardsTable.status eq TaskStatus.DONE.name) }
                .orderBy(TaskCardsTable.doneAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toCard() }
        }

    override suspend fun transition(
        id: TaskCardId,
        transition: TaskTransition,
        by: MemberId,
        reason: String?,
        at: Instant,
    ): TransitionOutcome =
        dbQuery(db) {
            val changed =
                TaskCardsTable.update(
                    { (TaskCardsTable.id eq id.value) and (TaskCardsTable.status inList transition.from().map { it.name }) },
                ) {
                    it[status] = transition.to().name
                    when (transition) {
                        TaskTransition.REPORT -> {
                            it[reportedAt] = at.toEpochMilli()
                            it[rejectionReason] = null
                        }
                        TaskTransition.WITHDRAW -> it[reportedAt] = null
                        TaskTransition.REJECT -> {
                            it[reportedAt] = null
                            it[rejectionReason] = reason
                        }
                        TaskTransition.ACCEPT -> {
                            it[doneAt] = at.toEpochMilli()
                            it[doneBy] = by.value
                        }
                    }
                }
            if (changed == 0) return@dbQuery if (row(id) != null) TransitionOutcome.WRONG_STATUS else TransitionOutcome.NOT_FOUND
            if (transition == TaskTransition.ACCEPT) recordAccepted(id, by, at)
            TransitionOutcome.CHANGED
        }

    override suspend fun remove(id: TaskCardId): TransitionOutcome =
        dbQuery(db) {
            val removed = TaskCardsTable.deleteWhere { (TaskCardsTable.id eq id.value) and (status neq TaskStatus.DONE.name) }
            when {
                removed > 0 -> TransitionOutcome.CHANGED
                row(id) != null -> TransitionOutcome.WRONG_STATUS
                else -> TransitionOutcome.NOT_FOUND
            }
        }

    private fun JdbcTransaction.row(id: TaskCardId): ResultRow? =
        TaskCardsTable.selectAll().where { TaskCardsTable.id eq id.value }.firstOrNull()

    /** Runs inside the transition's transaction, so the event exists exactly when the card is DONE (ADR-0016). */
    private fun JdbcTransaction.recordAccepted(
        id: TaskCardId,
        by: MemberId,
        at: Instant,
    ) {
        val card = checkNotNull(row(id)) { "Accepted card vanished" }
        val event =
            TaskAccepted(
                cardId = id.value,
                memberId = card[TaskCardsTable.memberId],
                title = card[TaskCardsTable.title],
                points = card[TaskCardsTable.points],
                acceptedBy = by.value,
                acceptedAt = at.toEpochMilli(),
            )
        recordEvent(event, at)
    }

    private fun ResultRow.toCard() =
        TaskCard(
            id = TaskCardId(this[TaskCardsTable.id]),
            memberId = MemberId(this[TaskCardsTable.memberId]),
            catalogTaskId = this[TaskCardsTable.catalogTaskId]?.let { CatalogTaskId(it) },
            title = this[TaskCardsTable.title],
            emoji = this[TaskCardsTable.emoji],
            points = this[TaskCardsTable.points],
            status = TaskStatus.valueOf(this[TaskCardsTable.status]),
            rejectionReason = this[TaskCardsTable.rejectionReason],
            assignedAt = Instant.ofEpochMilli(this[TaskCardsTable.assignedAt]),
            reportedAt = this[TaskCardsTable.reportedAt]?.let { Instant.ofEpochMilli(it) },
            doneAt = this[TaskCardsTable.doneAt]?.let { Instant.ofEpochMilli(it) },
        )
}
