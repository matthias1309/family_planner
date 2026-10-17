package de.familyplanner.android.feature.auth.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BerlinZone: ZoneId = ZoneId.of("Europe/Berlin")
private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
private val DateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY)

fun formatTime(instant: Instant): String = TimeFormat.format(instant.atZone(BerlinZone))

fun formatDateTime(instant: Instant): String = DateTimeFormat.format(instant.atZone(BerlinZone))
