package de.familyplanner.android.feature.meals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditRoute
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesRoute
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsRoute
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewRoute
import kotlinx.serialization.Serializable

@Serializable
data object WeekOverviewRoute

@Serializable
data object ProfilesRoute

@Serializable
data class ProfileEditRoute(
    val memberId: String,
)

@Serializable
data object DeadlineSettingsRoute

/** Einstieg von der Startseite; `app` setzt ihn in den Slot von `feature:auth`. */
@Composable
fun MealsHomeActions(onOpenWeek: () -> Unit) {
    OutlinedButton(onClick = onOpenWeek, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.home_attendance))
    }
}

fun NavGraphBuilder.mealsGraph(navController: NavHostController) {
    composable<WeekOverviewRoute> {
        WeekOverviewRoute(
            onManage = { navController.navigate(ProfilesRoute) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<ProfilesRoute> {
        ProfilesRoute(
            onEdit = { navController.navigate(ProfileEditRoute(it)) },
            onSettings = { navController.navigate(DeadlineSettingsRoute) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<ProfileEditRoute> { entry ->
        ProfileEditRoute(memberId = entry.toRoute<ProfileEditRoute>().memberId, onBack = { navController.popBackStack() })
    }
    composable<DeadlineSettingsRoute> { DeadlineSettingsRoute(onBack = { navController.popBackStack() }) }
}
