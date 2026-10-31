package de.familyplanner.android.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.DefaultSessionEvents
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionEvent
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.auth.domain.AuthRepository
import de.familyplanner.android.feature.auth.domain.CreateMemberUseCase
import de.familyplanner.android.feature.auth.domain.CreatePairingCodeUseCase
import de.familyplanner.android.feature.auth.domain.LoadFamilyUseCase
import de.familyplanner.android.feature.auth.domain.Me
import de.familyplanner.android.feature.auth.domain.RefreshMeUseCase
import de.familyplanner.android.feature.auth.domain.RevokeDeviceUseCase
import de.familyplanner.android.feature.auth.ui.family.FamilyViewModel
import de.familyplanner.android.feature.auth.ui.home.HomeViewModel
import de.familyplanner.android.feature.gamification.domain.BookPointsUseCase
import de.familyplanner.android.feature.gamification.domain.CookingPointsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadBadgesUseCase
import de.familyplanner.android.feature.gamification.domain.LoadChildAccountUseCase
import de.familyplanner.android.feature.gamification.domain.LoadPointsUseCase
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountViewModel
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsViewModel
import de.familyplanner.android.feature.gamification.ui.me.MeViewModel
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
import de.familyplanner.android.feature.meals.ui.today.TodayMealViewModel
import de.familyplanner.android.feature.tasks.TaskOpenRequests
import de.familyplanner.android.feature.tasks.domain.board.LoadBoardUseCase
import de.familyplanner.android.feature.tasks.domain.board.MoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.RemoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskLane
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.ui.ReviewBadge
import de.familyplanner.android.feature.tasks.ui.board.BoardViewModel
import de.familyplanner.android.feature.tasks.ui.today.TodayTasksViewModel
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

            // Folgt der Session, damit das Auffrischen beim Start die Rolle nicht überschreibt.
            override suspend fun me() =
                ApiResult.Success(
                    sessions.current.let { Me(it?.memberId ?: "m", it?.memberName ?: "Lea", it?.role ?: Role.CHILD, "d") },
                )
        }

    private val inbox = SharedRecipeInbox()
    private val planRequests = PlanOpenRequests()
    private val taskRequests = TaskOpenRequests()
    private val boardCache = MemoryBoardCache()
    private var boardLanes: List<TaskLane> = emptyList()
    private val pointsRepository = StaticPointsRepository()
    private val pointsCache = MemoryPointsCache()
    private val familyRepository = EmptyFamilyRepository()
    private val taskRepository by lazy { StaticTaskRepository(boardLanes) }

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

            override suspend fun confirmCooked(dishId: String): ApiResult<Unit> = ApiResult.Failure(AppError.Network)

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

    private val pointsModule =
        module {
            viewModel { MeViewModel(LoadPointsUseCase(pointsRepository, pointsCache, sessions), sessions) }
            viewModel {
                FamilyPointsViewModel(
                    LoadPointsUseCase(pointsRepository, pointsCache, sessions),
                    CookingPointsUseCase(pointsRepository),
                )
            }
            viewModel { params ->
                ChildAccountViewModel(
                    params.get(),
                    LoadChildAccountUseCase(pointsRepository),
                    LoadBadgesUseCase(pointsRepository),
                    BookPointsUseCase(pointsRepository),
                )
            }
            viewModel {
                FamilyViewModel(
                    sessions,
                    LoadFamilyUseCase(familyRepository),
                    CreateMemberUseCase(familyRepository),
                    CreatePairingCodeUseCase(familyRepository),
                    RevokeDeviceUseCase(familyRepository, sessions),
                )
            }
        }

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    factory { HomeViewModel(sessions, RefreshMeUseCase(authRepository, sessions)) }
                    single { inbox }
                    single { planRequests }
                    single { taskRequests }
                    single { ReviewBadge(boardCache, sessions) }
                    viewModel {
                        BoardViewModel(
                            sessions,
                            LoadBoardUseCase(taskRepository, boardCache, sessions),
                            MoveCardUseCase(taskRepository, boardCache, sessions),
                            RemoveCardUseCase(taskRepository, boardCache, sessions),
                        )
                    }
                    viewModel { TodayTasksViewModel(sessions, LoadBoardUseCase(taskRepository, boardCache, sessions)) }
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
                    viewModel { TodayMealViewModel(LoadWeekPlanUseCase(planRepository, planCache, sessions)) }
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
                pointsModule,
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

    private fun openMeals() = compose.onNode(hasText("Essen") and hasClickAction()).performClick()

    @Test
    fun `REQ-TASK-001 child sees four main areas`() {
        sessions.setCurrent(session)
        show()

        listOf("Heute", "Essen", "Aufgaben", "Ich").forEach { compose.onNode(hasText(it) and hasClickAction()).assertIsDisplayed() }
        compose.onNodeWithText("Familie").assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-001 parent sees family as main area`() {
        sessions.setCurrent(Session("tok", "p", "Anna", Role.PARENT, "d"))
        show()

        compose.onNode(hasText("Familie") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Ich").assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-001 meals area offers plan, shopping, recipes and attendance`() {
        sessions.setCurrent(session)
        show()

        openMeals()

        listOf("Essensplan", "Einkaufsliste", "Rezepte", "Anwesenheit & Fristen").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `REQ-TASK-001 sub page hides the bar and back returns to meals`() {
        sessions.setCurrent(session)
        show()
        openMeals()

        compose.onNodeWithText("Essensplan").performClick()
        compose.onNodeWithText("Zurück").assertIsDisplayed()
        compose.onNode(hasText("Aufgaben") and hasClickAction()).assertDoesNotExist()

        compose.onNodeWithText("Zurück").performClick()
        compose.onNodeWithText("Einkaufsliste").assertIsDisplayed()
        compose.onNode(hasText("Aufgaben") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-001 tasks area shows the board`() {
        boardLanes = listOf(TaskLane("m", "Lea", listOf(taskCard("t-1", "m", "Bad putzen"))))
        sessions.setCurrent(session)
        show()

        compose.onNode(hasText("Aufgaben") and hasClickAction()).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Bad putzen").assertIsDisplayed()
        compose.onNodeWithText("Erledigt").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-012 me shows collected points and balance`() {
        sessions.setCurrent(session)
        show()

        compose.onNode(hasText("Ich") and hasClickAction()).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Gesammelt").assertIsDisplayed()
        compose.onNodeWithText("Guthaben").assertIsDisplayed()
        compose.onNodeWithText("Platz 2 von 2").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-014 parent sees the leaderboard under family`() {
        sessions.setCurrent(Session("tok", "p", "Anna", Role.PARENT, "d"))
        show()

        compose.onNode(hasText("Familie") and hasClickAction()).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Punkte").assertIsDisplayed()
        compose.onNodeWithText("Ben").assertIsDisplayed()
        compose.onNodeWithText("Kochpunkte: 3").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-013 tapping a child opens its points account`() {
        sessions.setCurrent(Session("tok", "p", "Anna", Role.PARENT, "d"))
        show()
        compose.onNode(hasText("Familie") and hasClickAction()).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Lea").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Punktekonto Lea").assertIsDisplayed()
        compose.onNodeWithText("Punkte buchen").assertIsDisplayed()
        compose.onNode(hasText("Aufgaben") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-003 parent sees badge with review count`() {
        boardLanes =
            listOf(
                TaskLane(
                    "c",
                    "Ben",
                    listOf(taskCard("t-1", "c", "Bad", TaskStatus.REVIEW), taskCard("t-2", "c", "Müll", TaskStatus.REVIEW)),
                ),
            )
        sessions.setCurrent(Session("tok", "p", "Anna", Role.PARENT, "d"))
        kotlinx.coroutines.runBlocking {
            boardCache.write(
                "p",
                de.familyplanner.android.feature.tasks.domain.board
                    .BoardSnapshot(boardLanes, java.time.Instant.EPOCH),
            )
        }
        show()
        compose.waitForIdle()

        compose.onNode(hasContentDescription("2 zur Abnahme"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-003 children see no badge`() {
        boardLanes = listOf(TaskLane("m", "Lea", listOf(taskCard("t-1", "m", "Bad", TaskStatus.REVIEW))))
        sessions.setCurrent(session)
        kotlinx.coroutines.runBlocking {
            boardCache.write(
                "m",
                de.familyplanner.android.feature.tasks.domain.board
                    .BoardSnapshot(boardLanes, java.time.Instant.EPOCH),
            )
        }
        show()
        compose.waitForIdle()

        compose.onNode(hasContentDescription("1 zur Abnahme"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-002 today shows the tasks card and a tap opens the board`() {
        boardLanes = listOf(TaskLane("m", "Lea", listOf(taskCard("t-1", "m", "Bad putzen"), taskCard("t-2", "m", "Müll"))))
        sessions.setCurrent(session)
        show()
        compose.waitForIdle()

        compose.onNodeWithText("Deine Aufgaben").assertIsDisplayed()
        compose.onNodeWithText("2 offen").assertIsDisplayed().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Todo").assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-030 notification tap opens tasks and empties the request`() {
        boardLanes = listOf(TaskLane("m", "Lea", listOf(taskCard("t-1", "m", "Bad putzen"))))
        sessions.setCurrent(session)
        taskRequests.request()
        show()
        compose.waitForIdle()

        compose.onNodeWithText("Todo").assertIsDisplayed()
        assertFalse(taskRequests.pending.value)
    }

    @Test
    fun `REQ-TASK-002 today shows the meal card and a tap opens the meal plan`() {
        sessions.setCurrent(session)
        show()

        compose.onNodeWithText("Heute gibt es").performClick()

        compose.onNodeWithText("Zurück").assertIsDisplayed()
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

        compose.onNodeWithText("Zurück").performClick()
        compose.onNodeWithText("Einkaufsliste").assertIsDisplayed()
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
