package de.familyplanner.android.feature.meals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.familyplanner.android.feature.meals.ui.dish.DishEditArgs
import de.familyplanner.android.feature.meals.ui.dish.DishEditRoute
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanRoute
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditRoute
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesRoute
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditArgs
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditRoute
import de.familyplanner.android.feature.meals.ui.recipe.RecipeListArgs
import de.familyplanner.android.feature.meals.ui.recipe.RecipeViewArgs
import de.familyplanner.android.feature.meals.ui.recipe.RecipeViewRoute
import de.familyplanner.android.feature.meals.ui.recipes.RecipeListRoute
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsRoute
import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListArgs
import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListRoute
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewRoute
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data object WeekPlanRoute

/** [date] als ISO-Datum (`yyyy-MM-dd`); [dishId] `null` = neues Gericht; [cookId] Vorauswahl (Stellvertretung/Kind). */
@Serializable
data class DishEditRoute(
    val date: String,
    val dishId: String? = null,
    val cookId: String? = null,
)

/** [pick]: Rezept für den Gericht-Editor wählen (Ergebnis über [PICKED_RECIPE_KEY]). */
@Serializable
data class RecipeListRoute(
    val pick: Boolean = false,
)

/** [dishDate] ISO-Datum; mit [dishId]: Ansicht zu einem Gericht (REQ-MEAL-063). */
@Serializable
data class RecipeViewRoute(
    val recipeId: String,
    val dishDate: String? = null,
    val dishId: String? = null,
)

/** [recipeId] `null` = neues Rezept; [importText] geteilter Text/Link für den Import (ADR-0012). */
@Serializable
data class RecipeEditRoute(
    val recipeId: String? = null,
    val importText: String? = null,
)

/** Öffnet den Editor „Neues Rezept“ mit Import (Teilen-Menü). */
fun NavHostController.openRecipeImport(text: String) = navigate(RecipeEditRoute(importText = text))

internal const val PICKED_RECIPE_KEY = "pickedRecipeId"

/** [weekStart] ISO-Datum (Montag); `null` = Planwoche. */
@Serializable
data class ShoppingListRoute(
    val weekStart: String? = null,
)

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
fun MealsHomeActions(
    onOpenPlan: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenShopping: () -> Unit,
) {
    Button(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.home_plan))
    }
    OutlinedButton(onClick = onOpenWeek, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.home_attendance))
    }
    OutlinedButton(onClick = onOpenRecipes, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.home_recipes))
    }
    OutlinedButton(onClick = onOpenShopping, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.home_shopping))
    }
}

fun NavGraphBuilder.mealsGraph(navController: NavHostController) {
    recipeGraph(navController)
    composable<WeekPlanRoute> {
        WeekPlanRoute(
            onOpenEditor = { args -> navController.navigate(DishEditRoute(args.date.toString(), args.dishId, args.cookId)) },
            onOpenRecipe = { args -> navController.navigate(RecipeViewRoute(args.recipeId, args.dishDate?.toString(), args.dishId)) },
            onBack = { navController.popBackStack() },
            onOpenShopping = { week -> navController.navigate(ShoppingListRoute(week.toString())) },
        )
    }
    composable<ShoppingListRoute> { entry ->
        val route = entry.toRoute<ShoppingListRoute>()
        ShoppingListRoute(ShoppingListArgs(route.weekStart?.let(LocalDate::parse)), onBack = { navController.popBackStack() })
    }
    composable<DishEditRoute> { entry ->
        val route = entry.toRoute<DishEditRoute>()
        val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_RECIPE_KEY, null).collectAsStateWithLifecycle()
        DishEditRoute(
            args = DishEditArgs(LocalDate.parse(route.date), route.dishId, route.cookId),
            pickedRecipeId = picked,
            onRecipeConsumed = { entry.savedStateHandle[PICKED_RECIPE_KEY] = null },
            onPickRecipe = { navController.navigate(RecipeListRoute(pick = true)) },
            onDone = { navController.popBackStack() },
        )
    }
    composable<WeekOverviewRoute> {
        WeekOverviewRoute(
            onManage = { navController.navigate(ProfilesRoute) },
            onSettings = { navController.navigate(DeadlineSettingsRoute) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<ProfilesRoute> {
        ProfilesRoute(
            onEdit = { navController.navigate(ProfileEditRoute(it)) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<ProfileEditRoute> { entry ->
        ProfileEditRoute(memberId = entry.toRoute<ProfileEditRoute>().memberId, onBack = { navController.popBackStack() })
    }
    composable<DeadlineSettingsRoute> { DeadlineSettingsRoute(onBack = { navController.popBackStack() }) }
}

private fun NavGraphBuilder.recipeGraph(navController: NavHostController) {
    composable<RecipeListRoute> { entry ->
        val pick = entry.toRoute<RecipeListRoute>().pick
        RecipeListRoute(
            args = RecipeListArgs(pick),
            onOpen = { id ->
                if (pick) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_RECIPE_KEY, id)
                    navController.popBackStack()
                } else {
                    navController.navigate(RecipeViewRoute(id))
                }
            },
            onCreate = { navController.navigate(RecipeEditRoute()) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<RecipeViewRoute> { entry ->
        val route = entry.toRoute<RecipeViewRoute>()
        RecipeViewRoute(
            args = RecipeViewArgs(route.recipeId, route.dishDate?.let(LocalDate::parse), route.dishId),
            onEdit = { navController.navigate(RecipeEditRoute(it)) },
            onDone = { navController.popBackStack() },
        )
    }
    composable<RecipeEditRoute> { entry ->
        val route = entry.toRoute<RecipeEditRoute>()
        RecipeEditRoute(RecipeEditArgs(route.recipeId, route.importText), onDone = { navController.popBackStack() })
    }
}
