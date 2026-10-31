package de.familyplanner.android.feature.gamification.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.Reward
import de.familyplanner.android.feature.gamification.domain.SavingsGoal
import de.familyplanner.android.feature.gamification.ui.rewardLabel

private val CardPadding = 16.dp

/** Karte „Sparziel“ auf „Ich“ und „Heute“; [onClick] `null` = nicht antippbar (offline). */
@Composable
fun SavingsGoalCard(
    goal: SavingsGoal?,
    onClick: (() -> Unit)?,
) {
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(CardPadding)) {
            Text(stringResource(R.string.game_goal_title), style = MaterialTheme.typography.titleMedium)
            val reward = goal?.reward
            if (goal == null || reward == null) {
                Text(stringResource(R.string.game_goal_none))
            } else {
                GoalDetails(goal, reward)
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun GoalDetails(
    goal: SavingsGoal,
    reward: Reward,
) {
    Text(rewardLabel(reward.emoji, reward.title), style = MaterialTheme.typography.titleLarge)
    goal.progress?.let { progress ->
        val target = progress.target.coerceAtLeast(1)
        LinearProgressIndicator(progress = { (progress.current.toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.game_goal_progress, progress.current, progress.target))
    }
    when {
        reward.archived -> Text(stringResource(R.string.game_goal_archived), color = MaterialTheme.colorScheme.error)
        goal.redeemable -> Text(stringResource(R.string.game_goal_redeemable), color = MaterialTheme.colorScheme.primary)
    }
}
