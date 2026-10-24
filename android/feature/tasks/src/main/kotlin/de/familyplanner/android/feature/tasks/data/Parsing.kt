package de.familyplanner.android.feature.tasks.data

import java.time.Instant
import java.time.format.DateTimeParseException

internal fun parseInstant(value: String?): Instant? =
    value?.let {
        try {
            Instant.parse(it)
        } catch (
            @Suppress("SwallowedException") e: DateTimeParseException,
        ) {
            null
        }
    }

/** Wandelt alle Elemente um; `null`, sobald eines nicht umgewandelt werden kann (Serverfehler statt halber Liste). */
internal inline fun <T, R : Any> List<T>.mapAllOrNull(transform: (T) -> R?): List<R>? {
    val result = ArrayList<R>(size)
    for (item in this) result += transform(item) ?: return null
    return result
}
