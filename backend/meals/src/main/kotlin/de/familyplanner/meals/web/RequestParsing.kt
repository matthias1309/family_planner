package de.familyplanner.meals.web

import de.familyplanner.core.error.AppException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.UUID

/**
 * Dates are stored as `yyyy-MM-dd` text and compared lexicographically, which only holds for four-digit years;
 * the range also keeps date arithmetic far away from `LocalDate` overflow.
 */
const val MIN_SUPPORTED_YEAR = 2000
const val MAX_SUPPORTED_YEAR = 2100

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

fun parseDate(
    value: String,
    field: String,
): LocalDate {
    val date =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw AppException.Validation("Ungültiges Datum '$field'").also { it.initCause(e) }
        }
    if (date.year !in MIN_SUPPORTED_YEAR..MAX_SUPPORTED_YEAR) {
        throw AppException.Validation("Datum '$field' muss zwischen den Jahren $MIN_SUPPORTED_YEAR und $MAX_SUPPORTED_YEAR liegen")
    }
    return date
}

/** Accepts exactly `HH:mm`; seconds or missing leading zeros are rejected. */
fun parseTime(
    value: String,
    field: String,
): LocalTime =
    try {
        LocalTime.parse(value, TIME_FORMAT)
    } catch (e: DateTimeParseException) {
        throw AppException.Validation("Ungültige Uhrzeit '$field'").also { it.initCause(e) }
    }

fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

/** Parses an ID from a request body; malformed IDs fail with 400 instead of a misleading 404. */
internal fun parseUuid(
    value: String,
    field: String,
): String =
    runCatching { UUID.fromString(value).toString() }
        .getOrElse { throw AppException.Validation("Ungültige Kennung '$field'") }
