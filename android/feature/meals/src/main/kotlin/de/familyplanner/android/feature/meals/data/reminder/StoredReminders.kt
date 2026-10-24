package de.familyplanner.android.feature.meals.data.reminder

import de.familyplanner.android.feature.meals.data.mapAllOrNull
import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.reminder.KnownDish
import de.familyplanner.android.feature.meals.domain.reminder.Reminder
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSnapshot
import kotlinx.serialization.Serializable
import java.time.Instant

internal const val REMINDER_STORE_VERSION = 1

/** Dateiformat des Abgleich-Stands (ADR-0013), getrennt von der Domäne. */
@Serializable
data class StoredReminders(
    val version: Int = REMINDER_STORE_VERSION,
    val snapshot: StoredSnapshot? = null,
)

@Serializable
data class StoredSnapshot(
    val memberId: String,
    val weekStart: String,
    val deadlineMillis: Long? = null,
    val cookState: String? = null,
    val reminders: List<StoredReminder> = emptyList(),
    val dishes: Map<String, StoredKnownDish> = emptyMap(),
)

@Serializable
data class StoredReminder(
    val weekStart: String,
    val daysBefore: Long,
    val atMillis: Long,
)

@Serializable
data class StoredKnownDish(
    val status: String,
    val rejectionReason: String? = null,
)

internal fun ReminderSnapshot.toStored() =
    StoredSnapshot(
        memberId = memberId,
        weekStart = weekStart.toString(),
        deadlineMillis = deadline?.toEpochMilli(),
        cookState = cookState?.name,
        reminders = reminders.map { StoredReminder(it.weekStart.toString(), it.daysBefore, it.at.toEpochMilli()) },
        dishes = dishes.mapValues { StoredKnownDish(it.value.status.name, it.value.rejectionReason) },
    )

/** `null`, wenn ein Wert unlesbar ist — der Stand zählt dann als nicht gespeichert. */
internal fun StoredSnapshot.toDomain(): ReminderSnapshot? {
    val start = parseDate(weekStart)
    val state = cookState?.let { name -> CookState.entries.firstOrNull { it.name == name } }
    val mappedReminders = reminders.mapAllOrNull { it.toDomain() }
    val mappedDishes = dishes.toKnownDishes()
    val unreadable = start == null || (cookState != null && state == null) || mappedReminders == null || mappedDishes == null
    if (unreadable) return null
    return ReminderSnapshot(memberId, start, deadlineMillis?.let(Instant::ofEpochMilli), state, mappedReminders, mappedDishes)
}

private fun StoredReminder.toDomain(): Reminder? = parseDate(weekStart)?.let { Reminder(it, daysBefore, Instant.ofEpochMilli(atMillis)) }

private fun Map<String, StoredKnownDish>.toKnownDishes(): Map<String, KnownDish>? {
    val result = mutableMapOf<String, KnownDish>()
    for ((id, dish) in this) {
        val status = DishStatus.entries.firstOrNull { it.name == dish.status } ?: return null
        result[id] = KnownDish(status, dish.rejectionReason)
    }
    return result
}
