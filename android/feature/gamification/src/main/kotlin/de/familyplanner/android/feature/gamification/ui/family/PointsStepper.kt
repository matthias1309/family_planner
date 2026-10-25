package de.familyplanner.android.feature.gamification.ui.family

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.familyplanner.android.feature.gamification.R

/** Zahl zwischen [min] und [max]: „–“ n „+“, an den Grenzen deaktiviert. */
@Composable
fun PointsStepper(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val less = stringResource(R.string.game_points_less)
        val more = stringResource(R.string.game_points_more)
        TextButton(
            onClick = { onChange(value - 1) },
            enabled = enabled && value > min,
            modifier = Modifier.semantics { contentDescription = less },
        ) { Text("–") }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        TextButton(
            onClick = { onChange(value + 1) },
            enabled = enabled && value < max,
            modifier = Modifier.semantics { contentDescription = more },
        ) { Text("+") }
    }
}
