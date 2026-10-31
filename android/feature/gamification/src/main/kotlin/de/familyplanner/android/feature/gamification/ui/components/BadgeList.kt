package de.familyplanner.android.feature.gamification.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.Badge
import de.familyplanner.android.feature.gamification.domain.BadgeOverview
import de.familyplanner.android.feature.gamification.domain.BadgeProgress

private val BadgeSize = 40.dp
private val ProgressWidth = 72.dp
private const val OPEN_ALPHA = 0.6f
private const val SECRET_EMOJI = "❓"

@Composable
fun BadgeHeader(overview: BadgeOverview) {
    Text(
        stringResource(R.string.game_badges_count, overview.earnedCount, overview.totalCount),
        style = MaterialTheme.typography.titleMedium,
    )
}

/** Badges als `Column` (wird in Listen eingebettet); noch nicht erhaltene sind abgeblendet. */
@Composable
fun BadgeList(badges: List<Badge>) {
    if (badges.isEmpty()) {
        Text(stringResource(R.string.game_badges_empty))
        return
    }
    Column {
        badges.forEach { badge ->
            val rowModifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            Row(
                modifier = if (badge.earned) rowModifier else rowModifier.alpha(OPEN_ALPHA),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BadgeIcon(badge.emoji)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(badge.title ?: stringResource(R.string.game_badge_secret))
                    (badge.description ?: badge.hint)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                BadgeStatus(badge)
            }
        }
    }
}

@Composable
private fun BadgeIcon(emoji: String?) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(BadgeSize)) {
        Box(contentAlignment = Alignment.Center) { Text(emoji ?: SECRET_EMOJI) }
    }
}

@Composable
private fun BadgeStatus(badge: Badge) {
    val awardedAt = badge.awardedAt
    val progress = badge.progress
    when {
        badge.earned && awardedAt != null ->
            Text(
                stringResource(R.string.game_badge_awarded, formatDate(awardedAt.atZone(BerlinZone).toLocalDate())),
                style = MaterialTheme.typography.labelSmall,
            )
        progress != null -> ProgressBar(progress)
    }
}

@Composable
private fun ProgressBar(progress: BadgeProgress) {
    Column(horizontalAlignment = Alignment.End) {
        LinearProgressIndicator(
            progress = { (progress.current.toFloat() / progress.target).coerceIn(0f, 1f) },
            modifier = Modifier.width(ProgressWidth),
        )
        Text("${progress.current}/${progress.target}", style = MaterialTheme.typography.labelSmall)
    }
}
