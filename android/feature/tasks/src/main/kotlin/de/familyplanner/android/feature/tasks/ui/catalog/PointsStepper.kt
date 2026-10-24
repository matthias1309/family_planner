package de.familyplanner.android.feature.tasks.ui.catalog

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
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits

/** Punkte 1–100: „–“ n „+“, an den Grenzen deaktiviert. */
@Composable
fun PointsStepper(
    value: Int,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val less = stringResource(R.string.tasks_points_less)
        val more = stringResource(R.string.tasks_points_more)
        TextButton(
            onClick = { onChange(value - 1) },
            enabled = enabled && value > TaskLimits.POINTS_MIN,
            modifier = Modifier.semantics { contentDescription = less },
        ) { Text("–") }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        TextButton(
            onClick = { onChange(value + 1) },
            enabled = enabled && value < TaskLimits.POINTS_MAX,
            modifier = Modifier.semantics { contentDescription = more },
        ) { Text("+") }
    }
}
