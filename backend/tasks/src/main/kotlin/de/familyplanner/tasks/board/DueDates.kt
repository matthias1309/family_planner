package de.familyplanner.tasks.board

import de.familyplanner.core.error.AppException
import java.time.Duration
import java.time.Instant

/** A due date must lie after [now] and at most [DUE_DATE_MAX_DAYS] ahead; `null` means no due date (REQ-TASK-026). */
internal fun validatedDueAt(
    dueAt: Instant?,
    now: Instant,
): Instant? {
    if (dueAt == null) return null
    if (!dueAt.isAfter(now)) throw AppException.Validation("Die Frist muss in der Zukunft liegen")
    if (dueAt.isAfter(now.plus(Duration.ofDays(DUE_DATE_MAX_DAYS)))) {
        throw AppException.Validation("Die Frist darf höchstens $DUE_DATE_MAX_DAYS Tage in der Zukunft liegen")
    }
    return dueAt
}

/** Reported (or, without a report, accepted) after the due date (REQ-TASK-027). Exactly on time is not late. */
fun TaskCard.isReportedLate(): Boolean {
    val finishedAt = reportedAt ?: doneAt
    return dueAt != null && finishedAt != null && finishedAt.isAfter(dueAt)
}
