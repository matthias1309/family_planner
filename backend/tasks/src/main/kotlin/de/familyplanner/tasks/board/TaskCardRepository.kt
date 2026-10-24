package de.familyplanner.tasks.board

import de.familyplanner.core.member.MemberId
import java.time.Instant

interface TaskCardRepository {
    suspend fun createAll(cards: List<TaskCard>)

    suspend fun findById(id: TaskCardId): TaskCard?

    /** Cards not DONE plus DONE cards finished at or after [doneSince]. */
    suspend fun findBoard(doneSince: Instant): List<TaskCard>

    /** DONE cards of one child, newest first. */
    suspend fun findDone(
        memberId: MemberId,
        limit: Int,
    ): List<TaskCard>

    /**
     * Atomically moves the card if its status is in [TaskTransition.from]; on ACCEPT it records
     * [de.familyplanner.core.events.TaskAccepted] in the same transaction (ADR-0016).
     * [reason] is stored on REJECT and cleared on REPORT.
     */
    suspend fun transition(
        id: TaskCardId,
        transition: TaskTransition,
        by: MemberId,
        reason: String?,
        at: Instant,
    ): TransitionOutcome

    /** Deletes the card unless it is DONE ([TransitionOutcome.WRONG_STATUS]). */
    suspend fun remove(id: TaskCardId): TransitionOutcome
}
