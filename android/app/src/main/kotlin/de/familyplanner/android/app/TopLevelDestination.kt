package de.familyplanner.android.app

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import de.familyplanner.android.R
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.feature.auth.FamilyRoute
import de.familyplanner.android.feature.auth.HomeRoute
import de.familyplanner.android.feature.meals.MealsHomeRoute

/** Hauptbereiche der unteren Leiste (ADR-0014); Reihenfolge = Anzeige. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    TODAY(HomeRoute, R.string.nav_today, Icons.Filled.Home),
    MEALS(MealsHomeRoute, R.string.nav_meals, Icons.Filled.DateRange),
    TASKS(TasksRoute, R.string.nav_tasks, Icons.Filled.CheckCircle),
    ME(MeRoute, R.string.nav_me, Icons.Filled.Face),
    FAMILY(FamilyRoute, R.string.nav_family, Icons.Filled.AccountCircle),
}

/** Eltern verwalten die Familie, Kinder haben „Ich“ (REQ-TASK-001). */
fun topLevelDestinations(role: Role): List<TopLevelDestination> =
    listOf(
        TopLevelDestination.TODAY,
        TopLevelDestination.MEALS,
        TopLevelDestination.TASKS,
        if (role == Role.PARENT) TopLevelDestination.FAMILY else TopLevelDestination.ME,
    )

/** Wechselt den Hauptbereich; jeder Bereich behält seinen Zustand, Zurück führt zu „Heute“. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) =
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

/** Öffnet den Essensplan über den Bereich „Essen“, damit „Zurück“ dorthin führt. */
fun NavHostController.openMealsAndThen(open: NavHostController.() -> Unit) {
    navigateToTopLevel(TopLevelDestination.MEALS)
    open()
}

/** Hauptbereich, zu dem das Ziel gehört; `null` auf Unterseiten (dort zeigt die App keine Leiste). */
fun NavDestination?.topLevelOf(destinations: List<TopLevelDestination>): TopLevelDestination? =
    destinations.firstOrNull { d -> this?.hierarchy?.any { it.hasRoute(d.route::class) } == true }

/** Sichtbarkeit der Leiste: nur auf den Hauptseiten. */
fun NavDestination?.showsNavigationBar(destinations: List<TopLevelDestination>): Boolean = topLevelOf(destinations) != null
