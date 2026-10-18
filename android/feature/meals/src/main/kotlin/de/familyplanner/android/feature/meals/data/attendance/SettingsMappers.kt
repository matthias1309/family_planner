package de.familyplanner.android.feature.meals.data.attendance

import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.data.parseTime
import de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettings
import de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettingsOverview
import java.time.DayOfWeek
import de.familyplanner.api.model.DeadlineSettings as ApiDeadlineSettings
import de.familyplanner.api.model.DeadlineSettingsOverview as ApiDeadlineSettingsOverview
import de.familyplanner.api.model.Weekday as ApiWeekday

internal fun ApiWeekday.toDomain(): DayOfWeek = DayOfWeek.valueOf(name)

internal fun DayOfWeek.toApi(): ApiWeekday = ApiWeekday.valueOf(name)

internal fun ApiDeadlineSettings.toDomain(): DeadlineSettings? {
    val order = parseTime(orderDeadlineTime) ?: return null
    val child = parseTime(childDeadlineTime) ?: return null
    val from = parseDate(effectiveFrom) ?: return null
    return DeadlineSettings(orderDeadlineDay.toDomain(), order, child, from)
}

internal fun ApiDeadlineSettingsOverview.toDomain(): DeadlineSettingsOverview? {
    val now = current.toDomain() ?: return null
    val next = scheduled?.let { it.toDomain() ?: return null }
    return DeadlineSettingsOverview(now, next)
}
