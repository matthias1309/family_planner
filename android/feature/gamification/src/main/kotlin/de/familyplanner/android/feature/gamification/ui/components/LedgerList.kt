package de.familyplanner.android.feature.gamification.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.LedgerEntry
import de.familyplanner.android.feature.gamification.ui.formatAmount
import de.familyplanner.android.feature.gamification.ui.labelRes

/** Verlauf als `Column` (wird in Listen eingebettet). */
@Composable
fun LedgerList(entries: List<LedgerEntry>) {
    if (entries.isEmpty()) {
        Text(stringResource(R.string.game_history_empty))
        return
    }
    Column {
        entries.forEach { entry ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(entry.bookedAt.atZone(BerlinZone).toLocalDate()), modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(entry.kind.labelRes()), style = MaterialTheme.typography.labelSmall)
                    entry.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                val color = if (entry.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Text(formatAmount(entry.amount), color = color)
            }
        }
    }
}
