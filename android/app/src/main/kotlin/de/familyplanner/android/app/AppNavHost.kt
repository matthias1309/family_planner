package de.familyplanner.android.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionEvent
import de.familyplanner.android.core.data.session.SessionEvents
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.auth.HomeRoute
import de.familyplanner.android.feature.auth.WelcomeRoute
import de.familyplanner.android.feature.auth.authGraph
import de.familyplanner.android.feature.auth.mainGraph
import de.familyplanner.android.feature.gamification.RewardOpenRequests
import de.familyplanner.android.feature.gamification.gamificationGraph
import de.familyplanner.android.feature.gamification.openChildAccount
import de.familyplanner.android.feature.gamification.openManageRewards
import de.familyplanner.android.feature.gamification.openRewards
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsRoute
import de.familyplanner.android.feature.gamification.ui.today.TodaySavingsRoute
import de.familyplanner.android.feature.meals.PlanOpenRequests
import de.familyplanner.android.feature.meals.SharedRecipeInbox
import de.familyplanner.android.feature.meals.mealsGraph
import de.familyplanner.android.feature.meals.openRecipeImport
import de.familyplanner.android.feature.meals.openWeekPlan
import de.familyplanner.android.feature.meals.ui.today.TodayMealRoute
import de.familyplanner.android.feature.tasks.TaskOpenRequests
import de.familyplanner.android.feature.tasks.tasksGraph
import de.familyplanner.android.feature.tasks.ui.ReviewBadge
import de.familyplanner.android.feature.tasks.ui.today.TodayTasksRoute
import org.koin.compose.koinInject

/** Die Session bestimmt den Graphen: abgemeldet → Auth-Screens, angemeldet → Hauptscreens. */
@Composable
fun AppNavHost(
    sessions: SessionStore = koinInject(),
    events: SessionEvents = koinInject(),
) {
    val state by sessions.state.collectAsStateWithLifecycle(initialValue = SessionState.Loading)
    var signedOutNotice by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(events) {
        events.events.collect { if (it == SessionEvent.SignedOut) signedOutNotice = true }
    }
    LaunchedEffect(state) {
        if (state is SessionState.SignedIn) signedOutNotice = false
    }

    when (val signedIn = state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize())
        SessionState.SignedOut -> AuthHost(signedOutNotice)
        is SessionState.SignedIn -> MainHost(role = signedIn.session.role)
    }
}

@Composable
private fun AuthHost(signedOutNotice: Boolean) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = WelcomeRoute) {
        authGraph(navController, signedOutNotice)
    }
}

@Composable
private fun MainHost(
    role: Role,
    inbox: SharedRecipeInbox = koinInject(),
    planRequests: PlanOpenRequests = koinInject(),
    taskRequests: TaskOpenRequests = koinInject(),
    rewardRequests: RewardOpenRequests = koinInject(),
    reviewBadge: ReviewBadge = koinInject(),
) {
    val navController = rememberNavController()
    val shared by inbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let { text ->
            inbox.consume()
            navController.openMealsAndThen { openRecipeImport(text) }
        }
    }
    val openPlan by planRequests.pending.collectAsStateWithLifecycle()
    LaunchedEffect(openPlan) {
        if (openPlan) {
            planRequests.consume()
            navController.openMealsAndThen { openWeekPlan() }
        }
    }
    val openTasks by taskRequests.pending.collectAsStateWithLifecycle()
    LaunchedEffect(openTasks) {
        if (openTasks) {
            taskRequests.consume()
            navController.navigateToTopLevel(TopLevelDestination.TASKS)
        }
    }
    val openRewards by rewardRequests.pending.collectAsStateWithLifecycle()
    LaunchedEffect(openRewards) {
        if (openRewards) {
            rewardRequests.consume()
            if (role == Role.PARENT) {
                navController.navigateToTopLevel(TopLevelDestination.FAMILY)
                navController.openManageRewards()
            } else {
                navController.navigateToTopLevel(TopLevelDestination.ME)
                navController.openRewards()
            }
        }
    }
    val reviews by reviewBadge.count.collectAsStateWithLifecycle(initialValue = 0)
    val destinations = remember(role) { topLevelDestinations(role) }
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination.topLevelOf(destinations)
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            current?.let {
                AppNavigationBar(
                    destinations,
                    it,
                    badges = mapOf(TopLevelDestination.TASKS to reviews),
                ) { destination -> navController.navigateToTopLevel(destination) }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            mainGraph(
                todayContent = {
                    TodayMealRoute(onOpenPlan = { navController.openMealsAndThen { openWeekPlan() } })
                    TodayTasksRoute(onOpenTasks = { navController.navigateToTopLevel(TopLevelDestination.TASKS) })
                    TodaySavingsRoute(
                        onOpenRewards = {
                            navController.navigateToTopLevel(TopLevelDestination.ME)
                            navController.openRewards()
                        },
                    )
                },
                familyContent = {
                    FamilyPointsRoute(
                        onOpenChild = navController::openChildAccount,
                        onOpenRewards = navController::openManageRewards,
                    )
                },
            )
            mealsGraph(navController)
            tasksGraph(navController)
            gamificationGraph(navController)
        }
    }
}
