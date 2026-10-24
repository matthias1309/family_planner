package de.familyplanner.android.feature.tasks.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.ui.formatDateTime
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.board.CardAction
import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.ui.isSecondary
import de.familyplanner.android.feature.tasks.ui.labelRes

private val CardPadding = 16.dp

/** Eine Karte mit Titel, Punkten, Status-Zeile und den Aktionen der Rolle (REQ-TASK-022–024). */
@Composable
fun TaskCardItem(
    card: TaskCard,
    childName: String?,
    actions: List<CardAction>,
    enabled: Boolean,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(card.emoji, card.title).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.tasks_card_points, card.points), style = MaterialTheme.typography.labelLarge)
            }
            CardMeta(card, childName)
            CardActions(actions, enabled, onAction)
        }
    }
}

@Composable
private fun CardMeta(
    card: TaskCard,
    childName: String?,
) {
    val reported = card.reportedAt?.let(::formatDateTime).orEmpty()
    when {
        card.status == TaskStatus.REVIEW && childName != null ->
            Text(
                stringResource(R.string.tasks_card_child_reported, childName, reported),
            )
        card.status == TaskStatus.REVIEW -> Text(stringResource(R.string.tasks_card_reported, reported))
        card.status == TaskStatus.DONE -> Text(stringResource(R.string.tasks_card_done, card.doneAt?.let(::formatDateTime).orEmpty()))
        // Ohne Begründung ist eine zurückgewiesene Karte von einer frisch zugewiesenen nicht zu unterscheiden.
        card.rejectionReason != null ->
            Text(stringResource(R.string.tasks_card_rejected, card.rejectionReason), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CardActions(
    actions: List<CardAction>,
    enabled: Boolean,
    onAction: (CardAction) -> Unit,
) {
    if (actions.isEmpty()) return
    val primary = actions.filterNot { it.isSecondary }
    val secondary = actions.filter { it.isSecondary }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (secondary.isNotEmpty()) OverflowMenu(secondary, enabled, onAction)
        primary.forEach { action ->
            // Die letzte Hauptaktion ist die Hauptsache (Abnehmen, Erledigt); davor stehen die zurückhaltenden.
            if (action == primary.last()) {
                Button(onClick = { onAction(action) }, enabled = enabled) { Text(stringResource(action.labelRes())) }
            } else {
                OutlinedButton(onClick = { onAction(action) }, enabled = enabled) { Text(stringResource(action.labelRes())) }
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    actions: List<CardAction>,
    enabled: Boolean,
    onAction: (CardAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, enabled = enabled) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.tasks_card_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(action.labelRes())) },
                onClick = {
                    expanded = false
                    onAction(action)
                },
            )
        }
    }
}
