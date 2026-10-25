package de.familyplanner.android.feature.gamification.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.familyplanner.android.feature.gamification.R

@Composable
fun StatTiles(
    collected: Int,
    balance: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Tile(stringResource(R.string.game_collected), collected, Modifier.weight(1f))
        Tile(stringResource(R.string.game_balance), balance, Modifier.weight(1f))
    }
}

@Composable
private fun Tile(
    label: String,
    value: Int,
    modifier: Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value.toString(), style = MaterialTheme.typography.displaySmall)
        }
    }
}
