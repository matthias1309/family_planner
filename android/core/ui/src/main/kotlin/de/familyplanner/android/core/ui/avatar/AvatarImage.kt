package de.familyplanner.android.core.ui.avatar

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import de.familyplanner.android.core.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LOG_TAG = "AvatarImage"
private val DefaultAvatarSize = 40.dp
private val PreviewSpacing = 8.dp

/** Renders [look]; without a look (child has not saved an avatar yet) the initial in a circle is shown. */
@Composable
fun AvatarImage(
    look: AvatarLook?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = DefaultAvatarSize,
) {
    val context = LocalContext.current
    val definition by produceState(AvatarDefinition.peek(), context) {
        value = value ?: withContext(Dispatchers.Default) { AvatarDefinition.load(context) }
    }
    // Der Wert bleibt über Schlüsselwechsel erhalten: bis das neue Bild fertig ist, steht das alte (kein Flackern).
    val svg by produceState<SVG?>(initialValue = null, look, definition) {
        val loaded = definition
        value =
            if (look == null || loaded == null) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    runCatching { SVG.getFromString(AvatarSvg.render(loaded, look.toSelection())) }
                        .onFailure { Log.w(LOG_TAG, "Avatar could not be rendered", it) }
                        .getOrNull()
                }
            }
    }
    val description = stringResource(R.string.core_avatar_description, name)
    val rendered = svg
    if (rendered == null) {
        InitialAvatar(name, description, modifier, size)
        return
    }
    Canvas(modifier.size(size).clip(CircleShape).semantics { contentDescription = description }) {
        drawIntoCanvas { canvas ->
            rendered.documentWidth = this.size.width
            rendered.documentHeight = this.size.height
            rendered.renderToCanvas(canvas.nativeCanvas)
        }
    }
}

/** Quellenangabe für „Avataaars“ (ADR-0017 Nr. 6). */
@Composable
fun AvatarCredit(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.core_avatar_credit),
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InitialAvatar(
    name: String,
    description: String,
    modifier: Modifier,
    size: Dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Preview(showBackground = true)
@Composable
private fun AvatarImagePreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(PreviewSpacing)) {
        AvatarImage(look = SampleLooks.free, name = "Mia", size = 80.dp)
        AvatarImage(look = SampleLooks.hat, name = "Ben", size = 80.dp)
        AvatarImage(look = SampleLooks.glassesBeard, name = "Opa", size = 80.dp)
        AvatarImage(look = SampleLooks.graphicShirt, name = "Lea", size = 80.dp)
        AvatarImage(look = null, name = "Tom", size = 80.dp)
    }
}
