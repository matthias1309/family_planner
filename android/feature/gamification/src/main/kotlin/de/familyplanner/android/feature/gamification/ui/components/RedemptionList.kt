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
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.ui.labelRes
import de.familyplanner.android.feature.gamification.ui.rewardLabel

/** Einlösungen als `Column` (wird in Listen eingebettet). */
@Composable
fun RedemptionList(redemptions: List<Redemption>) {
    if (redemptions.isEmpty()) {
        Text(stringResource(R.string.game_redemptions_empty))
        return
    }
    Column {
        redemptions.forEach { redemption ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(redemption.requestedAt.atZone(BerlinZone).toLocalDate()), modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(rewardLabel(redemption.emoji, redemption.title))
                    Text(
                        stringResource(R.string.game_points_price, redemption.price) + " · " +
                            stringResource(redemption.status.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (redemption.status == RedemptionStatus.REJECTED) {
                        redemption.rejectionReason?.let {
                            Text(stringResource(R.string.game_redemption_reason, it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
