package de.familyplanner.android.feature.meals

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import de.familyplanner.android.feature.meals.data.attendance.RemoteAttendanceRepository
import de.familyplanner.android.feature.meals.data.cooking.RemoteCookingRepository
import de.familyplanner.android.feature.meals.data.plan.DataStorePlanCache
import de.familyplanner.android.feature.meals.data.plan.PlanCacheSerializer
import de.familyplanner.android.feature.meals.data.plan.RemoteMealPlanRepository
import de.familyplanner.android.feature.meals.data.recipe.PageScriptLoader
import de.familyplanner.android.feature.meals.data.recipe.RemoteRecipeRepository
import de.familyplanner.android.feature.meals.data.recipe.WebRecipeSource
import de.familyplanner.android.feature.meals.data.recipe.WebViewPageScriptLoader
import de.familyplanner.android.feature.meals.data.shopping.RemoteShoppingRepository
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
import de.familyplanner.android.feature.meals.domain.cooking.CancelCookingUseCase
import de.familyplanner.android.feature.meals.domain.cooking.CookingRepository
import de.familyplanner.android.feature.meals.domain.cooking.CookingUseCases
import de.familyplanner.android.feature.meals.domain.cooking.LoadWeekStatusUseCase
import de.familyplanner.android.feature.meals.domain.cooking.ReviewDishUseCase
import de.familyplanner.android.feature.meals.domain.cooking.WithdrawCancellationUseCase
import de.familyplanner.android.feature.meals.domain.plan.DeleteDishUseCase
import de.familyplanner.android.feature.meals.domain.plan.DishUseCases
import de.familyplanner.android.feature.meals.domain.plan.LoadDishEditorUseCase
import de.familyplanner.android.feature.meals.domain.plan.LoadWeekPlanUseCase
import de.familyplanner.android.feature.meals.domain.plan.MealPlanRepository
import de.familyplanner.android.feature.meals.domain.plan.PlanCache
import de.familyplanner.android.feature.meals.domain.plan.SaveDishUseCase
import de.familyplanner.android.feature.meals.domain.recipe.DeleteRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.ImportRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipesUseCase
import de.familyplanner.android.feature.meals.domain.recipe.RecipeRepository
import de.familyplanner.android.feature.meals.domain.recipe.RecipeSource
import de.familyplanner.android.feature.meals.domain.recipe.RecipeUseCases
import de.familyplanner.android.feature.meals.domain.recipe.SaveRecipeUseCase
import de.familyplanner.android.feature.meals.domain.shopping.AddShoppingItemUseCase
import de.familyplanner.android.feature.meals.domain.shopping.DeleteShoppingItemUseCase
import de.familyplanner.android.feature.meals.domain.shopping.LoadShoppingListUseCase
import de.familyplanner.android.feature.meals.domain.shopping.SetShoppingCheckUseCase
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingRepository
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingUseCases
import de.familyplanner.android.feature.meals.ui.dish.DishEditViewModel
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanViewModel
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditViewModel
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesViewModel
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditViewModel
import de.familyplanner.android.feature.meals.ui.recipe.RecipeViewModel
import de.familyplanner.android.feature.meals.ui.recipes.RecipeListViewModel
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsViewModel
import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListViewModel
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
        factory {
            DishUseCases(
                LoadDishEditorUseCase(get(), get()),
                SaveDishUseCase(get()),
                DeleteDishUseCase(get()),
                ReviewDishUseCase(get()),
                LoadRecipeUseCase(get()),
            )
        }
        viewModel { WeekPlanViewModel(get(), get(), get()) }
        viewModel { params -> DishEditViewModel(params.get(), get(), get()) }

        // recipe
        single<RecipeRepository> { RemoteRecipeRepository(get(), get()) }
        factory {
            RecipeUseCases(
                LoadRecipesUseCase(get()),
                LoadRecipeUseCase(get()),
                SaveRecipeUseCase(get()),
                DeleteRecipeUseCase(get()),
            )
        }
        viewModel { params -> RecipeListViewModel(params.get(), get(), get()) }
        viewModel { params -> RecipeViewModel(params.get(), get(), get(), get()) }
        viewModel { params -> RecipeEditViewModel(params.get(), get(), get(), get()) }
        single { SharedRecipeInbox() }
        single<PageScriptLoader> { WebViewPageScriptLoader(androidContext()) }
        single<RecipeSource> { WebRecipeSource(get()) }
        factory { ImportRecipeUseCase(get()) }

        // cooking
        single<CookingRepository> { RemoteCookingRepository(get(), get()) }
        factory { CookingUseCases(LoadWeekStatusUseCase(get(), get()), CancelCookingUseCase(get()), WithdrawCancellationUseCase(get())) }

        // shopping
        single<ShoppingRepository> { RemoteShoppingRepository(get(), get()) }
        factory {
            ShoppingUseCases(
                LoadShoppingListUseCase(get()),
                AddShoppingItemUseCase(get()),
                SetShoppingCheckUseCase(get()),
                DeleteShoppingItemUseCase(get()),
            )
        }
        viewModel { params -> ShoppingListViewModel(params.get(), get(), get()) }
    }
