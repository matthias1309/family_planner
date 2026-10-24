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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.familyplanner.android.R
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionEvent
import de.familyplanner.android.core.data.session.SessionEvents
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.auth.HomeRoute
import de.familyplanner.android.feature.auth.WelcomeRoute
import de.familyplanner.android.feature.auth.authGraph
import de.familyplanner.android.feature.auth.mainGraph
import de.familyplanner.android.feature.meals.PlanOpenRequests
import de.familyplanner.android.feature.meals.SharedRecipeInbox
import de.familyplanner.android.feature.meals.mealsGraph
import de.familyplanner.android.feature.meals.openRecipeImport
import de.familyplanner.android.feature.meals.openWeekPlan
import de.familyplanner.android.feature.meals.ui.today.TodayMealRoute
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
    val destinations = remember(role) { topLevelDestinations(role) }
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination.topLevelOf(destinations)
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            current?.let { AppNavigationBar(destinations, it) { destination -> navController.navigateToTopLevel(destination) } }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            mainGraph { TodayMealRoute(onOpenPlan = { navController.openMealsAndThen { openWeekPlan() } }) }
            mealsGraph(navController)
            composable<TasksRoute> {
                ComingSoonScreen(stringResource(R.string.nav_tasks), stringResource(R.string.tasks_coming_soon))
            }
            composable<MeRoute> {
                ComingSoonScreen(stringResource(R.string.nav_me), stringResource(R.string.me_coming_soon))
            }
        }
    }
}
