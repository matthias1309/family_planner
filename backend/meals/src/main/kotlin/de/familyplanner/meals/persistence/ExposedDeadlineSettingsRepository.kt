package de.familyplanner.meals.persistence

import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.meals.attendance.DeadlineSettings
import de.familyplanner.meals.attendance.DeadlineSettingsRepository
import de.familyplanner.meals.attendance.PlanWeek
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ExposedDeadlineSettingsRepository(
    private val db: Database,
) : DeadlineSettingsRepository {
    override suspend fun findEffective(week: PlanWeek): DeadlineSettings? =
        dbQuery(db) {
            MealDeadlineSettingsTable
                .selectAll()
                .where { MealDeadlineSettingsTable.effectiveFrom lessEq week.monday.toString() }
                .orderBy(MealDeadlineSettingsTable.effectiveFrom, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toSettings()
        }

    override suspend fun findScheduledAfter(week: PlanWeek): DeadlineSettings? =
        dbQuery(db) {
            MealDeadlineSettingsTable
                .selectAll()
                .where { MealDeadlineSettingsTable.effectiveFrom greater week.monday.toString() }
                .orderBy(MealDeadlineSettingsTable.effectiveFrom, SortOrder.ASC)
                .limit(1)
                .singleOrNull()
                ?.toSettings()
        }

    override suspend fun save(
        settings: DeadlineSettings,
        at: Instant,
    ) {
        dbQuery(db) {
            MealDeadlineSettingsTable.upsert {
                it[effectiveFrom] = settings.effectiveFrom.monday.toString()
                it[orderDeadlineDay] = settings.orderDeadlineDay.value
                it[orderDeadlineTime] = settings.orderDeadlineTime.toString()
                it[childDeadlineTime] = settings.childDeadlineTime.toString()
                it[updatedAt] = at.toEpochMilli()
            }
        }
    }
}

internal fun ResultRow.toSettings() =
    DeadlineSettings(
        orderDeadlineDay = DayOfWeek.of(this[MealDeadlineSettingsTable.orderDeadlineDay]),
        orderDeadlineTime = LocalTime.parse(this[MealDeadlineSettingsTable.orderDeadlineTime]),
        childDeadlineTime = LocalTime.parse(this[MealDeadlineSettingsTable.childDeadlineTime]),
        effectiveFrom = PlanWeek(LocalDate.parse(this[MealDeadlineSettingsTable.effectiveFrom])),
    )
