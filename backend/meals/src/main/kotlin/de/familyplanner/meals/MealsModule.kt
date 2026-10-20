package de.familyplanner.meals

import de.familyplanner.meals.attendance.AttendanceRepository
import de.familyplanner.meals.attendance.AttendanceService
import de.familyplanner.meals.attendance.DeadlineSettingsRepository
import de.familyplanner.meals.attendance.DeadlineSettingsService
import de.familyplanner.meals.persistence.ExposedAttendanceRepository
import de.familyplanner.meals.persistence.ExposedDeadlineSettingsRepository
import de.familyplanner.meals.persistence.ExposedMealPlanRepository
import de.familyplanner.meals.persistence.ExposedRecipeRepository
import de.familyplanner.meals.persistence.ExposedShoppingListRepository
import de.familyplanner.meals.plan.CookingWeekService
import de.familyplanner.meals.plan.DishReviewService
import de.familyplanner.meals.plan.MealPlanRepository
import de.familyplanner.meals.plan.MealPlanService
import de.familyplanner.meals.recipe.RecipeRepository
import de.familyplanner.meals.recipe.RecipeService
import de.familyplanner.meals.shopping.ShoppingListRepository
import de.familyplanner.meals.shopping.ShoppingListService
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
        single<RecipeRepository> { ExposedRecipeRepository(get()) }
        single { RecipeService(get(), clock) }
        single { MealPlanService(get(), get(), get(), get(), clock) }
        single { DishReviewService(get(), clock) }
        single { CookingWeekService(get(), get(), get(), get(), clock) }
        single<ShoppingListRepository> { ExposedShoppingListRepository(get()) }
        single { ShoppingListService(get(), get(), clock) }
    }
