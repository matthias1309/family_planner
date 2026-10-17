package de.familyplanner.android.feature.meals

import de.familyplanner.android.feature.meals.data.attendance.RemoteAttendanceRepository
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
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditViewModel
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesViewModel
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsViewModel
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewViewModel
import de.familyplanner.api.client.MealsApi
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

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
    }
