package de.familyplanner.android.feature.meals.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun LocalTime.toApiTime(): String = TimeFormat.format(this)

internal fun parseDate(value: String?): LocalDate? =
    value?.let {
        try {
            LocalDate.parse(it)
        } catch (
            @Suppress("SwallowedException") e: DateTimeParseException,
        ) {
            null
        }
    }

internal fun parseTime(value: String): LocalTime? =
    try {
        LocalTime.parse(value)
    } catch (
        @Suppress("SwallowedException") e: DateTimeParseException,
    ) {
        null
    }

internal fun parseInstant(value: String): Instant? =
    try {
        Instant.parse(value)
    } catch (
        @Suppress("SwallowedException") e: DateTimeParseException,
    ) {
        null
    }

/** Wandelt alle Elemente um; `null`, sobald eines nicht umgewandelt werden kann (Serverfehler statt halber Liste). */
internal inline fun <T, R : Any> List<T>.mapAllOrNull(transform: (T) -> R?): List<R>? {
    val result = ArrayList<R>(size)
    for (item in this) result += transform(item) ?: return null
    return result
}
