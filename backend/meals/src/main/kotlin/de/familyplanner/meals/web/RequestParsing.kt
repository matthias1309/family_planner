package de.familyplanner.meals.web

import de.familyplanner.core.error.AppException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

fun parseDate(
    value: String,
    field: String,
): LocalDate =
    try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        throw AppException.Validation("Ungültiges Datum '$field'").also { it.initCause(e) }
    }

fun parseTime(
    value: String,
    field: String,
): LocalTime =
    try {
        LocalTime.parse(value)
    } catch (e: DateTimeParseException) {
        throw AppException.Validation("Ungültige Uhrzeit '$field'").also { it.initCause(e) }
    }

fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)
