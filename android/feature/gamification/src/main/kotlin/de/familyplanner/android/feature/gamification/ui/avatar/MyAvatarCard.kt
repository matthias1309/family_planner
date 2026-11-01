package de.familyplanner.android.feature.gamification.ui.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.ui.avatar.AvatarImage
import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.R

private val CardPadding = 16.dp
private val CardAvatarSize = 64.dp

/** Karte „Dein Avatar“ auf „Ich“ und „Heute“; [enabled] `false` (offline) = nicht antippbar. */
@Composable
fun MyAvatarCard(
    look: AvatarLook?,
    name: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(CardPadding),
            horizontalArrangement = Arrangement.spacedBy(CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(look, name, size = CardAvatarSize)
            Text(
                stringResource(if (look == null) R.string.game_avatar_setup else R.string.game_avatar_open),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
