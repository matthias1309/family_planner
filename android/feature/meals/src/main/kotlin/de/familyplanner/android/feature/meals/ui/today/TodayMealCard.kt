package de.familyplanner.android.feature.meals.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import org.koin.androidx.compose.koinViewModel

private val CardPadding = 16.dp

@Composable
fun TodayMealRoute(onOpenPlan: () -> Unit) {
    val viewModel = koinViewModel<TodayMealViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayMealCard(state = state, onOpenPlan = onOpenPlan)
}

/** Karte „Heute gibt es …“; ein Tipp öffnet den Essensplan. */
@Composable
fun TodayMealCard(
    state: TodayMealUiState,
    onOpenPlan: () -> Unit,
) {
    Card(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(CardPadding)) {
            Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleMedium)
            when {
                state.isLoading -> Unit
                state.unavailable -> Text(stringResource(R.string.today_unavailable))
                else -> {
                    val dish = state.day?.dish?.takeIf { it.status != DishStatus.REJECTED }
                    if (dish == null) {
                        Text(stringResource(R.string.today_nothing))
                    } else {
                        DishInfo(dish, state.day?.cookName)
                    }
                }
            }
        }
    }
}

@Composable
private fun DishInfo(
    dish: Dish,
    cookName: String?,
) {
    Text(dish.name, style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.today_cook, cookName ?: "?"))
    if (dish.status == DishStatus.SUBMITTED) {
        Text(stringResource(R.string.today_pending), color = MaterialTheme.colorScheme.tertiary)
    }
}
