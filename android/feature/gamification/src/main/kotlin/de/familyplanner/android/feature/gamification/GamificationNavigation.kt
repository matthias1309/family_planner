package de.familyplanner.android.feature.gamification

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountArgs
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountRoute
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesArgs
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesRoute
import de.familyplanner.android.feature.gamification.ui.me.MeRoute
import kotlinx.serialization.Serializable

/** Hauptbereich „Ich“ (Kinder, ADR-0014). */
@Serializable
data object MeHomeRoute

@Serializable
data class ChildAccountRoute(
    val memberId: String,
    val name: String,
)

@Serializable
data class MemberBadgesRoute(
    val memberId: String,
    val name: String,
)

fun NavGraphBuilder.gamificationGraph(navController: NavHostController) {
    composable<MeHomeRoute> { MeRoute(onOpenBadges = navController::openMemberBadges) }
    composable<ChildAccountRoute> { entry ->
        val route = entry.toRoute<ChildAccountRoute>()
        ChildAccountRoute(ChildAccountArgs(route.memberId, route.name), onBack = { navController.popBackStack() })
    }
    composable<MemberBadgesRoute> { entry ->
        val route = entry.toRoute<MemberBadgesRoute>()
        MemberBadgesRoute(MemberBadgesArgs(route.memberId, route.name), onBack = { navController.popBackStack() })
    }
}

fun NavHostController.openMemberBadges(
    memberId: String,
    name: String,
) = navigate(MemberBadgesRoute(memberId, name))

fun NavHostController.openChildAccount(
    memberId: String,
    name: String,
) = navigate(ChildAccountRoute(memberId, name))
