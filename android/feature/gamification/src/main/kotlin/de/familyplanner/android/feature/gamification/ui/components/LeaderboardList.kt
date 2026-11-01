package de.familyplanner.android.feature.gamification.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.ui.avatar.AvatarImage
import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.RankedChild

private val AvatarSize = 40.dp
private val RankWidth = 24.dp

/** Rangliste als `Column` (wird in Listen eingebettet). Mit [onClick] sind die Zeilen antippbar; [looks]: memberId → Avatar. */
@Composable
fun LeaderboardList(
    entries: List<RankedChild>,
    highlightId: String?,
    onClick: ((RankedChild) -> Unit)?,
    looks: Map<String, AvatarLook> = emptyMap(),
) {
    if (entries.isEmpty()) {
        Text(stringResource(R.string.game_leaderboard_empty))
        return
    }
    Column {
        entries.forEach { child ->
            val rowModifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick(child) } else it }
            Row(
                modifier = rowModifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val weight = if (child.memberId == highlightId) FontWeight.Bold else FontWeight.Normal
                Text("${child.rank}", modifier = Modifier.widthIn(min = RankWidth), fontWeight = weight)
                AvatarImage(looks[child.memberId], child.name, size = AvatarSize)
                Text(child.name, modifier = Modifier.weight(1f).padding(start = 12.dp), fontWeight = weight)
                Text("${child.collected}", fontWeight = weight)
                if (onClick != null) Text("  ›")
            }
        }
    }
}
