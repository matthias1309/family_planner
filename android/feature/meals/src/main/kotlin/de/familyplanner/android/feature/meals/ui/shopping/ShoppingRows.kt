package de.familyplanner.android.feature.meals.ui.shopping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingEntry
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingSource
import de.familyplanner.android.feature.meals.ui.weekdayShort

/** „Curry (Do), Carbonara (Di)“ — Herkunft eines berechneten Eintrags. */
internal fun formatSources(sources: List<ShoppingSource>): String =
    sources.joinToString(", ") { "${it.dishName} (${weekdayShort(it.date.dayOfWeek)})" }

@Composable
internal fun ShoppingRow(
    entry: ShoppingEntry,
    state: ShoppingListUiState,
    onEvent: (ShoppingListEvent) -> Unit,
) {
    val text = entry.displayText()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (state.canEdit) {
            Checkbox(
                checked = entry.checked,
                onCheckedChange = { onEvent(ShoppingListEvent.SetChecked(entry.key, it)) },
                enabled = !state.isBusy,
                modifier = Modifier.semantics { contentDescription = text },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            val shown = if (entry.checked && !state.canEdit) "✓ $text" else text
            Text(
                shown,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (entry.checked) TextDecoration.LineThrough else null,
            )
            val origin = if (entry.isFreeItem) stringResource(R.string.shopping_own_item) else formatSources(entry.sources)
            Text(origin, style = MaterialTheme.typography.bodySmall)
        }
        val itemId = entry.itemId
        if (state.canEdit && itemId != null) {
            IconButton(onClick = { onEvent(ShoppingListEvent.DeleteItem(itemId)) }, enabled = !state.isBusy) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.shopping_delete, entry.name))
            }
        }
    }
}
