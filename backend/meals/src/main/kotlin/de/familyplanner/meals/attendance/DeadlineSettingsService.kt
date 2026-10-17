package de.familyplanner.meals.attendance

import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime

data class DeadlineSettingsOverview(
    val current: DeadlineSettings,
    val scheduled: DeadlineSettings?,
)

class DeadlineSettingsService(
    private val repository: DeadlineSettingsRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(DeadlineSettingsService::class.java)

    suspend fun effectiveFor(week: PlanWeek): DeadlineSettings = repository.findEffective(week) ?: DeadlineSettings.defaults(week)

    suspend fun overview(): DeadlineSettingsOverview {
        val currentWeek = PlanWeek.current(clock)
        return DeadlineSettingsOverview(effectiveFor(currentWeek), repository.findScheduledAfter(currentWeek))
    }

    /** New values apply from the plan week after the one being planned now; changing again before then overwrites. */
    suspend fun update(
        orderDeadlineDay: DayOfWeek,
        orderDeadlineTime: LocalTime,
        childDeadlineTime: LocalTime,
    ): DeadlineSettingsOverview {
        val effectiveFrom = PlanWeek.current(clock).next()
        repository.save(DeadlineSettings(orderDeadlineDay, orderDeadlineTime, childDeadlineTime, effectiveFrom), clock.instant())
        log.info("Deadline settings scheduled from {}", effectiveFrom.monday)
        return overview()
    }
}
