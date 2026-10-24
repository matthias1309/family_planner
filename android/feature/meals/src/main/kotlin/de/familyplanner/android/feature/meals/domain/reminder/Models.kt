package de.familyplanner.android.feature.meals.domain.reminder

import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import java.time.Instant
import java.time.LocalDate

/** Eingeplante Erinnerung: [daysBefore] Tage vor der Frist der Planwoche [weekStart]. */
data class Reminder(
    val weekStart: LocalDate,
    val daysBefore: Long,
    val at: Instant,
)

/** Zuletzt bekannter Stand eines Gerichts (Vergleich für Statusmeldungen). */
data class KnownDish(
    val status: DishStatus,
    val rejectionReason: String?,
)

/** Ergebnis des letzten erfolgreichen Abgleichs; Quelle für Alarme offline und nach Neustart. */
data class ReminderSnapshot(
    val memberId: String,
    val weekStart: LocalDate,
    /** Nur Kind mit Frist. */
    val deadline: Instant?,
    /** Nur Kind, das in der Woche kocht. */
    val cookState: CookState?,
    val reminders: List<Reminder>,
    val dishes: Map<String, KnownDish>,
)

sealed interface StatusNotice {
    val dishId: String

    data class Approved(
        override val dishId: String,
        val dishName: String,
        val date: LocalDate,
    ) : StatusNotice

    data class Rejected(
        override val dishId: String,
        val dishName: String,
        val date: LocalDate,
        val reason: String?,
    ) : StatusNotice

    data class Submitted(
        override val dishId: String,
        val dishName: String,
        val date: LocalDate,
        val cookName: String,
    ) : StatusNotice
}

data class DeadlineReminder(
    val deadline: Instant,
    val rejected: Boolean,
)

/** Offene Einreichung laut lokalem Stand (WhatsApp-Hinweis, REQ-MEAL-051). */
data class PendingCooking(
    val deadline: Instant,
    val rejected: Boolean,
)

enum class SyncOutcome { SYNCED, UNREACHABLE, SIGNED_OUT }
