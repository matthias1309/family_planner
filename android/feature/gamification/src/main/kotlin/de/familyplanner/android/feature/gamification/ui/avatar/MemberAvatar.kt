package de.familyplanner.android.feature.gamification.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.avatar.AvatarImage
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarDirectory
import org.koin.compose.koinInject

/** Avatar eines Mitglieds für Slots anderer Module (z. B. Board-Reiter); ohne Avatar die Initiale. */
@Composable
fun MemberAvatar(
    memberId: String,
    name: String,
    size: Dp,
) {
    val directory = koinInject<AvatarDirectory>()
    val looks by directory.looks.collectAsStateWithLifecycle()
    LaunchedEffect(directory) { directory.ensureLoaded() }
    AvatarImage(looks[memberId], name, size = size)
}
