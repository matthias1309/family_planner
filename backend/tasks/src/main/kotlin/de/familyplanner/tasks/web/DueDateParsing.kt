package de.familyplanner.tasks.web

import de.familyplanner.core.error.AppException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Reads `dueAt` of a due-date body. The key must be present: a body without it must not silently clear the due date,
 * although the JSON configuration tolerates missing nullable fields. Explicit `null` clears it.
 */
internal fun dueAtOf(body: JsonObject): Instant? =
    when (val value = body["dueAt"]) {
        null -> throw AppException.Validation("Frist fehlt")
        is JsonNull -> null
        is JsonPrimitive -> parseDueAt(value.content)
        else -> throw AppException.Validation("Ungültige Frist")
    }

/** Parses an RFC-3339 date-time with offset; `null` means no due date. */
internal fun parseDueAt(value: String?): Instant? {
    if (value == null) return null
    try {
        return OffsetDateTime.parse(value).toInstant()
    } catch (e: DateTimeParseException) {
        throw AppException.Validation("Ungültige Frist").also { it.initCause(e) }
    }
}
