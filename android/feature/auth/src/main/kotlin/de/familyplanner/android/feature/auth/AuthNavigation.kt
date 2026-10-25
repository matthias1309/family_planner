package de.familyplanner.android.feature.auth

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import de.familyplanner.android.feature.auth.ui.family.FamilyRoute
import de.familyplanner.android.feature.auth.ui.home.HomeRoute
import de.familyplanner.android.feature.auth.ui.pair.PairRoute
import de.familyplanner.android.feature.auth.ui.setup.SetupRoute
import de.familyplanner.android.feature.auth.ui.welcome.WelcomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object WelcomeRoute

@Serializable
data object SetupRoute

@Serializable
data object PairRoute

@Serializable
data object HomeRoute

@Serializable
data object FamilyRoute

/** Screens ohne Session. Der Wechsel nach dem Koppeln erfolgt über den SessionStore, nicht über die Navigation. */
fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    signedOutNotice: Boolean,
) {
    composable<WelcomeRoute> {
        WelcomeScreen(
            signedOutNotice = signedOutNotice,
            onPair = { navController.navigate(PairRoute) },
            onSetup = { navController.navigate(SetupRoute) },
        )
    }
    composable<PairRoute> { PairRoute(onBack = { navController.popBackStack() }) }
    composable<SetupRoute> {
        SetupRoute(
            onBack = { navController.popBackStack() },
            onPair = { navController.navigate(PairRoute) },
        )
    }
}

/**
 * [todayContent] und [familyContent] nehmen die Inhalte anderer Feature-Module für „Heute“ und „Familie“ auf
 * (feature:auth kennt sie nicht).
 */
fun NavGraphBuilder.mainGraph(
    todayContent: @Composable () -> Unit = {},
    familyContent: @Composable () -> Unit = {},
) {
    composable<HomeRoute> { HomeRoute(todayContent = todayContent) }
    composable<FamilyRoute> { FamilyRoute(familyContent = familyContent) }
}
