package de.familyplanner.tasks.board

import de.familyplanner.core.member.Member
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.tasks.catalog.CatalogTaskId
import java.time.Instant

const val DONE_VISIBLE_DAYS = 7L
const val HISTORY_LIMIT_DEFAULT = 50
const val HISTORY_LIMIT_MAX = 100
const val ASSIGN_MAX = 20
const val REASON_MAX = 200

@JvmInline
value class TaskCardId(
    val value: String,
)

enum class TaskStatus { TODO, REVIEW, DONE }

/** Who performs a write. */
data class Actor(
    val id: MemberId,
    val role: Role,
) {
    val isParent: Boolean get() = role == Role.PARENT
}

/** A task assigned to one child; title, emoji and points are copies of the catalog task at assignment time. */
data class TaskCard(
    val id: TaskCardId,
    val memberId: MemberId,
    val catalogTaskId: CatalogTaskId?,
    val title: String,
    val emoji: String?,
    val points: Int,
    val status: TaskStatus,
    val rejectionReason: String?,
    val assignedAt: Instant,
    val reportedAt: Instant?,
    val doneAt: Instant?,
)

/** One child's column on the board. */
data class Lane(
    val member: Member,
    val cards: List<TaskCard>,
)

enum class TaskTransition { REPORT, WITHDRAW, ACCEPT, REJECT }

enum class TransitionOutcome { CHANGED, NOT_FOUND, WRONG_STATUS }
