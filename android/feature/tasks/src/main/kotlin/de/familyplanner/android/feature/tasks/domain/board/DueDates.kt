package de.familyplanner.android.feature.tasks.domain.board

import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.tasks.domain.reminder.TaskReminder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Überfällig: noch in TODO und Fristende erreicht (REQ-TASK-027); offline mit der Geräteuhr. */
fun TaskCard.isOverdue(now: Instant): Boolean = status == TaskStatus.TODO && dueAt != null && !dueAt.isAfter(now)

/** Frist aus Datum und Uhrzeit in Berlin. */
fun dueInstant(
    date: LocalDate,
    time: LocalTime,
): Instant = ZonedDateTime.of(date, time, BerlinZone).toInstant()

/** `null` = gültig; die Frist muss in der Zukunft liegen (der Server prüft ebenso). */
fun validateDueAt(
    dueAt: Instant?,
    now: Instant,
): AppError.Validation? = if (dueAt != null && !dueAt.isAfter(now)) AppError.Validation("Die Frist muss in der Zukunft liegen") else null

/** Erinnerungen 2 h vor Fristende: nur Kinder, nur eigene Karten in TODO mit Frist; vergangene Zeitpunkte entfallen (REQ-TASK-031). */
fun taskReminders(
    lanes: List<TaskLane>,
    memberId: String,
    isParent: Boolean,
    now: Instant,
): List<TaskReminder> =
    if (isParent) {
        emptyList()
    } else {
        lanes
            .filter { it.memberId == memberId }
            .flatMap { it.cards }
            .filter { it.status == TaskStatus.TODO }
            .mapNotNull { card ->
                card.dueAt?.let { TaskReminder(card.id, card.title, it, it.minus(Duration.ofHours(TaskLimits.REMINDER_LEAD_HOURS))) }
            }.filter { it.at.isAfter(now) }
    }
