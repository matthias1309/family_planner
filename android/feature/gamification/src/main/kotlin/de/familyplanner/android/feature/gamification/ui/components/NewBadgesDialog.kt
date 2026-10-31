package de.familyplanner.android.feature.gamification.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.Badge

/** Hinweis auf Badges, die seit dem letzten Öffnen neu erhalten wurden (REQ-GAME-020). */
@Composable
fun NewBadgesDialog(
    badges: List<Badge>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.game_new_badges_title, badges.size)) },
        text = { Column { badges.forEach { Text("${it.emoji.orEmpty()} ${it.title.orEmpty()}".trim()) } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_new_badge_ok)) } },
    )
}
