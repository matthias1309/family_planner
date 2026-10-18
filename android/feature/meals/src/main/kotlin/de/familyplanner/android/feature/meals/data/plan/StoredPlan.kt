package de.familyplanner.android.feature.meals.data.plan

import de.familyplanner.android.feature.meals.data.mapAllOrNull
import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import kotlinx.serialization.Serializable
import java.time.Instant

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
)

@Serializable
data class StoredDish(
    val id: String,
    val cookId: String,
    val name: String,
    val ingredients: List<String>,
)

internal fun WeekPlanSnapshot.toStored() =
    StoredWeek(
        weekStart = weekStart.toString(),
        syncedAtMillis = syncedAt.toEpochMilli(),
        days =
            days.map { day ->
                StoredDay(day.date.toString(), day.dish?.let { StoredDish(it.id, it.cookId, it.name, it.ingredients) }, day.cookName)
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
    val mapped = dish?.let { Dish(it.id, day, it.cookId, it.name, it.ingredients) }
    return PlannedDay(day, mapped, cookName)
}
