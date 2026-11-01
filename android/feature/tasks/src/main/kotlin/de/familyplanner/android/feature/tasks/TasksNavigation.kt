package de.familyplanner.android.feature.tasks

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.familyplanner.android.feature.tasks.ui.assign.AssignArgs
import de.familyplanner.android.feature.tasks.ui.assign.AssignRoute
import de.familyplanner.android.feature.tasks.ui.board.BoardRoute
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditArgs
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditRoute
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogRoute
import de.familyplanner.android.feature.tasks.ui.history.HistoryArgs
import de.familyplanner.android.feature.tasks.ui.history.HistoryRoute
import de.familyplanner.android.feature.tasks.ui.pool.PoolRoute
import kotlinx.serialization.Serializable

/** Hauptbereich „Aufgaben“ (ADR-0014): das Board. */
@Serializable
data object TasksHomeRoute

/** [memberId]: vorausgewähltes Kind, `null` = keines. */
@Serializable
data class AssignRoute(
    val memberId: String? = null,
)

@Serializable
data object PoolRoute

@Serializable
data object CatalogRoute

/** [catalogTaskId] `null` = neue Aufgabe. */
@Serializable
data class CatalogEditRoute(
    val catalogTaskId: String? = null,
)

@Serializable
data class HistoryRoute(
    val memberId: String,
    val name: String,
)

fun NavGraphBuilder.tasksGraph(
    navController: NavHostController,
    laneAvatar: @Composable (memberId: String, name: String) -> Unit = { _, _ -> },
) {
    composable<TasksHomeRoute> {
        BoardRoute(
            onOpenCatalog = { navController.navigate(CatalogRoute) },
            onAssign = { navController.navigate(AssignRoute(it)) },
            onOpenHistory = { memberId, name -> navController.navigate(HistoryRoute(memberId, name)) },
            onOpenPool = { navController.navigate(PoolRoute) },
            laneAvatar = laneAvatar,
        )
    }
    composable<AssignRoute> { entry ->
        AssignRoute(
            args = AssignArgs(entry.toRoute<AssignRoute>().memberId),
            onOpenCatalog = { navController.navigate(CatalogRoute) },
            onDone = { navController.popBackStack() },
        )
    }
    composable<PoolRoute> {
        PoolRoute(onDone = { navController.popBackStack() })
    }
    composable<CatalogRoute> {
        CatalogRoute(
            onOpen = { navController.navigate(CatalogEditRoute(it)) },
            onCreate = { navController.navigate(CatalogEditRoute()) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<CatalogEditRoute> { entry ->
        CatalogEditRoute(CatalogEditArgs(entry.toRoute<CatalogEditRoute>().catalogTaskId), onDone = { navController.popBackStack() })
    }
    composable<HistoryRoute> { entry ->
        val route = entry.toRoute<HistoryRoute>()
        HistoryRoute(HistoryArgs(route.memberId, route.name), onBack = { navController.popBackStack() })
    }
}
