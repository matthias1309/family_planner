package de.familyplanner.meals

import de.familyplanner.meals.attendance.AttendanceRepository
import de.familyplanner.meals.attendance.AttendanceService
import de.familyplanner.meals.attendance.DeadlineSettingsRepository
import de.familyplanner.meals.attendance.DeadlineSettingsService
import de.familyplanner.meals.persistence.ExposedAttendanceRepository
import de.familyplanner.meals.persistence.ExposedDeadlineSettingsRepository
import de.familyplanner.meals.persistence.ExposedMealPlanRepository
import de.familyplanner.meals.plan.MealPlanRepository
import de.familyplanner.meals.plan.MealPlanService
import org.koin.dsl.module
import java.time.Clock

/** `Database` and `MemberRepository` come from `coreModule`. */
fun mealsModule(clock: Clock) =
    module {
        single<AttendanceRepository> { ExposedAttendanceRepository(get()) }
        single<DeadlineSettingsRepository> { ExposedDeadlineSettingsRepository(get()) }
        single { DeadlineSettingsService(get(), clock) }
        single { AttendanceService(get(), get(), get(), clock) }
        single<MealPlanRepository> { ExposedMealPlanRepository(get()) }
        single { MealPlanService(get(), get(), clock) }
    }
