package de.familyplanner.android.feature.tasks.domain.notice

import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskLane
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus

/** Vergleicht den letzten Stand mit dem neuen; ohne vorherigen Stand keine Hinweise (Basislinie, REQ-TASK-030). */
fun taskNotices(
    isParent: Boolean,
    memberId: String,
    previous: List<TaskLane>?,
    current: List<TaskLane>,
): List<TaskNotice> {
    if (previous == null) return emptyList()
    val before = previous.flatMap { it.cards }.associateBy { it.id }
    return if (isParent) {
        current.flatMap { lane ->
            lane.cards
                .filter { it.status == TaskStatus.REVIEW && before[it.id]?.status != TaskStatus.REVIEW }
                .map { TaskNotice.Reported(it.id, it.title, lane.name) }
        }
    } else {
        current
            .filter { it.memberId == memberId }
            .flatMap { lane -> lane.cards.mapNotNull { childNotice(before[it.id], it) } }
    }
}

private fun childNotice(
    before: TaskCard?,
    now: TaskCard,
): TaskNotice? =
    when {
        before == null -> TaskNotice.Assigned(now.id, now.title).takeIf { now.status == TaskStatus.TODO }
        before.status != TaskStatus.DONE && now.status == TaskStatus.DONE -> TaskNotice.Accepted(now.id, now.title, now.points)
        before.status == TaskStatus.REVIEW && now.status == TaskStatus.TODO -> TaskNotice.Rejected(now.id, now.title, now.rejectionReason)
        else -> null
    }
