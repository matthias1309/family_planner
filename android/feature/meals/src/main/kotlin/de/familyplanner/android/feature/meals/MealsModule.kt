package de.familyplanner.android.feature.meals

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import de.familyplanner.android.feature.meals.data.attendance.RemoteAttendanceRepository
import de.familyplanner.android.feature.meals.data.plan.DataStorePlanCache
import de.familyplanner.android.feature.meals.data.plan.PlanCacheSerializer
import de.familyplanner.android.feature.meals.data.plan.RemoteMealPlanRepository
import de.familyplanner.android.feature.meals.domain.attendance.AddExceptionUseCase
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceRepository
import de.familyplanner.android.feature.meals.domain.attendance.DeleteExceptionUseCase
import de.familyplanner.android.feature.meals.domain.attendance.ExceptionUseCases
import de.familyplanner.android.feature.meals.domain.attendance.LoadDeadlineSettingsUseCase
import de.familyplanner.android.feature.meals.domain.attendance.LoadExceptionsUseCase
import de.familyplanner.android.feature.meals.domain.attendance.LoadProfilesUseCase
import de.familyplanner.android.feature.meals.domain.attendance.LoadWeekOverviewUseCase
import de.familyplanner.android.feature.meals.domain.attendance.SaveDeadlineSettingsUseCase
import de.familyplanner.android.feature.meals.domain.attendance.SaveProfileUseCase
import de.familyplanner.android.feature.meals.domain.plan.DeleteDishUseCase
import de.familyplanner.android.feature.meals.domain.plan.DishUseCases
import de.familyplanner.android.feature.meals.domain.plan.LoadDishEditorUseCase
import de.familyplanner.android.feature.meals.domain.plan.LoadWeekPlanUseCase
import de.familyplanner.android.feature.meals.domain.plan.MealPlanRepository
import de.familyplanner.android.feature.meals.domain.plan.PlanCache
import de.familyplanner.android.feature.meals.domain.plan.SaveDishUseCase
import de.familyplanner.android.feature.meals.ui.dish.DishEditViewModel
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanViewModel
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditViewModel
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesViewModel
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsViewModel
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewViewModel
import de.familyplanner.api.client.MealsApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

private const val PLAN_CACHE_FILE = "meal_plan_cache.json"

val mealsModule =
    module {
        // Der einzige MealsApi-Eintrag: weitere Teilbereiche (Wochenplan, …) nutzen dasselbe single.
        single { get<Retrofit>().create(MealsApi::class.java) }

        // attendance
        single<AttendanceRepository> { RemoteAttendanceRepository(get(), get(), get()) }
        factory { LoadWeekOverviewUseCase(get()) }
        factory { LoadProfilesUseCase(get()) }
        factory { SaveProfileUseCase(get()) }
        factory { ExceptionUseCases(LoadExceptionsUseCase(get()), AddExceptionUseCase(get()), DeleteExceptionUseCase(get())) }
        factory { LoadDeadlineSettingsUseCase(get()) }
        factory { SaveDeadlineSettingsUseCase(get()) }
        viewModel { WeekOverviewViewModel(get(), get()) }
        viewModel { ProfilesViewModel(get()) }
        viewModel { params -> ProfileEditViewModel(params.get(), get(), get(), get()) }
        viewModel { DeadlineSettingsViewModel(get(), get()) }

        // plan
        single<MealPlanRepository> { RemoteMealPlanRepository(get(), get(), get()) }
        // Genau eine DataStore-Instanz je Datei und Prozess, sonst IllegalStateException.
        single<PlanCache> {
            DataStorePlanCache(
                DataStoreFactory.create(
                    serializer = PlanCacheSerializer(get()),
                    produceFile = { androidContext().dataStoreFile(PLAN_CACHE_FILE) },
                ),
            )
        }
        factory { LoadWeekPlanUseCase(get(), get(), get()) }
        factory { DishUseCases(LoadDishEditorUseCase(get()), SaveDishUseCase(get()), DeleteDishUseCase(get())) }
        viewModel { WeekPlanViewModel(get(), get()) }
        viewModel { params -> DishEditViewModel(params[0], params.getOrNull(), get(), get()) }
    }
