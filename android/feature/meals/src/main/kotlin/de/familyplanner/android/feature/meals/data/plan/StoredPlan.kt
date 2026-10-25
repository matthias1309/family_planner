package de.familyplanner.android.feature.meals.data.plan

import de.familyplanner.android.feature.meals.data.mapAllOrNull
import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

internal const val CACHE_VERSION = 1

/** Dateiformat des Offline-Caches (ADR-0011), getrennt von der Domäne. */
@Serializable
data class StoredPlanCache(
    val version: Int = CACHE_VERSION,
    val memberId: String? = null,
    val weeks: List<StoredWeek> = emptyList(),
)

@Serializable
data class StoredWeek(
    val weekStart: String,
    val syncedAtMillis: Long,
    val days: List<StoredDay>,
)

@Serializable
data class StoredDay(
    val date: String,
    val dish: StoredDish? = null,
    val cookName: String? = null,
    val cookIsChild: Boolean = false,
)

@Serializable
data class StoredDish(
    val id: String,
    val cookId: String,
    val name: String,
    val ingredients: List<String>,
    // Defaults: Cache-Dateien aus 3b (ohne diese Felder) bleiben lesbar; dort gab es nur Elterngerichte.
    val status: String = "APPROVED",
    val rejectionReason: String? = null,
    val late: Boolean = false,
    val dayValid: Boolean = true,
    val cookedAtMillis: Long? = null,
)

internal fun Dish.toStored() =
    StoredDish(id, cookId, name, ingredients, status.name, rejectionReason, late, dayValid, cookedAt?.toEpochMilli())

internal fun WeekPlanSnapshot.toStored() =
    StoredWeek(
        weekStart = weekStart.toString(),
        syncedAtMillis = syncedAt.toEpochMilli(),
        days =
            days.map { day ->
                StoredDay(day.date.toString(), day.dish?.toStored(), day.cookName, day.cookIsChild)
            },
    )

/** `null`, wenn ein Datum unlesbar ist — der Eintrag zählt dann als nicht gespeichert. */
internal fun StoredWeek.toDomain(): WeekPlanSnapshot? {
    val start = parseDate(weekStart) ?: return null
    val mapped = days.mapAllOrNull { it.toDomain() } ?: return null
    return WeekPlanSnapshot(start, mapped, Instant.ofEpochMilli(syncedAtMillis))
}

private fun StoredDay.toDomain(): PlannedDay? {
    val day = parseDate(date) ?: return null
    val mapped = dish?.let { it.toDomain(day) ?: return null }
    return PlannedDay(day, mapped, cookName, cookIsChild)
}

private fun StoredDish.toDomain(day: LocalDate): Dish? {
    val parsed = DishStatus.entries.firstOrNull { it.name == status } ?: return null
    return Dish(
        id,
        day,
        cookId,
        name,
        ingredients,
        parsed,
        rejectionReason,
        late,
        dayValid,
        cookedAt = cookedAtMillis?.let(Instant::ofEpochMilli),
    )
}
