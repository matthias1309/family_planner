package de.familyplanner.android.feature.gamification.ui.avatar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.ui.avatar.AvatarImage
import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarItem
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarRubric
import de.familyplanner.android.feature.gamification.domain.avatar.isWearable
import de.familyplanner.android.feature.gamification.domain.avatar.itemIn
import de.familyplanner.android.feature.gamification.domain.avatar.wearing

private val TileSize = 64.dp
private val MiniAvatarSize = 56.dp
private val TileSpacing = 8.dp
private val SelectedBorder = 3.dp
private val NeutralColor = Color.Gray

/** Teile einer Rubrik als Raster; ein optionaler Slot beginnt mit „Nichts“. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarItemGrid(
    rubric: AvatarRubric,
    draft: AvatarLook,
    owned: Set<String>,
    onPick: (AvatarItem?) -> Unit,
) {
    val worn = draft.itemIn(rubric.slot)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TileSpacing), verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        if (rubric.optional) {
            val none = stringResource(R.string.game_avatar_none)
            Tile(none, selected = worn == null, onClick = { onPick(null) }) { Text(none, style = MaterialTheme.typography.labelSmall) }
        }
        rubric.items.forEach { item ->
            val state =
                if (item.isWearable(
                        owned,
                    )
                ) {
                    stringResource(R.string.game_avatar_owned)
                } else {
                    stringResource(R.string.game_points_price, item.price)
                }
            Tile(stringResource(R.string.game_avatar_tile, item.title, state), selected = item.id == worn, onClick = { onPick(item) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (rubric.slot.isColor) {
                        Box(Modifier.size(MiniAvatarSize / 2).clip(CircleShape).background(colorOf(item.id)))
                    } else {
                        AvatarImage(draft.wearing(rubric.slot, item.id), item.title, size = MiniAvatarSize)
                    }
                    Badge(item, owned)
                }
            }
        }
    }
}

@Composable
private fun Badge(
    item: AvatarItem,
    owned: Set<String>,
) {
    when {
        item.isFree -> Unit
        item.id in owned -> Text("✓", style = MaterialTheme.typography.labelSmall)
        else -> Text(stringResource(R.string.game_points_price, item.price), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Tile(
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val border = if (selected) BorderStroke(SelectedBorder, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, NeutralColor)
    Box(
        Modifier
            .size(TileSize)
            .border(border, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Farbe aus einer Katalog-ID wie `skin.EDB98A`; unlesbar → graue Fläche statt Absturz. */
private fun colorOf(itemId: String): Color =
    runCatching { Color(android.graphics.Color.parseColor("#" + itemId.substringAfter('.'))) }.getOrDefault(NeutralColor)
