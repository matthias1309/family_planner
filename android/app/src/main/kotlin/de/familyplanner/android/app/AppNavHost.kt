package de.familyplanner.android.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import de.familyplanner.android.core.data.session.SessionEvent
import de.familyplanner.android.core.data.session.SessionEvents
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.auth.HomeRoute
import de.familyplanner.android.feature.auth.WelcomeRoute
import de.familyplanner.android.feature.auth.authGraph
import de.familyplanner.android.feature.auth.mainGraph
import de.familyplanner.android.feature.meals.MealsHomeActions
import de.familyplanner.android.feature.meals.WeekOverviewRoute
import de.familyplanner.android.feature.meals.WeekPlanRoute
import de.familyplanner.android.feature.meals.mealsGraph
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

    when (state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize())
        SessionState.SignedOut -> AuthHost(signedOutNotice)
        is SessionState.SignedIn -> MainHost()
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
private fun MainHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoute) {
        mainGraph(navController) {
            MealsHomeActions(
                onOpenPlan = { navController.navigate(WeekPlanRoute) },
                onOpenWeek = { navController.navigate(WeekOverviewRoute) },
            )
        }
        mealsGraph(navController)
    }
}
