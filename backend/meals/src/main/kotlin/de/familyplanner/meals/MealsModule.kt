package de.familyplanner.meals

import de.familyplanner.meals.attendance.AttendanceRepository
import de.familyplanner.meals.attendance.AttendanceService
import de.familyplanner.meals.attendance.DeadlineSettingsRepository
import de.familyplanner.meals.attendance.DeadlineSettingsService
import de.familyplanner.meals.persistence.ExposedAttendanceRepository
import de.familyplanner.meals.persistence.ExposedDeadlineSettingsRepository
import org.koin.dsl.module
import java.time.Clock

/** `Database` and `MemberRepository` come from `coreModule`. */
fun mealsModule(clock: Clock) =
    module {
        single<AttendanceRepository> { ExposedAttendanceRepository(get()) }
        single<DeadlineSettingsRepository> { ExposedDeadlineSettingsRepository(get()) }
        single { DeadlineSettingsService(get(), clock) }
        single { AttendanceService(get(), get(), get(), clock) }
    }
