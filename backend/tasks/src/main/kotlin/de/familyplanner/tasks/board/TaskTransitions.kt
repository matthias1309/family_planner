package de.familyplanner.tasks.board

import de.familyplanner.tasks.board.TaskStatus.DONE
import de.familyplanner.tasks.board.TaskStatus.REVIEW
import de.familyplanner.tasks.board.TaskStatus.TODO
import de.familyplanner.tasks.board.TaskTransition.ACCEPT
import de.familyplanner.tasks.board.TaskTransition.REJECT
import de.familyplanner.tasks.board.TaskTransition.REPORT
import de.familyplanner.tasks.board.TaskTransition.WITHDRAW

/** Statuses a transition may start from (REQ-TASK-022/023). */
fun TaskTransition.from(): Set<TaskStatus> =
    when (this) {
        REPORT -> setOf(TODO)
        WITHDRAW -> setOf(REVIEW)
        ACCEPT -> setOf(REVIEW, TODO)
        REJECT -> setOf(REVIEW)
    }

fun TaskTransition.to(): TaskStatus =
    when (this) {
        REPORT -> REVIEW
        WITHDRAW, REJECT -> TODO
        ACCEPT -> DONE
    }

/** Children act only on their own lane and only report/withdraw; parents accept/reject (REQ-TASK-022/023). */
fun TaskTransition.isAllowedFor(
    actor: Actor,
    card: TaskCard,
): Boolean =
    when (this) {
        REPORT, WITHDRAW -> !actor.isParent && card.memberId == actor.id
        ACCEPT, REJECT -> actor.isParent
    }

fun TaskStatus.isRemovable(): Boolean = this != DONE
