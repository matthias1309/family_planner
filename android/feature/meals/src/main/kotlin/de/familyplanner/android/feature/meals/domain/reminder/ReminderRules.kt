package de.familyplanner.android.feature.meals.domain.reminder

import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import java.time.Instant
import java.time.LocalDate

private const val UNKNOWN_NAME = "?"

/** Erinnert wird, solange nichts (mehr) eingereicht ist: offen oder abgelehnt (REQ-MEAL-050). */
fun needsReminder(state: CookState?): Boolean = state == CookState.NOT_SUBMITTED || state == CookState.REJECTED

/** Kalendertage vor der Frist zur Frist-Uhrzeit (Berlin); schon vergangene Zeitpunkte entfallen. */
fun reminderTimes(
    weekStart: LocalDate,
    deadline: Instant,
    now: Instant,
): List<Reminder> =
    ReminderLimits.DAYS_BEFORE
        .map { Reminder(weekStart, it, deadline.atZone(BerlinZone).minusDays(it).toInstant()) }
        .filter { it.at > now }

fun Dish.known() = KnownDish(status, rejectionReason)

/**
 * Meldungen aus dem Vergleich mit dem zuletzt bekannten Stand. `previous == null` ist die Basislinie (keine Meldung).
 * Kind: eigene Gerichte werden freigegeben/abgelehnt. Eltern: ein Gericht wird neu eingereicht.
 */
fun statusNotices(
    isParent: Boolean,
    memberId: String,
    previous: Map<String, KnownDish>?,
    dishes: List<Dish>,
    names: Map<String, String>,
): List<StatusNotice> {
    if (previous == null) return emptyList()
    return dishes.mapNotNull { dish ->
        val before = previous[dish.id]
        when {
            isParent -> dish.takeIf { it.status == DishStatus.SUBMITTED && before?.status != DishStatus.SUBMITTED }?.toSubmitted(names)
            dish.cookId != memberId || before == dish.known() -> null
            else -> dish.toChildNotice()
        }
    }
}

private fun Dish.toSubmitted(names: Map<String, String>) = StatusNotice.Submitted(id, name, date, names[cookId] ?: UNKNOWN_NAME)

private fun Dish.toChildNotice(): StatusNotice? =
    when (status) {
        DishStatus.APPROVED -> StatusNotice.Approved(id, name, date)
        DishStatus.REJECTED -> StatusNotice.Rejected(id, name, date, rejectionReason)
        DishStatus.SUBMITTED -> null
    }
