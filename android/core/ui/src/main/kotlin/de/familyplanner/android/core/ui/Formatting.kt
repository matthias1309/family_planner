package de.familyplanner.android.core.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val BerlinZone: ZoneId = ZoneId.of("Europe/Berlin")
private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
private val DateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY)
private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY)

fun formatTime(instant: Instant): String = TimeFormat.format(instant.atZone(BerlinZone))

fun formatDateTime(instant: Instant): String = DateTimeFormat.format(instant.atZone(BerlinZone))

fun formatDate(date: LocalDate): String = DateFormat.format(date)

fun formatDateRange(
    from: LocalDate,
    to: LocalDate,
): String = "${formatDate(from)}–${formatDate(to)}"
