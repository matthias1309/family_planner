package de.familyplanner.android.feature.meals.domain.attendance

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.validationFailure
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun validatePattern(pattern: AttendancePattern): ApiResult.Failure? =
    if (pattern is AttendancePattern.Alternating && pattern.weekA.isEmpty() && pattern.weekB.isEmpty()) {
        validationFailure("Bitte mindestens einen Anwesenheitstag wählen.")
    } else {
        null
    }

internal fun isSupportedYear(date: LocalDate): Boolean = date.year in AttendanceLimits.MIN_YEAR..AttendanceLimits.MAX_YEAR

internal fun validateException(
    from: LocalDate,
    to: LocalDate,
    note: String?,
): ApiResult.Failure? =
    when {
        !isSupportedYear(from) || !isSupportedYear(to) ->
            validationFailure("Bitte ein Datum zwischen ${AttendanceLimits.MIN_YEAR} und ${AttendanceLimits.MAX_YEAR} wählen.")
        to.isBefore(from) -> validationFailure("Das Ende darf nicht vor dem Beginn liegen.")
        ChronoUnit.DAYS.between(from, to) + 1 > AttendanceLimits.EXCEPTION_MAX_DAYS ->
            validationFailure("Eine Ausnahme darf höchstens ${AttendanceLimits.EXCEPTION_MAX_DAYS} Tage dauern.")
        (note?.trim()?.length ?: 0) > AttendanceLimits.NOTE_MAX ->
            validationFailure("Die Notiz darf höchstens ${AttendanceLimits.NOTE_MAX} Zeichen haben.")
        else -> null
    }

/** Getrimmt; leer wird zu `null`, damit der Server keine leere Notiz speichert. */
internal fun normalizeNote(note: String?): String? = note?.trim()?.ifEmpty { null }
