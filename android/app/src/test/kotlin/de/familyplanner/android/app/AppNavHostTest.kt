package de.familyplanner.android.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.DefaultSessionEvents
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionEvent
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.auth.domain.AuthRepository
import de.familyplanner.android.feature.auth.domain.Me
import de.familyplanner.android.feature.auth.domain.RefreshMeUseCase
import de.familyplanner.android.feature.auth.ui.home.HomeViewModel
import de.familyplanner.android.feature.meals.PlanOpenRequests
import de.familyplanner.android.feature.meals.SharedRecipeInbox
import de.familyplanner.android.feature.meals.domain.cooking.CancelCookingUseCase
import de.familyplanner.android.feature.meals.domain.cooking.CookingRepository
import de.familyplanner.android.feature.meals.domain.cooking.CookingUseCases
import de.familyplanner.android.feature.meals.domain.cooking.LoadWeekStatusUseCase
import de.familyplanner.android.feature.meals.domain.cooking.WeekStatus
import de.familyplanner.android.feature.meals.domain.cooking.WithdrawCancellationUseCase
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishDraft
import de.familyplanner.android.feature.meals.domain.plan.LoadWeekPlanUseCase
import de.familyplanner.android.feature.meals.domain.plan.MealPlanRepository
import de.familyplanner.android.feature.meals.domain.plan.PlanCache
import de.familyplanner.android.feature.meals.domain.plan.PlanMember
import de.familyplanner.android.feature.meals.domain.plan.WeekPlan
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import de.familyplanner.android.feature.meals.domain.recipe.DeleteRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.ImportRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipesUseCase
import de.familyplanner.android.feature.meals.domain.recipe.Recipe
import de.familyplanner.android.feature.meals.domain.recipe.RecipeDraft
import de.familyplanner.android.feature.meals.domain.recipe.RecipeRepository
import de.familyplanner.android.feature.meals.domain.recipe.RecipeSource
import de.familyplanner.android.feature.meals.domain.recipe.RecipeSummary
import de.familyplanner.android.feature.meals.domain.recipe.RecipeUseCases
import de.familyplanner.android.feature.meals.domain.recipe.SaveRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.SourceResult
import de.familyplanner.android.feature.meals.domain.reminder.LoadPendingCookingUseCase
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSnapshot
import de.familyplanner.android.feature.meals.domain.reminder.ReminderStore
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSync
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanViewModel
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = Session("tok", "m", "Lea", Role.CHILD, "d")
    private val sessions = InMemorySessionStore()
    private val events = DefaultSessionEvents()

    private val authRepository =
        object : AuthRepository {
            override suspend fun setup(
                setupToken: String,
                parentName: String,
                deviceName: String,
            ) = ApiResult.Success(session)

            override suspend fun pair(
                code: String,
                deviceName: String,
            ) = ApiResult.Success(session)

            override suspend fun me() = ApiResult.Success(Me("m", "Lea", Role.CHILD, "d"))
        }

    private val inbox = SharedRecipeInbox()
    private val planRequests = PlanOpenRequests()

    private val recipeRepository =
        object : RecipeRepository {
            override suspend fun list() = ApiResult.Success(emptyList<RecipeSummary>())

            override suspend fun recipe(recipeId: String): ApiResult<Recipe> = ApiResult.Failure(AppError.NotFound)

            override suspend fun create(draft: RecipeDraft): ApiResult<Recipe> = ApiResult.Failure(AppError.NotFound)

            override suspend fun update(
                recipeId: String,
                draft: RecipeDraft,
            ): ApiResult<Recipe> = ApiResult.Failure(AppError.NotFound)

            override suspend fun delete(recipeId: String) = ApiResult.Success(Unit)
        }

    private val recipeSource =
        object : RecipeSource {
            override suspend fun read(url: String) = SourceResult.NotFound
        }

    private val planRepository =
        object : MealPlanRepository {
            override suspend fun week(weekStart: LocalDate): ApiResult<WeekPlan> = ApiResult.Failure(AppError.Network)

            override suspend fun members(): ApiResult<List<PlanMember>> = ApiResult.Failure(AppError.Network)

            override suspend fun createDish(draft: DishDraft): ApiResult<Dish> = ApiResult.Failure(AppError.Network)

            override suspend fun updateDish(
                dishId: String,
                draft: DishDraft,
            ): ApiResult<Dish> = ApiResult.Failure(AppError.Network)

            override suspend fun deleteDish(dishId: String): ApiResult<Unit> = ApiResult.Failure(AppError.Network)
        }

    private val cookingRepository =
        object : CookingRepository {
            override suspend fun status(weekStart: LocalDate): ApiResult<WeekStatus> = ApiResult.Failure(AppError.Network)

            override suspend fun approve(dishId: String): ApiResult<Unit> = ApiResult.Failure(AppError.Network)

            override suspend fun reject(
                dishId: String,
                reason: String,
            ): ApiResult<Unit> = ApiResult.Failure(AppError.Network)

            override suspend fun cancel(
                weekStart: LocalDate,
                memberId: String,
                reason: String,
            ): ApiResult<Unit> = ApiResult.Failure(AppError.Network)

            override suspend fun withdraw(
                weekStart: LocalDate,
                memberId: String,
            ): ApiResult<Unit> = ApiResult.Failure(AppError.Network)
        }

    private val planCache =
        object : PlanCache {
            override suspend fun read(
                memberId: String,
                weekStart: LocalDate,
            ): WeekPlanSnapshot? = null

            override suspend fun write(
                memberId: String,
                week: WeekPlanSnapshot,
                today: LocalDate,
            ) = Unit
        }

    private val reminderStore =
        object : ReminderStore {
            override suspend fun read(): ReminderSnapshot? = null

            override suspend fun write(snapshot: ReminderSnapshot) = Unit

            override suspend fun clear() = Unit
        }

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    factory { HomeViewModel(sessions, RefreshMeUseCase(authRepository, sessions)) }
                    single { inbox }
                    single { planRequests }
                    viewModel {
                        WeekPlanViewModel(
                            sessions,
                            LoadWeekPlanUseCase(planRepository, planCache, sessions),
                            CookingUseCases(
                                LoadWeekStatusUseCase(cookingRepository, planRepository),
                                CancelCookingUseCase(cookingRepository, NoReminderSync),
                                WithdrawCancellationUseCase(cookingRepository, NoReminderSync),
                            ),
                            LoadPendingCookingUseCase(sessions, reminderStore),
                        )
                    }
                    viewModel { params ->
                        RecipeEditViewModel(
                            params.get(),
                            sessions,
                            RecipeUseCases(
                                LoadRecipesUseCase(recipeRepository),
                                LoadRecipeUseCase(recipeRepository),
                                SaveRecipeUseCase(recipeRepository),
                                DeleteRecipeUseCase(recipeRepository),
                            ),
                            ImportRecipeUseCase(recipeSource),
                        )
                    }
                },
            )
        }
    }

    @After
    fun tearDown() = stopKoin()

    private fun show() = compose.setContent { FamilyPlannerTheme { AppNavHost(sessions, events) } }

    @Test
    fun `without session the welcome screen is shown`() {
        show()

        compose.onNodeWithText("Mit Code verbinden").assertIsDisplayed()
    }

    @Test
    fun `with session the home screen greets the member`() {
        sessions.setCurrent(session)
        show()

        compose.onNodeWithText("Hallo Lea 👋").assertIsDisplayed()
    }

    @Test
    fun `with session the home screen offers attendance`() {
        sessions.setCurrent(session)
        show()

        compose.onNodeWithText("Anwesenheit & Fristen").assertIsDisplayed()
    }

    @Test
    fun `with session the home screen offers the meal plan`() {
        sessions.setCurrent(session)
        show()

        compose.onNodeWithText("Essensplan").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-061 shared link opens the recipe editor and empties the inbox`() {
        sessions.setCurrent(Session("tok", "p", "Anna", Role.PARENT, "d"))
        inbox.offer("https://www.rewe.de/rezepte/x/")
        show()
        compose.waitForIdle()

        compose.onNodeWithText("Neues Rezept").assertIsDisplayed()
        assertNull(inbox.pending.value)
    }

    @Test
    fun `REQ-MEAL-050 notification tap opens the meal plan and empties the request`() {
        sessions.setCurrent(session)
        planRequests.request()
        show()
        compose.waitForIdle()

        compose.onNodeWithText("Zurück").assertIsDisplayed()
        assertFalse(planRequests.pending.value)
    }

    @Test
    fun `session switch moves between welcome and home`() {
        show()
        compose.onNodeWithText("Mit Code verbinden").assertIsDisplayed()

        sessions.setCurrent(session)
        compose.waitForIdle()
        compose.onNodeWithText("Hallo Lea 👋").assertIsDisplayed()

        sessions.setCurrent(null)
        compose.waitForIdle()
        compose.onNodeWithText("Mit Code verbinden").assertIsDisplayed()
    }

    @Test
    fun `sign out event shows hint on the welcome screen`() {
        show()

        events.emit(SessionEvent.SignedOut)
        compose.waitForIdle()

        compose.onNodeWithText("Dieses Gerät wurde abgemeldet. Bitte neu koppeln.").assertIsDisplayed()
    }
}

private object NoReminderSync : ReminderSync {
    override fun requestNow() = Unit

    override fun schedulePeriodic() = Unit
}
