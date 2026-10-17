package de.familyplanner.meals.persistence

import org.jetbrains.exposed.v1.core.Table
import java.time.DayOfWeek

object MealAttendanceProfilesTable : Table("meal_attendance_profiles") {
    val memberId = varchar("member_id", ID_LENGTH)
    val cooks = bool("cooks")
    val patternType = varchar("pattern_type", PATTERN_TYPE_LENGTH)
    val weekAMask = integer("week_a_mask")
    val weekBMask = integer("week_b_mask")
    val referenceWeek = varchar("reference_week", DATE_LENGTH).nullable()
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(memberId)
}

object MealAttendanceExceptionsTable : Table("meal_attendance_exceptions") {
    val id = varchar("id", ID_LENGTH)
    val memberId = varchar("member_id", ID_LENGTH)
    val fromDate = varchar("from_date", DATE_LENGTH)
    val toDate = varchar("to_date", DATE_LENGTH)
    val presence = varchar("presence", PRESENCE_LENGTH)
    val note = varchar("note", NOTE_LENGTH).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object MealDeadlineSettingsTable : Table("meal_deadline_settings") {
    val effectiveFrom = varchar("effective_from", DATE_LENGTH)
    val orderDeadlineDay = integer("order_deadline_day")
    val orderDeadlineTime = varchar("order_deadline_time", TIME_LENGTH)
    val childDeadlineTime = varchar("child_deadline_time", TIME_LENGTH)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(effectiveFrom)
}

private const val ID_LENGTH = 36
private const val DATE_LENGTH = 10
private const val TIME_LENGTH = 5
private const val PATTERN_TYPE_LENGTH = 11
private const val PRESENCE_LENGTH = 7
private const val NOTE_LENGTH = 60

/** Bit `dayOfWeek - 1`: Monday = bit 0 ... Sunday = bit 6. */
internal fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

internal fun Int.toDays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()
